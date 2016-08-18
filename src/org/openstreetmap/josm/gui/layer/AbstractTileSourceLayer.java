// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.image.ImageObserver;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.TemplatedTileSource;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ImageryAdjustAction;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.actions.SaveActionBase;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.imagery.ImageryFilterSettings.FilterChangeListener;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings.DisplaySettingsChangeEvent;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings.DisplaySettingsChangeListener;
import org.openstreetmap.josm.gui.layer.imagery.TileSourcePainter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.WMSLayerImporter;
import org.openstreetmap.josm.tools.GBC;

/**
 * Base abstract class that supports displaying images provided by TileSource. It might be TMS source, WMS or WMTS
 *
 * It implements all standard functions of tilesource based layers: autozoom, tile reloads, layer saving, loading,etc.
 *
 * @author Upliner
 * @author Wiktor Niesiobędzki
 * @param <T> Tile Source class used for this layer
 * @since 3715
 * @since 8526 (copied from TMSLayer)
 */
public abstract class AbstractTileSourceLayer<T extends AbstractTMSTileSource> extends ImageryLayer implements
        ImageObserver, TileLoaderListener, ZoomChangeListener, FilterChangeListener, DisplaySettingsChangeListener {
    private static final String PREFERENCE_PREFIX = "imagery.generic";

    /**
     * Registers all setting properties
     */
    static {
        new TileSourceDisplaySettings();
    }

    /** maximum zoom level supported */
    public static final int MAX_ZOOM = 30;
    /** minium zoom level supported */
    public static final int MIN_ZOOM = 2;

    /** minimum zoom level to show to user */
    public static final IntegerProperty PROP_MIN_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".min_zoom_lvl", 2);
    /** maximum zoom level to show to user */
    public static final IntegerProperty PROP_MAX_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".max_zoom_lvl",
            20);

    //public static final BooleanProperty PROP_DRAW_DEBUG = new BooleanProperty(PREFERENCE_PREFIX + ".draw_debug", false);

    /**
     * Offset between calculated zoom level and zoom level used to download and show tiles. Negative values will result in
     * lower resolution of imagery useful in "retina" displays, positive values will result in higher resolution
     */
    public static final IntegerProperty ZOOM_OFFSET = new IntegerProperty(PREFERENCE_PREFIX + ".zoom_offset", 0);

    /**
     * A timer that is used to delay invalidation events if required.
     */
    private final Timer invalidateLaterTimer = new Timer(100, e -> this.invalidate());

    private final TileSourceDisplaySettings displaySettings = createDisplaySettings();

    private final ImageryAdjustAction adjustAction = new ImageryAdjustAction(this);

    private HashMap<MapView, TileSourcePainter<T>> painters = new HashMap<>();

    /**
     * Creates Tile Source based Imagery Layer based on Imagery Info
     * @param info imagery info
     */
    public AbstractTileSourceLayer(ImageryInfo info) {
        super(info);
        setBackgroundLayer(true);
        this.setVisible(true);
        getFilterSettings().addFilterChangeListener(this);
        getDisplaySettings().addSettingsChangeListener(this);
    }

    /**
     * This method creates the {@link TileSourceDisplaySettings} object. Subclasses may implement it to e.g. change the prefix.
     * @return The object.
     * @since 10568
     */
    protected TileSourceDisplaySettings createDisplaySettings() {
        return new TileSourceDisplaySettings();
    }

    /**
     * Gets the {@link TileSourceDisplaySettings} instance associated with this tile source.
     * @return The tile source display settings
     * @since 10568
     */
    public TileSourceDisplaySettings getDisplaySettings() {
        return displaySettings;
    }

    @Override
    public void filterChanged() {
        invalidate();
    }

    /**
     * Generate the tile loader
     * @param tileSource A tile source that is already generated.
     * @return The tile loader.
     */
    public TileLoader generateTileLoader(T tileSource) {
        Map<String, String> headers = getHeaders(tileSource);

        TileLoader loader = getTileLoaderFactory().makeTileLoader(this, headers);
        if (loader != null) {
            return loader;
        }

        try {
            if ("file".equalsIgnoreCase(new URL(tileSource.getBaseUrl()).getProtocol())) {
                return new OsmTileLoader(this);
            }
        } catch (MalformedURLException e) {
            // ignore, assume that this is not a file
            if (Main.isDebugEnabled()) {
                Main.debug(e.getMessage());
            }
        }

        return new OsmTileLoader(this, headers);
    }

    /**
     * Generates the tile source for this layer.
     * @return The tile source
     */
    public T getTileSource() {
        return getTileSource(getInfo());
    }

    protected abstract TileLoaderFactory getTileLoaderFactory();

    /**
     * Used by the default {@link TileSourcePainter} to create the tile source.
     * @param info imagery info
     * @return TileSource for specified ImageryInfo
     * @throws IllegalArgumentException when Imagery is not supported by layer
     */
    protected T getTileSource(ImageryInfo info) {
        throw new UnsupportedOperationException();
    }

    protected Map<String, String> getHeaders(T tileSource) {
        if (tileSource instanceof TemplatedTileSource) {
            return ((TemplatedTileSource) tileSource).getHeaders();
        }
        return null;
    }

    @Override
    public synchronized void tileLoadingFinished(Tile tile, boolean success) {
        if (tile.hasError()) {
            success = false;
            tile.setImage(null);
        }
        tile.setLoaded(success);
        invalidateLater();
        if (Main.isDebugEnabled()) {
            Main.debug("tileLoadingFinished() tile: " + tile + " success: " + success);
        }
    }

    /**
     * Initiates a repaint of Main.map
     *
     * @see Main#map
     * @see MapFrame#repaint()
     * @see #invalidate() To trigger a repaint of all places where the layer is displayed.
     */
    protected void redraw() {
        invalidate();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings#getDx()}
     */
    @Override
    @Deprecated
    public double getDx() {
        return getDisplaySettings().getDx();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings#getDy()}
     */
    @Override
    @Deprecated
    public double getDy() {
        return getDisplaySettings().getDy();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings}
     */
    @Override
    @Deprecated
    public void displace(double dx, double dy) {
        getDisplaySettings().addDisplacement(new EastNorth(dx, dy));
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings}
     */
    @Override
    @Deprecated
    public void setOffset(double dx, double dy) {
        getDisplaySettings().setDisplacement(new EastNorth(dx, dy));
    }

    @Override
    public Object getInfoComponent() {
        JPanel panel = (JPanel) super.getInfoComponent();
        EastNorth offset = getDisplaySettings().getDisplacement();
        if (offset.distanceSq(0, 0) > 1e-10) {
            panel.add(new JLabel(tr("Offset: ") + offset.east() + ';' + offset.north()), GBC.eol().insets(0, 5, 10, 0));
        }
        return panel;
    }

    @Override
    protected Action getAdjustAction() {
        return adjustAction;
    }

    private static boolean actionSupportLayers(List<Layer> layers) {
        return layers.size() == 1 && layers.get(0) instanceof TMSLayer;
    }

    private class AutoZoomAction extends AbstractAction implements LayerAction {
        AutoZoomAction() {
            super(tr("Auto zoom"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setAutoZoom(!getDisplaySettings().isAutoZoom());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isAutoZoom());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    private class AutoLoadTilesAction extends AbstractAction implements LayerAction {
        AutoLoadTilesAction() {
            super(tr("Auto load tiles"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setAutoLoad(!getDisplaySettings().isAutoLoad());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isAutoLoad());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    private class ShowErrorsAction extends AbstractAction implements LayerAction {
        ShowErrorsAction() {
            super(tr("Show errors"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setShowErrors(!getDisplaySettings().isShowErrors());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isShowErrors());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    @Override
    public void hookUpMapView() {
    }

    @Override
    public LayerPainter attachToMapView(MapViewEvent event) {
        GuiHelper.assertCallFromEdt();
        MapView.addZoomChangeListener(this);

        if (this instanceof NativeScaleLayer) {
            event.getMapView().setNativeScaleLayer((NativeScaleLayer) this);
        }

        TileSourcePainter<T> painter = createMapViewPainter(event);
        painters.put(event.getMapView(), painter);
        return painter;
    }

    @Override
    protected TileSourcePainter<T> createMapViewPainter(MapViewEvent event) {
        return new TileSourcePainter<>(this, event.getMapView());
    }

    /**
     * Tile source layer popup menu.
     */
    public class TileSourceLayerPopup extends JPopupMenu {
        /**
         * Constructs a new {@code TileSourceLayerPopup}.
         * @param mv
         */
        public TileSourceLayerPopup(MapView mv) {
            for (Action a : getCommonEntries()) {
                addAction(a);
            }
            for (Action a : getMapViewEntries(mv)) {
                addAction(a);
            }
        }

        private void addAction(Action a) {
            if (a instanceof LayerAction) {
                add(((LayerAction) a).createMenuComponent());
            } else {
                add(new JMenuItem(a));
            }
        }
    }

    @Override
    public void displaySettingsChanged(DisplaySettingsChangeEvent e) {
        switch (e.getChangedSetting()) {
        //case TileSourceDisplaySettings.AUTO_ZOOM:
        //    if (getDisplaySettings().isAutoZoom() && getBestZoom() != currentZoomLevel) {
        //        setZoomLevel(getBestZoom());
        //        invalidate();
        //    }
        //    break;
        case TileSourceDisplaySettings.AUTO_LOAD:
            if (getDisplaySettings().isAutoLoad()) {
                invalidate();
            }
            break;
        default:
            // trigger a redraw just to be sure.
            invalidate();
        }
    }

    /**
     * Checks zoom level against settings
     * @param maxZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return maximum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMaxZoomLvl(int maxZoomLvl, TileSource ts) {
        if (maxZoomLvl > MAX_ZOOM) {
            maxZoomLvl = MAX_ZOOM;
        }
        if (maxZoomLvl < PROP_MIN_ZOOM_LVL.get()) {
            maxZoomLvl = PROP_MIN_ZOOM_LVL.get();
        }
        if (ts != null && ts.getMaxZoom() != 0 && ts.getMaxZoom() < maxZoomLvl) {
            maxZoomLvl = ts.getMaxZoom();
        }
        return maxZoomLvl;
    }

    /**
     * Checks zoom level against settings
     * @param minZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return minimum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMinZoomLvl(int minZoomLvl, TileSource ts) {
        if (minZoomLvl < MIN_ZOOM) {
            minZoomLvl = MIN_ZOOM;
        }
        if (minZoomLvl > PROP_MAX_ZOOM_LVL.get()) {
            minZoomLvl = getMaxZoomLvl(ts);
        }
        if (ts != null && ts.getMinZoom() > minZoomLvl) {
            minZoomLvl = ts.getMinZoom();
        }
        return minZoomLvl;
    }

    /**
     * @param ts TileSource for which we want to know maximum zoom level
     * @return maximum max zoom level, that will be shown on layer
     */
    public static int getMaxZoomLvl(TileSource ts) {
        return checkMaxZoomLvl(PROP_MAX_ZOOM_LVL.get(), ts);
    }

    /**
     * @param ts TileSource for which we want to know minimum zoom level
     * @return minimum zoom level, that will be shown on layer
     */
    public static int getMinZoomLvl(TileSource ts) {
        return checkMinZoomLvl(PROP_MIN_ZOOM_LVL.get(), ts);
    }

    /**
     * Sets maximum zoom level, that layer will attempt show
     * @param maxZoomLvl maximum zoom level
     */
    public static void setMaxZoomLvl(int maxZoomLvl) {
        PROP_MAX_ZOOM_LVL.put(checkMaxZoomLvl(maxZoomLvl, null));
    }

    /**
     * Sets minimum zoom level, that layer will attempt show
     * @param minZoomLvl minimum zoom level
     */
    public static void setMinZoomLvl(int minZoomLvl) {
        PROP_MIN_ZOOM_LVL.put(checkMinZoomLvl(minZoomLvl, null));
    }

    /**
     * This fires every time the user changes the zoom, but also (due to ZoomChangeListener) - on all
     * changes to visible map (panning/zooming)
     */
    @Override
    public void zoomChanged() {
        invalidate();
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        boolean done = (infoflags & (ERROR | FRAMEBITS | ALLBITS)) != 0;
        if (Main.isDebugEnabled()) {
            Main.debug("imageUpdate() done: " + done + " calling repaint");
        }

        if (done) {
            invalidate();
        } else {
            invalidateLater();
        }
        return !done;
    }

    /**
     * Invalidate the layer at a time in the future so taht the user still sees the interface responsive.
     */
    private void invalidateLater() {
        GuiHelper.runInEDT(() -> {
            if (!invalidateLaterTimer.isRunning()) {
                invalidateLaterTimer.setRepeats(false);
                invalidateLaterTimer.start();
            }
        });
    }

    // This function is called for several zoom levels, not just
    // the current one.  It should not trigger any tiles to be
    // downloaded.  It should also avoid polluting the tile cache
    // with any tiles since these tiles are not mandatory.
    //
    // The "border" tile tells us the boundaries of where we may
    // draw.  It will not be from the zoom level that is being
    // drawn currently.  If drawing the displayZoomLevel,
    // border is null and we draw the entire tile set.
//    private List<Tile> paintTileImages(Graphics g, TileSet ts, int zoom, Tile border) {
//        if (zoom <= 0)
//            return Collections.emptyList();
//        Rectangle2D borderRect = coordinateConverter.getRectangleForTile(border);
//        List<Tile> missedTiles = new LinkedList<>();
//        // The callers of this code *require* that we return any tiles
//        // that we do not draw in missedTiles.  ts.allExistingTiles() by
//        // default will only return already-existing tiles.  However, we
//        // need to return *all* tiles to the callers, so force creation here.
//        for (Tile tile : ts.allTilesCreate()) {
//            Image img = getLoadedTileImage(tile);
//            if (img == null || tile.hasError()) {
//                if (Main.isDebugEnabled()) {
//                    Main.debug("missed tile: " + tile);
//                }
//                missedTiles.add(tile);
//                continue;
//            }
//
//            // applying all filters to this layer
//            img = applyImageProcessors((BufferedImage) img);
//
//            Rectangle2D sourceRect = coordinateConverter.getRectangleForTile(tile);
//            if (borderRect != null && !sourceRect.intersects(borderRect)) {
//                continue;
//            }
//            drawImageInside(g, img, sourceRect, borderRect);
//        }
//        return missedTiles;
//    }

//    private void paintTileText(TileSet ts, Tile tile, Graphics g, MapView mv, int zoom, Tile t) {
//        if (tile == null) {
//            return;
//        }
//        Point2D p = coordinateConverter.getPixelForTile(t);
//        int fontHeight = g.getFontMetrics().getHeight();
//        int x = (int) p.getX();
//        int y = (int) p.getY();
//        int texty = y + 2 + fontHeight;
//
//        /*if (PROP_DRAW_DEBUG.get()) {
//            myDrawString(g, "x=" + t.getXtile() + " y=" + t.getYtile() + " z=" + zoom + "", p.x + 2, texty);
//            texty += 1 + fontHeight;
//            if ((t.getXtile() % 32 == 0) && (t.getYtile() % 32 == 0)) {
//                myDrawString(g, "x=" + t.getXtile() / 32 + " y=" + t.getYtile() / 32 + " z=7", p.x + 2, texty);
//                texty += 1 + fontHeight;
//            }
//        }*/
//
//        /*String tileStatus = tile.getStatus();
//        if (!tile.isLoaded() && PROP_DRAW_DEBUG.get()) {
//            myDrawString(g, tr("image " + tileStatus), p.x + 2, texty);
//            texty += 1 + fontHeight;
//        }*/
//
//        if (tile.hasError() && getDisplaySettings().isShowErrors()) {
//            myDrawString(g, tr("Error") + ": " + tr(tile.getErrorMessage()), x + 2, texty);
//            //texty += 1 + fontHeight;
//        }
//
//        int xCursor = -1;
//        int yCursor = -1;
//        if (Main.isDebugEnabled()) {
//            if (yCursor < t.getYtile()) {
//                if (t.getYtile() % 32 == 31) {
//                    g.fillRect(0, y - 1, mv.getWidth(), 3);
//                } else {
//                    g.drawLine(0, y, mv.getWidth(), y);
//                }
//                //yCursor = t.getYtile();
//            }
//            // This draws the vertical lines for the entire column. Only draw them for the top tile in the column.
//            if (xCursor < t.getXtile()) {
//                if (t.getXtile() % 32 == 0) {
//                    // level 7 tile boundary
//                    g.fillRect(x - 1, 0, 3, mv.getHeight());
//                } else {
//                    g.drawLine(x, 0, x, mv.getHeight());
//                }
//                //xCursor = t.getXtile();
//            }
//        }
//    }

//    private final TileSet nullTileSet = new TileSet();
//
//    private class TileSet extends TileRange {
//
//        protected TileSet(TileXY t1, TileXY t2, int zoom) {
//            super(t1, t2, zoom);
//        }
//
//        /**
//         * null tile set
//         */
//        private TileSet() {
//            // default
//        }
//
//        private boolean tooSmall() {
//            return this.tilesSpanned() < 2.1;
//        }
//
//        private boolean tooLarge() {
//            return insane() || this.tilesSpanned() > 20;
//        }
//
//        private boolean insane() {
//            return tileCache == null || size() > tileCache.getCacheSize();
//        }
//
//        /**
//         * Get all tiles represented by this TileSet that are already in the tileCache.
//         */
//        private List<Tile> allExistingTiles() {
//            return allTiles(p -> getTile(p));
//        }
//
//        private List<Tile> allTilesCreate() {
//            return allTiles(p -> getOrCreateTile(p));
//        }
//
//        private List<Tile> allTiles(Function<TilePosition, Tile> mapper) {
//            return tilePositions().map(mapper).filter(Objects::nonNull).collect(Collectors.toList());
//        }
//
//        @Override
//        public Stream<TilePosition> tilePositions() {
//            if (this.insane()) {
//                // Tileset is either empty or too large
//                return Stream.empty();
//            } else {
//                return super.tilePositions();
//            }
//        }
//
//        private List<Tile> allLoadedTiles() {
//            return allExistingTiles().stream().filter(Tile::isLoaded).collect(Collectors.toList());
//        }
//
//        /**
//         * Call the given paint method for all tiles in this tile set.
//         * <p>
//         * Uses a parallel stream.
//         * @param visitor A visitor to call for each tile.
//         * @param missed a consumer to call for each missed tile.
//         */
//        public void visitTiles(Consumer<Tile> visitor, Consumer<TilePosition> missed) {
//            tilePositions().parallel().forEach(tp -> visitTilePosition(visitor, tp, missed));
//        }
//
//        private void visitTilePosition(Consumer<Tile> visitor, TilePosition tp, Consumer<TilePosition> missed) {
//            Tile tile = getTile(tp);
//            if (tile == null) {
//                missed.accept(tp);
//            } else {
//                visitor.accept(tile);
//            }
//        }
//
//    }

//    private class DeepTileSet {
//        private final ProjectionBounds bounds;
//        private final int minZoom, maxZoom;
//        private final TileSet[] tileSets;
//        private final TileSetInfo[] tileSetInfos;
//
//        @SuppressWarnings("unchecked")
//        DeepTileSet(ProjectionBounds bounds, int minZoom, int maxZoom) {
//            this.bounds = bounds;
//            this.minZoom = minZoom;
//            this.maxZoom = maxZoom;
//            this.tileSets = new AbstractTileSourceLayer.TileSet[maxZoom - minZoom + 1];
//            this.tileSetInfos = new TileSetInfo[maxZoom - minZoom + 1];
//        }
//
//        public TileSet getTileSet(int zoom) {
//            if (zoom < minZoom)
//                return nullTileSet;
//            synchronized (tileSets) {
//                TileSet ts = tileSets[zoom - minZoom];
//                if (ts == null) {
//                    ts = AbstractTileSourceLayer.this.getTileSet(bounds.getMin(), bounds.getMax(), zoom);
//                    tileSets[zoom - minZoom] = ts;
//                }
//                return ts;
//            }
//        }
//
//        public TileSetInfo getTileSetInfo(int zoom) {
//            if (zoom < minZoom)
//                return new TileSetInfo();
//            synchronized (tileSetInfos) {
//                TileSetInfo tsi = tileSetInfos[zoom - minZoom];
//                if (tsi == null) {
//                    tsi = AbstractTileSourceLayer.getTileSetInfo(getTileSet(zoom));
//                    tileSetInfos[zoom - minZoom] = tsi;
//                }
//                return tsi;
//            }
//        }
//    }
//
//    @Override
//    public void paint(Graphics2D g, MapView mv, Bounds bounds) {
//        // old and unused.
//    }
//
//    void drawInViewArea(Graphics2D g, MapView mv, ProjectionBounds pb) {
//        DeepTileSet dts = new DeepTileSet(pb, getMinZoomLvl(), zoom);
//        TileSet ts = dts.getTileSet(zoom);
//
//        int displayZoomLevel = zoom;
//
//        boolean noTilesAtZoom = false;
//
//        // Too many tiles... refuse to download
//        if (!ts.tooLarge()) {
//            //Main.debug("size: " + ts.size() + " spanned: " + ts.tilesSpanned());
//            ts.loadAllTiles(false);
//        }
//
//        if (displayZoomLevel != zoom) {
//            ts = dts.getTileSet(displayZoomLevel);
//        }
//
//        g.setColor(Color.DARK_GRAY);
//
//        List<Tile> missedTiles = this.paintTileImages(g, ts);
//        int[] otherZooms = ;
//        for (int zoomOffset : otherZooms) {
//            if (!getDisplaySettings().isAutoZoom()) {
//                break;
//            }
//            int newzoom = displayZoomLevel + zoomOffset;
//            if (newzoom < getMinZoomLvl() || newzoom > getMaxZoomLvl()) {
//                continue;
//            }
//            if (missedTiles.isEmpty()) {
//                break;
//            }
//            List<Tile> newlyMissedTiles = new LinkedList<>();
//            for (Tile missed : missedTiles) {
//                if ("no-tile".equals(missed.getValue("tile-info")) && zoomOffset > 0) {
//                    newlyMissedTiles.add(missed);
//                    continue;
//                }
//                Tile t2 = tempCornerTile(missed);
//                TileSet ts2 = getTileSet(getShiftedLatLon(tileSource.tileXYToLatLon(missed)),
//                        getShiftedLatLon(tileSource.tileXYToLatLon(t2)), newzoom);
//                // Instantiating large TileSets is expensive.  If there
//                // are no loaded tiles, don't bother even trying.
//                if (ts2.allLoadedTiles().isEmpty()) {
//                    newlyMissedTiles.add(missed);
//                    continue;
//                }
//                if (ts2.tooLarge()) {
//                    continue;
//                }
//                newlyMissedTiles.addAll(this.paintTileImages(g, ts2, newzoom, missed));
//            }
//            missedTiles = newlyMissedTiles;
//        }
//        if (Main.isDebugEnabled() && !missedTiles.isEmpty()) {
//            Main.debug("still missed " + missedTiles.size() + " in the end");
//        }
//
//    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        actions.addAll(Arrays.asList(getLayerListEntries()));
        actions.addAll(Arrays.asList(getCommonEntries()));
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[actions.size()]);
    }

    public Action[] getLayerListEntries() {
        return new Action[] { LayerListDialog.getInstance().createActivateLayerAction(this),
                LayerListDialog.getInstance().createShowHideLayerAction(),
                LayerListDialog.getInstance().createDeleteLayerAction(), SeparatorLayerAction.INSTANCE,
                // color,
                new OffsetAction(), new RenameLayerAction(this.getAssociatedFile(), this),
                SeparatorLayerAction.INSTANCE };
    }

    /**
     * Returns the common menu entries.
     * @return the common menu entries
     */
    public Action[] getCommonEntries() {
        return new Action[] {
                new AutoLoadTilesAction(),
                new AutoZoomAction(),
                new ShowErrorsAction() };
    }

    private List<Action> getMapViewEntries(MapView mv) {
        TileSourcePainter<T> painter = painters.get(mv);
        return painter.getMenuEntries();
    }

    @Override
    public String getToolTipText() {
        String currentZoomLevel = painters.values().stream().findAny().map(TileSourcePainter::getZoomString).orElse("?");
        if (getDisplaySettings().isAutoLoad()) {
            return tr("{0} ({1}), automatically downloading in zoom {2}", this.getClass().getSimpleName(), getName(),
                    currentZoomLevel);
        } else {
            return tr("{0} ({1}), downloading in zoom {2}", this.getClass().getSimpleName(), getName(),
                    currentZoomLevel);
        }
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public boolean isChanged() {
        // we use #invalidate()
        return false;
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // never called, we use a custom painter
        throw new UnsupportedOperationException();
    }

    /**
     * Task responsible for precaching imagery along the gpx track
     *
     */
    public class PrecacheTask implements TileLoaderListener {
        private final ProgressMonitor progressMonitor;
        private int totalCount;
        private final AtomicInteger processedCount = new AtomicInteger(0);
        private final TileLoader tileLoader;

        /**
         * @param progressMonitor that will be notified about progess of the task
         */
        public PrecacheTask(ProgressMonitor progressMonitor) {
            this.progressMonitor = progressMonitor;
            // TODO
            T tileSource = null;
            this.tileLoader = getTileLoaderFactory().makeTileLoader(this, getHeaders(tileSource));
            if (this.tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) this.tileLoader)
                        .setDownloadExecutor(TMSCachedTileLoader.getNewThreadPoolExecutor("Precache downloader"));
            }
        }

        /**
         * @return true, if all is done
         */
        public boolean isFinished() {
            return processedCount.get() >= totalCount;
        }

        /**
         * @return total number of tiles to download
         */
        public int getTotalCount() {
            return totalCount;
        }

        /**
         * cancel the task
         */
        public void cancel() {
            if (tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) tileLoader).cancelOutstandingTasks();
            }
        }

        @Override
        public void tileLoadingFinished(Tile tile, boolean success) {
            int processed = this.processedCount.incrementAndGet();
            if (success) {
                this.progressMonitor.worked(1);
                this.progressMonitor.setCustomText(tr("Downloaded {0}/{1} tiles", processed, totalCount));
            } else {
                Main.warn("Tile loading failure: " + tile + " - " + tile.getErrorMessage());
            }
        }

        /**
         * @return tile loader that is used to load the tiles
         */
        public TileLoader getTileLoader() {
            return tileLoader;
        }
    }

    /**
     * Calculates tiles, that needs to be downloaded to cache, gets a current tile loader and creates a task to download
     * all of the tiles. Buffer contains at least one tile.
     *
     * To prevent accidental clear of the queue, new download executor is created with separate queue
     *
     * @param progressMonitor progress monitor for download task
     * @param points lat/lon coordinates to download
     * @param bufferX how many units in current Coordinate Reference System to cover in X axis in both sides
     * @param bufferY how many units in current Coordinate Reference System to cover in Y axis in both sides
     * @return precache task representing download task
     */
    public AbstractTileSourceLayer<T>.PrecacheTask downloadAreaToCache(final ProgressMonitor progressMonitor,
            List<LatLon> points, double bufferX, double bufferY) {
        PrecacheTask precacheTask = new PrecacheTask(progressMonitor);
        final Set<Tile> requestedTiles = new ConcurrentSkipListSet<>(
                (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getKey(), o2.getKey()));
        for (LatLon point : points) {
            //TODO
            TileSource tileSource = null;
            int currentZoomLevel = 0;
            TileXY minTile = tileSource.latLonToTileXY(point.lat() - bufferY, point.lon() - bufferX, currentZoomLevel);
            TileXY curTile = tileSource.latLonToTileXY(point.toCoordinate(), currentZoomLevel);
            TileXY maxTile = tileSource.latLonToTileXY(point.lat() + bufferY, point.lon() + bufferX, currentZoomLevel);

            // take at least one tile of buffer
            int minY = Math.min(curTile.getYIndex() - 1, minTile.getYIndex());
            int maxY = Math.max(curTile.getYIndex() + 1, maxTile.getYIndex());
            int minX = Math.min(curTile.getXIndex() - 1, minTile.getXIndex());
            int maxX = Math.max(curTile.getXIndex() + 1, maxTile.getXIndex());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    requestedTiles.add(new Tile(tileSource, x, y, currentZoomLevel));
                }
            }
        }

        precacheTask.totalCount = requestedTiles.size();
        precacheTask.progressMonitor.setTicksCount(requestedTiles.size());

        TileLoader loader = precacheTask.getTileLoader();
        for (Tile t : requestedTiles) {
            loader.createTileLoaderJob(t).submit();
        }
        return precacheTask;
    }

    @Override
    public boolean isSavable() {
        return true; // With WMSLayerExporter
    }

    @Override
    public File createAndOpenSaveFileChooser() {
        return SaveActionBase.createAndOpenSaveFileChooser(tr("Save WMS file"), WMSLayerImporter.FILE_FILTER);
    }

    @Override
    public void destroy() {
        super.destroy();
        adjustAction.destroy();
    }

    /**
     * A {@link TileSourcePainter} notifies us of a dispatch
     * @param tileSourcePainter The painter.
     */
    public void detach(TileSourcePainter<T> tileSourcePainter) {
        GuiHelper.assertCallFromEdt();
        painters.entrySet().removeIf(e -> e.getValue().equals(tileSourcePainter));
    }
}
