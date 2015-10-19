// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.geom.Area;
import java.awt.geom.GeneralPath;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractButton;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.MapMode;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.Preferences.PreferenceChangedListener;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.ViewportData;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.visitor.paint.PaintColors;
import org.openstreetmap.josm.data.osm.visitor.paint.Rendering;
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.LayerManagerWithActive;
import org.openstreetmap.josm.gui.layer.LayerManagerWithActive.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManagerWithActive.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.MapViewPaintable;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.gui.layer.geoimage.GeoImageLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.MarkerLayer;
import org.openstreetmap.josm.gui.layer.markerlayer.PlayHeadMarker;
import org.openstreetmap.josm.gui.mapview.NavigationModel.ZoomChangeEvent;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.AudioPlayer;
import org.openstreetmap.josm.tools.BugReportExceptionHandler;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * This is a component used in the {@link MapFrame} for browsing the map. It use is to
 * provide the MapMode's enough capabilities to operate.<br><br>
 *
 * {@code MapView} holds meta-data about the data set currently displayed, as scale level,
 * center point viewed, what scrolling mode or editing mode is selected or with
 * what projection the map is viewed etc..<br><br>
 *
 * {@code MapView} is able to administrate several layers.
 *
 * @author imi
 */
public class MapView extends NavigatableComponent
implements PropertyChangeListener, PreferenceChangedListener, OsmDataLayer.LayerStateChangeListener,
LayerManager.LayerChangeListener, LayerManagerWithActive.ActiveLayerChangeListener {
    /**
     * Interface to notify listeners of a layer change.
     * @author imi
     */
    @Deprecated
    public interface LayerChangeListener {

        /**
         * Notifies this listener that the active layer has changed.
         * @param oldLayer The previous active layer
         * @param newLayer The new activer layer
         */
        void activeLayerChange(Layer oldLayer, Layer newLayer);

        /**
         * Notifies this listener that a layer has been added.
         * @param newLayer The new added layer
         */
        void layerAdded(Layer newLayer);

        /**
         * Notifies this listener that a layer has been removed.
         * @param oldLayer The old removed layer
         */
        void layerRemoved(Layer oldLayer);
    }

    /**
     * An interface that needs to be implemented in order to listen for changes to the active edit layer.
     */
    @Deprecated
    public interface EditLayerChangeListener {

        /**
         * Called after the active edit layer was changed.
         * @param oldLayer The old edit layer
         * @param newLayer The current (new) edit layer
         */
        void editLayerChanged(OsmDataLayer oldLayer, OsmDataLayer newLayer);
    }

    protected static class LayerChangeAdapter implements ActiveLayerChangeListener, LayerManager.LayerChangeListener {

        private final LayerChangeListener wrapped;
        private boolean receiveOneInitialFire;

        public LayerChangeAdapter(LayerChangeListener wrapped) {
            this.wrapped = wrapped;
        }

        public LayerChangeAdapter(LayerChangeListener wrapped, boolean initialFire) {
            this(wrapped);
            this.receiveOneInitialFire = initialFire;
        }

        @Override
        public void layerAdded(LayerAddEvent e) {
            wrapped.layerAdded(e.getAddedLayer());
        }

        @Override
        public void layerRemoving(LayerRemoveEvent e) {
            wrapped.layerRemoved(e.getRemovedLayer());
        }

        @Override
        public void layerOrderChanged(LayerOrderChangeEvent e) {
            // not in old API
        }

        @Override
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
            Layer oldActive = receiveOneInitialFire ? null : e.getPreviousActiveLayer();
            Layer newActive = e.getSource().getActiveLayer();
            if (oldActive != newActive) {
                wrapped.activeLayerChange(oldActive, newActive);
            }
            receiveOneInitialFire = false;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((wrapped == null) ? 0 : wrapped.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            LayerChangeAdapter other = (LayerChangeAdapter) obj;
            if (wrapped == null) {
                if (other.wrapped != null)
                    return false;
            } else if (!wrapped.equals(other.wrapped))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "LayerChangeAdapter [wrapped=" + wrapped + "]";
        }

    }

    protected static class EditLayerChangeAdapter implements ActiveLayerChangeListener {

        private final EditLayerChangeListener wrapped;

        public EditLayerChangeAdapter(EditLayerChangeListener wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
            OsmDataLayer oldLayer = e.getPreviousEditLayer();
            OsmDataLayer newLayer = e.getSource().getEditLayer();
            if (oldLayer != newLayer) {
                wrapped.editLayerChanged(oldLayer, newLayer);
            }
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((wrapped == null) ? 0 : wrapped.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EditLayerChangeAdapter other = (EditLayerChangeAdapter) obj;
            if (wrapped == null) {
                if (other.wrapped != null)
                    return false;
            } else if (!wrapped.equals(other.wrapped))
                return false;
            return true;
        }

        @Override
        public String toString() {
            return "EditLayerChangeAdapter [wrapped=" + wrapped + "]";
        }

    }

    /**
     * Removes a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    @Deprecated
    public static void removeLayerChangeListener(LayerChangeListener listener) {
        LayerChangeAdapter adapter = new LayerChangeAdapter(listener);
        Main.layerManager.removeLayerChangeListener(adapter);
        Main.layerManager.removeActiveLayerChangeListener(adapter);
    }

    @Deprecated
    public static void removeEditLayerChangeListener(EditLayerChangeListener listener) {
        Main.layerManager.removeActiveLayerChangeListener(new EditLayerChangeAdapter(listener));
    }

    /**
     * Adds a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    @Deprecated
    public static void addLayerChangeListener(LayerChangeListener listener) {
        addLayerChangeListener(listener, false);
    }

    /**
     * Adds a layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @param initialFire fire an active-layer-changed-event right after adding
     * the listener in case there is a layer present (should be)
     */
    @Deprecated
    public static void addLayerChangeListener(LayerChangeListener listener, boolean initialFire) {
        if (listener != null) {
            initialFire = initialFire && Main.isDisplayingMapView();

            LayerChangeAdapter adapter = new LayerChangeAdapter(listener, initialFire);
            Main.layerManager.addLayerChangeListener(adapter, false);
            Main.layerManager.addActiveLayerChangeListener(adapter, initialFire);
            adapter.receiveOneInitialFire = false;
        }
    }

    /**
     * Adds an edit layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     * @param initialFire fire an edit-layer-changed-event right after adding
     * the listener in case there is an edit layer present
     */
    @Deprecated
    public static void addEditLayerChangeListener(EditLayerChangeListener listener, boolean initialFire) {
        if (listener != null) {
            Main.layerManager.addActiveLayerChangeListener(new EditLayerChangeAdapter(listener), initialFire && Main.isDisplayingMapView() && Main.map.mapView.getEditLayer() != null);
        }
    }

    /**
     * Adds an edit layer change listener
     *
     * @param listener the listener. Ignored if null or already registered.
     */
    @Deprecated
    public static void addEditLayerChangeListener(EditLayerChangeListener listener) {
        addEditLayerChangeListener(listener, false);
    }

    public boolean viewportFollowing = false;

    private LayerManagerWithActive layerManager = new LayerManagerWithActive();

    /**
     * The play head marker: there is only one of these so it isn't in any specific layer
     */
    public transient PlayHeadMarker playHeadMarker = null;

    /**
     * The last event performed by mouse.
     */
    public MouseEvent lastMEvent = new MouseEvent(this, 0, 0, 0, 0, 0, 0, false); // In case somebody reads it before first mouse move

    /**
     * Temporary layers (selection rectangle, etc.) that are never cached and
     * drawn on top of regular layers.
     * Access must be synchronized.
     */
    private final transient Set<MapViewPaintable> temporaryLayers = new LinkedHashSet<>();

    private transient BufferedImage nonChangedLayersBuffer;
    private transient BufferedImage offscreenBuffer;
    // Layers that wasn't changed since last paint
    private final transient List<Layer> nonChangedLayers = new ArrayList<>();
    private transient Layer changedLayer;
    private boolean paintPreferencesChanged = true;
    private Rectangle lastClipBounds = new Rectangle();
    private transient MapMover mapMover;

    /**
     * Constructs a new {@code MapView}.
     * @param layerManager The layers to display.
     * @param contentPane The content pane used to register shortcuts in its
     * {@link InputMap} and {@link ActionMap}
     * @param viewportData the initial viewport of the map. Can be null, then
     * the viewport is derived from the layer data.
     */
    public MapView(LayerManagerWithActive layerManager, final JPanel contentPane, final ViewportData viewportData) {
        this.layerManager = layerManager;
        initialViewport = viewportData;
        layerManager.addLayerChangeListener(this);
        layerManager.addActiveLayerChangeListener(this, false);
        Main.pref.addPreferenceChangeListener(this);

        addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent e) {
                removeComponentListener(this);

                for (JComponent c : getMapNavigationComponents(MapView.this)) {
                    MapView.this.add(c);
                }

                mapMover = new MapMover(getNavigationModel(), cursorManager, contentPane);
                mapMover.registerMouseEvents(MapView.this);
                // Notify the map view that it has changed.
                repaint();
            }
        });

        // listend to selection changes to redraw the map
        DataSet.addSelectionListener(repaintSelectionChangedListener);

        //store the last mouse action
        this.addMouseMotionListener(new MouseMotionListener() {
            @Override
            public void mouseDragged(MouseEvent e) {
                mouseMoved(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                lastMEvent = e;
            }
        });
        this.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent me) {
                // focus the MapView component when mouse is pressed inside it
                requestFocus();
            }
        });

        if (Shortcut.findShortcut(KeyEvent.VK_TAB, 0)!=null) {
            setFocusTraversalKeysEnabled(false);
        }
    }

    /**
     * Adds the map navigation components to a
     * @param forMapView The map view to get the components for.
     * @return A list containing the correctly positioned map navigation components.
     */
    public static List<? extends JComponent> getMapNavigationComponents(MapView forMapView) {
        MapSlider zoomSlider = new MapSlider(forMapView);
        zoomSlider.setBounds(3, 0, 114, 30);
        zoomSlider.setFocusTraversalKeysEnabled(Shortcut.findShortcut(KeyEvent.VK_TAB, 0) == null);

        MapScaler scaler = new MapScaler(forMapView);
        scaler.setLocation(10, 30);

        return Arrays.asList(zoomSlider, scaler);
    }

    // remebered geometry of the component
    private Dimension oldSize = null;
    private Point oldLoc = null;

    /**
     * Call this method to keep map position on screen during next repaint
     */
    public void rememberLastPositionOnScreen() {
        oldSize = getSize();
        oldLoc  = getLocationOnScreen();
    }

    /**
     * Add a layer to the current MapView. The layer will be added at topmost
     * position.
     * @param layer The layer to add
     */
    @Deprecated
    public void addLayer(Layer layer) {
        layerManager.addLayer(layer);
    }

    @Override
    public void layerAdded(LayerAddEvent e) {
        Layer layer = e.getAddedLayer();
        if (layer instanceof MarkerLayer && playHeadMarker == null) {
            playHeadMarker = PlayHeadMarker.create();
        }

        boolean isOsmDataLayer = layer instanceof OsmDataLayer;
        if (isOsmDataLayer) {
            ((OsmDataLayer) layer).addLayerStateChangeListener(this);
        }

        layer.addPropertyChangeListener(this);
        Main.addProjectionChangeListener(layer);
        AudioPlayer.reset();

        repaint();
    }

    @Override
    @Deprecated
    protected DataSet getCurrentDataSet() {
        return layerManager.getEditDataSet();
    }

    /**
     * Replies true if the active data layer (edit layer) is drawable.
     *
     * @return true if the active data layer (edit layer) is drawable, false otherwise
     */
    public boolean isActiveLayerDrawable() {
         return getEditLayer() != null;
    }

    /**
     * Replies true if the active data layer (edit layer) is visible.
     *
     * @return true if the active data layer (edit layer) is visible, false otherwise
     */
    public boolean isActiveLayerVisible() {
        OsmDataLayer e = getEditLayer();
        return e != null && e.isVisible();
    }

    /**
     * Remove the layer from the mapview. If the layer was in the list before,
     * an LayerChange event is fired.
     * @param layer The layer to remove
     */
    @Deprecated
    public void removeLayer(Layer layer) {
        layerManager.removeLayer(layer);
    }

    @Override
    public void layerRemoving(LayerRemoveEvent e) {
        Layer layer = e.getRemovedLayer();
        if (layer instanceof OsmDataLayer) {
            ((OsmDataLayer) layer).removeLayerPropertyChangeListener(this);
        }

        Main.removeProjectionChangeListener(layer);
        layer.removePropertyChangeListener(this);
        layer.destroy();
        AudioPlayer.reset();

        repaint();
    }

    private boolean virtualNodesEnabled = false;

    public void setVirtualNodesEnabled(boolean enabled) {
        if (virtualNodesEnabled != enabled) {
            virtualNodesEnabled = enabled;
            repaint();
        }
    }

    /**
     * Checks if virtual nodes should be drawn. Default is <code>false</code>
     * @return The virtual nodes property.
     * @see Rendering#render(DataSet, boolean, Bounds)
     */
    public boolean isVirtualNodesEnabled() {
        return virtualNodesEnabled;
    }

    /**
     * Moves the layer to the given new position. No event is fired, but repaints
     * according to the new Z-Order of the layers.
     *
     * @param layer     The layer to move
     * @param pos       The new position of the layer
     */
    public void moveLayer(Layer layer, int pos) {
        layerManager.moveLayer(layer, pos);
    }

    @Override
    public void layerOrderChanged(LayerOrderChangeEvent e) {
        AudioPlayer.reset();
        repaint();
    }

    /**
     * Gets the index of the layer in the layer list.
     * @param layer The layer to search for.
     * @return The index in the list.
     * @throws IllegalArgumentException if that layer does not belong to this view.
     */
    @Deprecated
    public int getLayerPos(Layer layer) {
        int curLayerPos;
            curLayerPos = getAllLayersAsList().indexOf(layer);
        if (curLayerPos == -1)
            throw new IllegalArgumentException(tr("Layer not in list."));
        return curLayerPos;
    }


    private void paintLayer(Layer layer, Graphics2D g, Bounds box) {
        if (layer.getOpacity() < 1) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) layer.getOpacity()));
        }
        layer.paint(g, this, box);
        g.setPaintMode();
    }

    /**
     * Draw the component.
     */
    @Override
    public void paint(Graphics g) {
        if (!prepareToDraw()) {
            return;
        }

        List<Layer> visibleLayers = layerManager.getVisibleLayersInZOrder();

        int nonChangedLayersCount = 0;
        for (Layer l: visibleLayers) {
            if (l.isChanged() || l == changedLayer) {
                break;
            } else {
                nonChangedLayersCount++;
            }
        }

        boolean canUseBuffer;

        synchronized (this) {
            canUseBuffer = !paintPreferencesChanged;
            paintPreferencesChanged = false;
        }
        canUseBuffer = canUseBuffer && nonChangedLayers.size() <= nonChangedLayersCount && lastClipBounds.contains(g.getClipBounds());
        if (canUseBuffer) {
            for (int i = 0; i < nonChangedLayers.size(); i++) {
                if (visibleLayers.get(i) != nonChangedLayers.get(i)) {
                    canUseBuffer = false;
                    break;
                }
            }
        }

        if (null == offscreenBuffer || offscreenBuffer.getWidth() != getWidth() || offscreenBuffer.getHeight() != getHeight()) {
            offscreenBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_3BYTE_BGR);
        }

        Graphics2D tempG = offscreenBuffer.createGraphics();
        tempG.setClip(g.getClip());
        Bounds box = getLatLonBounds(g.getClipBounds());

        if (!canUseBuffer || nonChangedLayersBuffer == null) {
            if (null == nonChangedLayersBuffer
                    || nonChangedLayersBuffer.getWidth() != getWidth() || nonChangedLayersBuffer.getHeight() != getHeight()) {
                nonChangedLayersBuffer = new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_3BYTE_BGR);
            }
            Graphics2D g2 = nonChangedLayersBuffer.createGraphics();
            g2.setClip(g.getClip());
            g2.setColor(PaintColors.getBackgroundColor());
            g2.fillRect(0, 0, getWidth(), getHeight());

            for (int i = 0; i < nonChangedLayersCount; i++) {
                paintLayer(visibleLayers.get(i), g2, box);
            }
        } else {
            // Maybe there were more unchanged layers then last time - draw them to buffer
            if (nonChangedLayers.size() != nonChangedLayersCount) {
                Graphics2D g2 = nonChangedLayersBuffer.createGraphics();
                g2.setClip(g.getClip());
                for (int i = nonChangedLayers.size(); i < nonChangedLayersCount; i++) {
                    paintLayer(visibleLayers.get(i), g2, box);
                }
            }
        }

        nonChangedLayers.clear();
        changedLayer = null;
        for (int i = 0; i < nonChangedLayersCount; i++) {
            nonChangedLayers.add(visibleLayers.get(i));
        }
        lastClipBounds = g.getClipBounds();

        tempG.drawImage(nonChangedLayersBuffer, 0, 0, null);

        for (int i = nonChangedLayersCount; i < visibleLayers.size(); i++) {
            paintLayer(visibleLayers.get(i), tempG, box);
        }

        synchronized (temporaryLayers) {
            for (MapViewPaintable mvp : temporaryLayers) {
                mvp.paint(tempG, this, box);
            }
        }

        // draw world borders
        tempG.setColor(Color.WHITE);
        Bounds b = getProjection().getWorldBoundsLatLon();
        double lat = b.getMinLat();
        double lon = b.getMinLon();

        Point p = getPoint(b.getMin());

        GeneralPath path = new GeneralPath();

        path.moveTo(p.x, p.y);
        double max = b.getMax().lat();
        for (; lat <= max; lat += 1.0) {
            p = getPoint(new LatLon(lat >= max ? max : lat, lon));
            path.lineTo(p.x, p.y);
        }
        lat = max; max = b.getMax().lon();
        for (; lon <= max; lon += 1.0) {
            p = getPoint(new LatLon(lat, lon >= max ? max : lon));
            path.lineTo(p.x, p.y);
        }
        lon = max; max = b.getMinLat();
        for (; lat >= max; lat -= 1.0) {
            p = getPoint(new LatLon(lat <= max ? max : lat, lon));
            path.lineTo(p.x, p.y);
        }
        lat = max; max = b.getMinLon();
        for (; lon >= max; lon -= 1.0) {
            p = getPoint(new LatLon(lat, lon <= max ? max : lon));
            path.lineTo(p.x, p.y);
        }

        int w = getWidth();
        int h = getHeight();

        // Work around OpenJDK having problems when drawing out of bounds
        final Area border = new Area(path);
        // Make the viewport 1px larger in every direction to prevent an
        // additional 1px border when zooming in
        final Area viewport = new Area(new Rectangle(-1, -1, w + 2, h + 2));
        border.intersect(viewport);
        tempG.draw(border);

        if (Main.isDisplayingMapView() && Main.map.filterDialog != null) {
            Main.map.filterDialog.drawOSDText(tempG);
        }

        if (playHeadMarker != null) {
            playHeadMarker.paint(tempG, this);
        }

        g.drawImage(offscreenBuffer, 0, 0, null);
        super.paint(g);
    }

    /**
     * Sets up the viewport to prepare for drawing the view.
     * @return <code>true</code> if the view can be drawn, <code>false</code> otherwise.
     */
    public boolean prepareToDraw() {
        if (initialViewport != null) {
            zoomTo(initialViewport);
            initialViewport = null;
        }
        if (BugReportExceptionHandler.exceptionHandlingInProgress())
            return false;

        if (getCenter() == null)
            return false; // no data loaded yet.

        // if the position was remembered, we need to adjust center once before repainting
        if (oldLoc != null && oldSize != null) {
            Point l1  = getLocationOnScreen();
            final EastNorth newCenter = new EastNorth(
                    getCenter().getX()+ (l1.x-oldLoc.x - (oldSize.width-getWidth())/2.0)*getScale(),
                    getCenter().getY()+ (oldLoc.y-l1.y + (oldSize.height-getHeight())/2.0)*getScale()
                    );
            oldLoc = null; oldSize = null;
            zoomTo(newCenter);
        }

        return true;
    }

    /**
     * @return An unmodifiable collection of all layers
     */
    @Deprecated
    public Collection<Layer> getAllLayers() {
        return layerManager.getLayers();
    }

    /**
     * @return An unmodifiable ordered list of all layers
     */
    @Deprecated
    public List<Layer> getAllLayersAsList() {
        return layerManager.getLayers();
    }

    /**
     * Replies an unmodifiable list of layers of a certain type.
     *
     * Example:
     * <pre>
     *     List&lt;WMSLayer&gt; wmsLayers = getLayersOfType(WMSLayer.class);
     * </pre>
     *
     * @param ofType The layer type.
     * @return an unmodifiable list of layers of a certain type.
     */
    @Deprecated
    public <T extends Layer> List<T> getLayersOfType(Class<T> ofType) {
        return layerManager.getLayersOfType(ofType);
    }

    /**
     * Replies the number of layers managed by this map view
     *
     * @return the number of layers managed by this map view
     */
    @Deprecated
    public int getNumLayers() {
        return getAllLayers().size();
    }

    /**
     * Replies true if there is at least one layer in this map view
     *
     * @return true if there is at least one layer in this map view
     */
    @Deprecated
    public boolean hasLayers() {
        return getNumLayers() > 0;
    }

    /**
     * Sets the active layer to <code>layer</code>. If <code>layer</code> is an instance
     * of {@link OsmDataLayer} also sets {@link #editLayer} to <code>layer</code>.
     *
     * @param layer the layer to be activate; must be one of the layers in the list of layers
     * @throws IllegalArgumentException if layer is not in the lis of layers
     */
    @Deprecated
    public void setActiveLayer(Layer layer) {
    	layerManager.setActiveLayer(layer);
    }
    /**
     * Replies the currently active layer
     *
     * @return the currently active layer (may be null)
     */
    @Deprecated
    public Layer getActiveLayer() {
        return layerManager.getActiveLayer();
    }

    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        /* This only makes the buttons look disabled. Disabling the actions as well requires
         * the user to re-select the tool after i.e. moving a layer. While testing I found
         * that I switch layers and actions at the same time and it was annoying to mind the
         * order. This way it works as visual clue for new users */
        for (final AbstractButton b: Main.map.allMapModeButtons) {
            MapMode mode = (MapMode) b.getAction();
            final boolean activeLayerSupported = mode.layerIsSupported(layerManager.getActiveLayer());
            if (activeLayerSupported) {
                Main.registerActionShortcut(mode, mode.getShortcut()); //fix #6876
            } else {
                Main.unregisterShortcut(mode.getShortcut());
            }
            GuiHelper.runInEDTAndWait(new Runnable() {
                @Override public void run() {
                    b.setEnabled(activeLayerSupported);
                }
            });
        }
        AudioPlayer.reset();
        refreshTitle();
        repaint();
    }

    /**
     * Replies the current edit layer, if any
     *
     * @return the current edit layer. May be null.
     */
    @Deprecated
    public OsmDataLayer getEditLayer() {
        return layerManager.getEditLayer();
    }

    /**
     * replies true if the list of layers managed by this map view contain layer
     *
     * @param layer the layer
     * @return true if the list of layers managed by this map view contain layer
     */
    @Deprecated
    public boolean hasLayer(Layer layer) {
        return layerManager.containsLayer(layer);
    }

    /**
     * Adds a new temporary layer.
     * <p>
     * A temporary layer is a layer that is painted above all normal layers. Layers are painted in the order they are added.
     *
     * @param mvp The layer to paint.
     * @return <code>true</code> if the layer was added.
     */
    public boolean addTemporaryLayer(MapViewPaintable mvp) {
        synchronized (temporaryLayers) {
            return temporaryLayers.add(mvp);
        }
    }

    /**
     * Removes a layer previously added as temporary layer.
     * @param mvp The layer to remove.
     * @return <code>true</code> if that layer was removed.
     */
    public boolean removeTemporaryLayer(MapViewPaintable mvp) {
        synchronized (temporaryLayers) {
            return temporaryLayers.remove(mvp);
        }
    }

    /**
     * Gets a list of temporary layers.
     * @return The layers in the order they are added.
     */
    public List<MapViewPaintable> getTemporaryLayers() {
        synchronized (temporaryLayers) {
            return Collections.unmodifiableList(new ArrayList<>(temporaryLayers));
        }
    }

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
        if (evt.getPropertyName().equals(Layer.VISIBLE_PROP)) {
            repaint();
        } else if (evt.getPropertyName().equals(Layer.OPACITY_PROP) ||
                evt.getPropertyName().equals(Layer.FILTER_STATE_PROP)) {
            Layer l = (Layer) evt.getSource();
            if (l.isVisible()) {
                changedLayer = l;
                repaint();
            }
        } else if (evt.getPropertyName().equals(OsmDataLayer.REQUIRES_SAVE_TO_DISK_PROP)
                || evt.getPropertyName().equals(OsmDataLayer.REQUIRES_UPLOAD_TO_SERVER_PROP)) {
            OsmDataLayer layer = (OsmDataLayer) evt.getSource();
            if (layer == getEditLayer()) {
                refreshTitle();
            }
        }
    }

    /**
     * Sets the title of the JOSM main window, adding a star if there are dirty layers.
     * @see Main#parent
     */
    protected void refreshTitle() {
        if (Main.parent != null) {
            OsmDataLayer editLayer = layerManager.getEditLayer();
            boolean dirty = editLayer != null &&
                    (editLayer.requiresSaveToFile() || (editLayer.requiresUploadToServer() && !editLayer.isUploadDiscouraged()));
            ((JFrame) Main.parent).setTitle((dirty ? "* " : "") + tr("Java OpenStreetMap Editor"));
            ((JFrame) Main.parent).getRootPane().putClientProperty("Window.documentModified", dirty);
        }
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        synchronized (this) {
            paintPreferencesChanged = true;
        }
    }

    @Override
    public void zoomChanged(ZoomChangeEvent e) {
        super.zoomChanged(e);
        synchronized (this) {
            paintPreferencesChanged = true;
        }
    }

    /**
     * A selection listener that fires a repaint as soon as the selection changes.
     */
    private transient SelectionChangedListener repaintSelectionChangedListener = new SelectionChangedListener() {
        @Override
        public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
            repaint();
        }
    };

    public void destroy() {
        layerManager.removeLayerChangeListener(this);
        layerManager.removeActiveLayerChangeListener(this);
        Main.pref.removePreferenceChangeListener(this);
        DataSet.removeSelectionListener(repaintSelectionChangedListener);
        MultipolygonCache.getInstance().clear(this);
        if (mapMover != null) {
            mapMover.destroy();
        }
        nonChangedLayers.clear();
        synchronized (temporaryLayers) {
            temporaryLayers.clear();
        }
    }

    @Override
    public void uploadDiscouragedChanged(OsmDataLayer layer, boolean newValue) {
        if (layer == getEditLayer()) {
            refreshTitle();
        }
    }

    /**
     * Get a string representation of all layers suitable for the {@code source} changeset tag.
     * @return A String of sources separated by ';'
     */
    public String getLayerInformationForSourceTag() {
        final Collection<String> layerInfo = new ArrayList<>();
        if (!getLayersOfType(GpxLayer.class).isEmpty()) {
            // no i18n for international values
            layerInfo.add("survey");
        }
        for (final GeoImageLayer i : getLayersOfType(GeoImageLayer.class)) {
            layerInfo.add(i.getName());
        }
        for (final ImageryLayer i : getLayersOfType(ImageryLayer.class)) {
            layerInfo.add(ImageryInfo.ImageryType.BING.equals(i.getInfo().getImageryType()) ? "Bing" : i.getName());
        }
        return Utils.join("; ", layerInfo);
    }

    /**
     * This is a listener that gets informed whenever repaint is called for this MapView.
     * <p>
     * This is the only safe method to find changes to the map view, since many components call MapView.repaint() directly.
     * @author Michael Zangl
     */
    public interface RepaintListener {
        /**
         * Called when any repaint method is called (using default arguments if required).
         * @param tm see {@link JComponent#repaint(long, int, int, int, int)}
         * @param x see {@link JComponent#repaint(long, int, int, int, int)}
         * @param y see {@link JComponent#repaint(long, int, int, int, int)}
         * @param width see {@link JComponent#repaint(long, int, int, int, int)}
         * @param height see {@link JComponent#repaint(long, int, int, int, int)}
         */
        void repaint(long tm, int x, int y, int width, int height);
    }

    private final CopyOnWriteArrayList<RepaintListener> repaintListeners = new CopyOnWriteArrayList<>();

    /**
     * Adds a listener that gets informed whenever repaint() is called for this class.
     * @param l The listener.
     */
    public void addRepaintListener(RepaintListener l) {
        repaintListeners.add(l);
    }

    /**
     * Removes a registered repaint listener.
     * @param l The listener.
     */
    public void removeRepaintListener(RepaintListener l) {
        repaintListeners.remove(l);
    }

    @Override
    public void repaint(long tm, int x, int y, int width, int height) {
        // This is the main repaint method, all other methods are convenience methods and simply call this method.
        // This is just an observation, not a must, but seems to be true for all implementations I found so far.
        if (repaintListeners != null) {
            // Might get called early in super constructor
            for (RepaintListener l : repaintListeners) {
                l.repaint(tm, x, y, width, height);
            }
        }
        super.repaint(tm, x, y, width, height);
    }
}
