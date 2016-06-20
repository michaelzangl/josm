// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import java.awt.BorderLayout;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.util.GuiHelper;

/**
 * This is the content panel inside the {@link MainFrame}. It displays the content the user is working with.
 * <p>
 * If there is no active layer, there is no content displayed. As soon as there are active layers, the {@link MapFrame} is displayed.
 *
 * @author Michael Zangl
 * @since 10432
 */
public class MainPanel extends JPanel {
    private MapFrame map;
    // Needs to be lazy because we need to wait for preferences to set up.
    private GettingStarted gettingStarted;
    private final CopyOnWriteArrayList<MapFrameListener> mapFrameListeners = new CopyOnWriteArrayList<>();
    private final transient MainLayerManager layerManager;

    /**
     * Create a new main panel
     * @param layerManager The layer manager to use to display the content.
     */
    public MainPanel(MainLayerManager layerManager) {
        super(new BorderLayout());
        this.layerManager = layerManager;
    }

    /**
     * Update the content of this {@link MainFrame} to either display the map or display the welcome screen.
     */
    protected void updateContent() {
        GuiHelper.assertCallFromEdt();
        MapFrame old = map;
        boolean showMap = !layerManager.getLayers().isEmpty();
        if (old != null && showMap) {
            // no state change
            return;
        }

        // remove old content
        setVisible(false);
        removeAll();
        if (old != null) {
            old.destroy();
        }

        // create new content
        if (showMap) {
            map = createNewMapFrame();
        } else {
            map = null;
            Main.map = map;
            add(getGettingStarted(), BorderLayout.CENTER);
        }
        setVisible(true);

        if (old == null && !showMap) {
            // listeners may not be able to handle this...
            return;
        }

        // Notify map frame listeners, mostly plugins.
        for (MapFrameListener listener : mapFrameListeners) {
            MapView.fireDeprecatedListenerOnAdd = true;
            listener.mapFrameInitialized(old, map);
            MapView.fireDeprecatedListenerOnAdd = false;
        }
        if (map == null && Main.currentProgressMonitor != null) {
            Main.currentProgressMonitor.showForegroundDialog();
        }
    }

    private MapFrame createNewMapFrame() {
        MapFrame mapFrame = new MapFrame(null, null);
        // Required by many components.
        Main.map = mapFrame;

        mapFrame.fillPanel(this);

        //TODO: Move this to some better place
        List<Layer> layers = Main.getLayerManager().getLayers();
        if (!layers.isEmpty()) {
            mapFrame.selectMapMode((MapMode) mapFrame.getDefaultButtonAction(), layers.get(0));
        }
        mapFrame.initializeDialogsPane();
        mapFrame.setVisible(true);
        return mapFrame;
    }

    /**
     * Registers a new {@code MapFrameListener} that will be notified of MapFrame changes.
     * <p>
     * It will fire an initial mapFrameInitialized event
     * when the MapFrame is present. Otherwise will only fire when the MapFrame is created
     * or destroyed.
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call.
     */
    public boolean addAndFireMapFrameListener(MapFrameListener listener) {
        boolean changed = addMapFrameListener(listener);
        if (changed && map != null) {
            listener.mapFrameInitialized(null, map);
        }
        return changed;
    }

    /**
     * Registers a new {@code MapFrameListener} that will be notified of MapFrame changes
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call
     */
    public boolean addMapFrameListener(MapFrameListener listener) {
        return listener != null && mapFrameListeners.add(listener);
    }

    /**
     * Unregisters the given {@code MapFrameListener} from MapFrame changes
     * @param listener The MapFrameListener
     * @return {@code true} if the listeners collection changed as a result of the call
     */
    public boolean removeMapFrameListener(MapFrameListener listener) {
        return listener != null && mapFrameListeners.remove(listener);
    }

    /**
     * Gets the {@link GettingStarted} panel.
     * @return The panel.
     */
    public GettingStarted getGettingStarted() {
        if (gettingStarted == null) {
            gettingStarted = new GettingStarted();
        }
        return gettingStarted;
    }

    /**
     * Re-adds the layer listeners. Never call this in production, only needed for testing.
     */
    public void reAddListeners() {
        layerManager.addLayerChangeListener(new LayerChangeListener() {
            @Override
            public void layerAdded(LayerAddEvent e) {
                updateContent();
            }

            @Override
            public void layerRemoving(final LayerRemoveEvent e) {
                // Delay main.map removal until after all listeners are finished.
                // Some components rely on this and e.g. get the MapView that way.
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        updateContent();
                    }
                });
            }

            @Override
            public void layerOrderChanged(LayerOrderChangeEvent e) {
                // ignored
            }
        });
    }
}
