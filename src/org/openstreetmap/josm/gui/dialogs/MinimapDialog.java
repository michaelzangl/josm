// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryLayerInfo;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeEvent;
import org.openstreetmap.josm.data.preferences.AbstractProperty.ValueChangeListener;
import org.openstreetmap.josm.data.preferences.CachingProperty;
import org.openstreetmap.josm.data.preferences.DoubleProperty;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapViewState.MapViewRectangle;
import org.openstreetmap.josm.gui.NavigatableComponent;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.tools.bugreport.BugReport;

/**
 * A small map of the current edit location implemented as {@link ToggleDialog}.
 */
public class MinimapDialog extends ToggleDialog implements PropertyChangeListener, ActiveLayerChangeListener {

    private static final StringProperty IMAGERY_LAYER = new StringProperty("minimap.layer", "");
    private static final CachingProperty<Double> SCALE_FACTOR = new DoubleProperty("minimap.zoom.factor", 3).cached();
    private static final List<String> EVENTS = Arrays.asList(NavigatableComponent.PROPNAME_CENTER, NavigatableComponent.PROPNAME_SCALE);

    private final ValueChangeListener<String> layerValueListener = this::layerValueChanged;
    private final ValueChangeListener<Double> scaleValueListener = this::scaleValueChanged;

    private boolean skipEvents;
    private MapView map;
    private Layer layer;

    /**
     * Constructs a new {@code MinimapDialog}.
     */
    public MinimapDialog() {
        super(tr("Mini map"), "minimap", tr("Displays a small map of the current edit location"), null, 150);
    }

    private void initialize() {
        if (map != null) {
            return;
        }
        map = new MapView(new MainLayerManager(), this, null);
        createLayout(map, false, Collections.emptyList());
        map.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showLayerSelectionDialog(e);
                    e.consume();
                }
            }

            private void showLayerSelectionDialog(MouseEvent e) {
                JPopupMenu menu = new JPopupMenu();
                addPopupDialogActions(menu);
                menu.show(map, e.getX(), e.getY());
            }
        });
    }

    @Override
    public void showDialog() {
        initialize();
        loadLayer();
        Main.map.mapView.addPropertyChangeListener(this);
        IMAGERY_LAYER.addListener(layerValueListener);
        SCALE_FACTOR.addListener(scaleValueListener);
        super.showDialog();
    }

    private void loadLayer() {
        List<ImageryInfo> savedLayers = getPossibleLayers();
        if (savedLayers.isEmpty()) {
            return;
        }
        String toLoad = IMAGERY_LAYER.get();
        try {
            ImageryInfo info = savedLayers.stream().filter(i -> i.getId().equals(toLoad)).findAny().orElse(savedLayers.get(0));
            layer = ImageryLayer.create(info);
            map.getLayerManager().addLayer(layer);
        } catch (RuntimeException e) {
            BugReport.intercept(e).put("toLoad", toLoad).warn();
        }
    }

    private static ArrayList<ImageryInfo> getPossibleLayers() {
        return new ArrayList<>(ImageryLayerInfo.instance.getLayers());
    }

    @Override
    public void hideDialog() {
        Main.map.mapView.removePropertyChangeListener(this);
        removeLayer();
        IMAGERY_LAYER.removeListener(layerValueListener);
        SCALE_FACTOR.removeListener(scaleValueListener);
        super.hideDialog();
    }

    @Override
    protected void addPopupDialogActions(JPopupMenu dialog) {
        super.addPopupDialogActions(dialog);
        dialog.addSeparator();
        for (ImageryInfo l : getPossibleLayers()) {
            JMenuItem item = new JMenuItem(l.getMenuName());
            item.addActionListener(e -> IMAGERY_LAYER.put(l.getId()));
            dialog.add(item);
        }
    }

    private void removeLayer() {
        if (layer != null) {
            map.getLayerManager().removeLayer(layer);
            layer = null;
        }
    }

    private void layerValueChanged(ValueChangeEvent<? extends String> e) {
        removeLayer();
        loadLayer();
    }

    private void scaleValueChanged(ValueChangeEvent<? extends Double> e) {
        updateZoomFromMainMap();
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
            handlePropertyChange(evt);
    }


    private void handlePropertyChange(PropertyChangeEvent evt) {
        if (evt.getSource() == Main.map.mapView && (EVENTS.contains(evt.getPropertyName()))) {
        updateZoomFromMainMap();
        }
    }

    private void updateZoomFromMainMap() {
        if (!skipEvents) {
            skipEvents = true;
            MapView mv = Main.map.mapView;
            MapViewRectangle bounds = mv.getState().getViewArea();
            map.zoomTo(bounds.getProjectionBounds());
            map.zoomToFactor(SCALE_FACTOR.get()); // to give a better overview
            skipEvents = false;
        }
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        Layer layer = map.getLayerManager().getActiveLayer();
        Layer newLayer = e.getSource().getActiveLayer();
        if (!Objects.equals(layer, newLayer)) {
            if (layer != null) {
                map.getLayerManager().removeLayer(layer);
            }
            if (newLayer != null) {
                map.getLayerManager().addLayer(newLayer);
            }
        }
    }
}
