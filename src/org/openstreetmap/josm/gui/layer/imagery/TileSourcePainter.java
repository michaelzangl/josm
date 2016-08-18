// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.io.IOException;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Stream;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

import org.openstreetmap.gui.jmapviewer.AttributionSupport;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.ICoordinate;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.MapViewState.MapViewLatLonRectangle;
import org.openstreetmap.josm.gui.MapViewState.MapViewPoint;
import org.openstreetmap.josm.gui.MapViewState.MapViewRectangle;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.ImageryLayer;
import org.openstreetmap.josm.gui.layer.MapViewGraphics;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.LayerPainter;
import org.openstreetmap.josm.gui.layer.MapViewPaintable.MapViewEvent;
import org.openstreetmap.josm.gui.layer.imagery.TileForAreaFinder.TileForAreaGetter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.MemoryManager;
import org.openstreetmap.josm.tools.MemoryManager.MemoryHandle;
import org.openstreetmap.josm.tools.MemoryManager.NotEnoughMemoryException;
import org.openstreetmap.josm.tools.Pair;

public class TileSourcePainter<T extends AbstractTMSTileSource> implements LayerPainter, ZoomChangeListener, TileForAreaGetter {
    /**
     *
     */
    protected final AbstractTileSourceLayer<T> layer;
    private static final Font INFO_FONT = new Font("sansserif", Font.BOLD, 13);
    /**
     * Absolute maximum of tiles to paint
     */
    private static final int MAX_TILES = 500;

    /*
     *  use MemoryTileCache instead of tileLoader JCS cache, as tileLoader caches only content (byte[] of image)
     *  and MemoryTileCache caches whole Tile. This gives huge performance improvement when a lot of tiles are visible
     *  in MapView (for example - when limiting min zoom in imagery)
     *
     *  Use per-layer tileCache instance, as the more layers there are, the more tiles needs to be cached
     */
    private final TileCache tileCache; // initialized together with tileSource
    protected final T tileSource;
    private final TileLoader tileLoader;
    private final AttributionSupport attribution = new AttributionSupport();

    protected final ZoomLevelManager zoom;

    private final TextPainter textPainter;

    private TilePosition highlightPosition;

    /**
     * The memory handle that will hold our tile source.
     */
    private MemoryHandle<?> memory;

    final MouseAdapter adapter = new MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON3) {
                TilePosition tilePos = getTileForPixelpos(mapView.getState().getForView(e.getPoint()));
                JPopupMenu popup = layer.new TileSourceLayerPopup(mapView);
                popup.addPopupMenuListener(new PopupMenuListener() {
                    @Override
                    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
                        highlightPosition = tilePos;
                        // triggers repaint
                        layer.invalidate();
                    }

                    @Override
                    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
                        highlightPosition = null;
                        layer.invalidate();
                    }

                    @Override
                    public void popupMenuCanceled(PopupMenuEvent e) {
                        // ignore
                    }
                });
                if (tilePos != null) {
                    popup.add(new JSeparator());
                    popup.add(new JMenuItem(new LoadTileAction(tilePos)));
                    Tile tile = getOrCreateTile(tilePos);
                    if (tile != null) {
                        popup.add(new JMenuItem(new ShowTileInfoAction(tile)));
                    }
                }
                popup.show(e.getComponent(), e.getX(), e.getY());
            } else if (e.getButton() == MouseEvent.BUTTON1) {
                attribution.handleAttribution(e.getPoint(), true);
            }
        }

        /**
         * Returns tile for a pixel position.<p>
         * This isn't very efficient, but it is only used when the user right-clicks on the map.
         * @param mapViewPoint pixel coordinate
         * @return Tile at pixel position
         */
        private TilePosition getTileForPixelpos(MapViewPoint mapViewPoint) {
            Main.trace("getTileForPixelpos({0})", mapViewPoint);
            TileCoordinateConverter converter = generateCoordinateConverter();

            TileRange ts = converter.getViewAtZoom(zoom.getCurrentZoomLevel());

            Stream<TilePosition> clickedTiles = ts.tilePositions()
                    .filter(t -> converter.getAreaForTile(t).contains(mapViewPoint));
            if (Main.isTraceEnabled()) {
                clickedTiles = clickedTiles.peek(t -> Main.trace("Clicked on tile: {0}, {1};  currentZoomLevel: {2}",
                        t.getX(), t.getY(), zoom.getCurrentZoomLevel()));
            }
            return clickedTiles.findAny().orElse(null);
        }
    };

    private final MapView mapView;

    /**
     * Create a new {@link TileSourcePainter}
     * @param layer The layer to paint
     * @param mapView The map view to paint for.
     */
    public TileSourcePainter(AbstractTileSourceLayer<T> layer, MapView mapView) {
        this.layer = layer;
        this.mapView = mapView;
        MapView.addZoomChangeListener(this);
        mapView.addMouseListener(adapter);

        tileSource = generateTileSource();
        if (tileSource == null) {
            throw new IllegalArgumentException(tr("Failed to create tile source"));
        }

        attribution.initialize(this.tileSource);

        tileLoader = layer.generateTileLoader(tileSource);

        tileCache = new MemoryTileCache(estimateTileCacheSize());
        textPainter = new TextPainter();
        zoom = new ZoomLevelManager(getSettings(), tileSource, generateCoordinateConverter());
        zoom.setZoomBounds(layer.getInfo());
    }

    protected T generateTileSource() {
        return layer.getTileSource();
    }

    @Override
    public void paint(MapViewGraphics graphics) {
        allocateCacheMemory();

        textPainter.start(graphics.getDefaultGraphics());

        if (memory != null) {
            doPaint(graphics);
        } else {
            textPainter.addTextOverlay(tr("There is noth enough memory to display this layer."));
        }
    }

    private TileCoordinateConverter generateCoordinateConverter() {
        return new TileCoordinateConverter(mapView.getState(), tileSource, getSettings());
    }

    private void doPaint(MapViewGraphics graphics) {
        MapViewRectangle pb = graphics.getClipBounds();

        drawInViewArea(graphics.getDefaultGraphics(), graphics.getMapView(), pb);
    }

    private void drawInViewArea(Graphics2D g, MapView mapView, MapViewRectangle rect) {
        g.setFont(INFO_FONT);
        TileCoordinateConverter converter = generateCoordinateConverter();
        zoom.updateZoomLevel(converter, this);
        loadTilesInView(converter);

        TileRange baseRange = converter.getViewAtZoom(zoom.getCurrentZoomLevel());

        Shape clip = g.getClip();
        g.setClip(converter.getProjectionClip());
        Stream<TilePosition> area;
        if (getSettings().isAutoZoom()) {
            area = TileForAreaFinder.getWithFallbackZoom(baseRange, this, zoom);
        } else {
            area = TileForAreaFinder.getAtDefaultZoom(baseRange, this);
        }
        paintTileImages(g, area);
        g.setClip(clip);

        if (highlightPosition != null) {
            paintHighlight(g, converter, highlightPosition);
        }
        paintStatus(baseRange, mapView.getProjection());
        paintAttribution(g, rect);
        if (Main.isDebugEnabled()) {
            paintDebug();
        }
    }

    private void loadTilesInView(TileCoordinateConverter converter) {
        int zoomToLoad = zoom.getDisplayZoomLevel();
        TileRange range = converter.getViewAtZoom(zoomToLoad);

        if (getSettings().isAutoZoom()) {
        // If all tiles at displayZoomLevel is loaded, load all tiles at next zoom level
        // to make sure there're really no more zoom levels
        if (zoomToLoad < zoom.getCurrentZoomLevel() && !hasTiles(range, TileSourcePainter::isMissing)) {
            zoomToLoad++;
            range = converter.getViewAtZoom(zoomToLoad);
        } else  {
            // When we have overzoomed tiles and all tiles at current zoomlevel is loaded,
            // load tiles at previovus zoomlevels until we have all tiles on screen is loaded.
            // loading is done in the next if section
            while (zoomToLoad > zoom.getMinZoom() && hasTiles(range, TileSourcePainter::isOverzoomed)
                    && !hasTiles(range, TileSourcePainter::isMissing)) {
                zoomToLoad--;
                range = converter.getViewAtZoom(zoomToLoad);
            }
        }
        }
        loadTiles(range, false);
    }

    /**
     * Paints a highlight rectangle around a tile.
     * @param g
     * @param converter
     * @param tile
     */
    private static void paintHighlight(Graphics2D g, TileCoordinateConverter converter, TilePosition tile) {
        MapViewLatLonRectangle area = converter.getAreaForTile(tile);
        g.setColor(Color.RED);
        g.draw(area.getInView());
    }

    /**
     * Paint the filtered images for the given tiles
     * @param g The graphics to paint on
     * @param area The tiles to paint.
     */
    private void paintTileImages(Graphics2D g, Stream<TilePosition> area) {
        TileCoordinateConverter converter = generateCoordinateConverter();
        Rectangle b = g.getClipBounds();
        int maxTiles = (int) (b.getWidth() * b.getHeight() / tileSource.getTileSize() / tileSource.getTileSize() * 5);
        List<Tile> errorTiles = Collections.synchronizedList(new ArrayList<>());
        Stream<Tile> tiles = area.parallel()
            .limit(Math.min(maxTiles, MAX_TILES))
            .map(this::getTile)
            .filter(Objects::nonNull);

        if (getSettings().isShowErrors()) {
            tiles = tiles.peek(t -> { if (t.hasError()) errorTiles.add(t); });
        }
        tiles.map(tile -> new Pair<>(tile, tile.getImage()))
            .filter(p -> imageLoaded(p.b))
            .map(p -> new Pair<>(p.a, layer.applyImageProcessors(p.b)))
            .forEachOrdered(p -> paintTileImage(g, p.a, p.b, converter));

        for (Tile error : errorTiles) {
            textPainter.drawTileString(tr("Error") + ": " + tr(error.getErrorMessage()),
                    new TilePosition(error), converter);
        }
    }

    /**
     * We only paint full tile images.
     * <p>
     * We handle that the correct tile images are in front by sorting the list of tiles accordingly.
     * @param g The graphics to paint on
     * @param tile The tile to paint
     * @param image The image to paint for the tile
     * @param converter The coordinate converter.
     */
    private void paintTileImage(Graphics2D g, Tile tile, BufferedImage image, TileCoordinateConverter converter) {
        AffineTransform transform = converter.getTransformForTile(new TilePosition(tile), 0, 0, 0, 1, 1, 1);
        transform.scale(1.0 / image.getWidth(), 1.0 / image.getHeight());

        g.drawImage(image, transform, layer);

        if (Main.isTraceEnabled()) {
            textPainter.drawTileString(tile.getKey(), new TilePosition(tile), converter);
        }
    }

    private void paintAttribution(Graphics2D defaultGraphics, MapViewRectangle rect) {
        Rectangle2D inView = rect.getInView();
        Graphics2D g = (Graphics2D) defaultGraphics.create();
        g.translate(inView.getMinX(), inView.getMinY());
        Bounds boundsBox = rect.getLatLonBoundsBox();
        attribution.paintAttribution(g, (int) inView.getWidth(), (int) inView.getHeight(),
                boundsBox.getMin().toCoordinate(), boundsBox.getMax().toCoordinate(), zoom.getDisplayZoomLevel(),
                layer);
    }

    private void paintStatus(TileRange baseRange, Projection projection) {
        if (isTooLarge(baseRange)) {
            textPainter.addTextOverlay(tr("zoom in to load more tiles"));
        } else if (!getSettings().isAutoZoom() && isTooSmall(baseRange)) {
            textPainter.addTextOverlay(tr("increase tiles zoom level (change resolution) to see more detail"));
        } else if (getSettings().isAutoZoom() && getSettings().isAutoLoad() && !hasTiles(baseRange, TileSourcePainter::isVisible)
                && (!hasTiles(baseRange, TileSourcePainter::isLoading) || hasTiles(baseRange, TileSourcePainter::isOverzoomed))) {
            textPainter.addTextOverlay(tr("No tiles at this zoom level"));
        }

        if (!isProjectionSupported(projection)) {
            textPainter.addTextOverlay(tr("The tile source does not support the current projection natively"));
        }
    }

    /**
     * Check whether this layer supports the given projection
     * @param projection The projection to search
     * @return <code>true</code> if supported.
     */
    protected boolean isProjectionSupported(Projection projection) {
        return true;
    }

    /**
     * Check if there are any matching tiles in the given range
     * @param range The range to check in
     * @param pred The predicate the tiles need to match
     * @return If there are such tiles.
     */
    public boolean hasTiles(TileRange range, Predicate<Tile> pred) {
        return range.tilePositions().map(this::getTile).anyMatch(pred);
    }

    /**
     * Test if a tile is visible.
     * @param t The tile to test
     * @return <code>true</code> if it is visible
     */
    public static boolean isVisible(Tile t) {
        return t != null && t.isLoaded() && !t.hasError();
    }

    /**
     * Test if a tile is missing.
     * @param t The tile to test
     * @return <code>true</code> if it is loading or not loaded yet.
     */
    public static boolean isMissing(Tile t) {
        return t == null || t.isLoading();
    }

    /**
     * Test if a tile is marked as loading.
     * @param t The tile to test
     * @return <code>true</code> if it is loading
     */
    public static boolean isLoading(Tile t) {
        return t != null && t.isLoading();
    }

    /**
     * Test if a tile is marked as overzoomed.
     * @param t The tile to test
     * @return <code>true</code> if it is overzoomed
     */
    public static boolean isOverzoomed(Tile t) {
        return t != null && "no-tile".equals(t.getValue("tile-info"));
    }

    private void paintDebug() {
        for (String s : zoom.getDebugInfo(generateCoordinateConverter())) {
            textPainter.addDebug(s);
        }
        textPainter.addDebug(tr("Estimated cache size: {0}", estimateTileCacheSize()));
        if (tileLoader instanceof TMSCachedTileLoader) {
            TMSCachedTileLoader cachedTileLoader = (TMSCachedTileLoader) tileLoader;
            for (String part : cachedTileLoader.getStats().split("\n")) {
                textPainter.addDebug(tr("Cache stats: {0}", part));
            }
        }
    }

    private boolean imageLoaded(Image i) {
        if (i == null) {
            return false;
        } else {
            int status = Toolkit.getDefaultToolkit().checkImage(i, -1, -1, layer);
            return (status & ImageObserver.ALLBITS) != 0;
        }
    }


    // 'source' is the pixel coordinates for the area that
    // the img is capable of filling in.  However, we probably
    // only want a portion of it.
    //
    // 'border' is the screen cordinates that need to be drawn.
    //  We must not draw outside of it.
    private void drawImageInside(Graphics g, Image sourceImg, Rectangle2D source, Rectangle2D border) {
        Rectangle2D target = source;

        // If a border is specified, only draw the intersection
        // if what we have combined with what we are supposed to draw.
        if (border != null) {
            target = source.createIntersection(border);
            if (Main.isDebugEnabled()) {
                Main.debug("source: " + source + "\nborder: " + border + "\nintersection: " + target);
            }
        }

        // All of the rectangles are in screen coordinates.  We need
        // to how these correlate to the sourceImg pixels.  We could
        // avoid doing this by scaling the image up to the 'source' size,
        // but this should be cheaper.
        //
        // In some projections, x any y are scaled differently enough to
        // cause a pixel or two of fudge.  Calculate them separately.
        double imageYScaling = sourceImg.getHeight(layer) / source.getHeight();
        double imageXScaling = sourceImg.getWidth(layer) / source.getWidth();

        // How many pixels into the 'source' rectangle are we drawing?
        double screenXoffset = target.getX() - source.getX();
        double screenYoffset = target.getY() - source.getY();
        // And how many pixels into the image itself does that correlate to?
        int imgXoffset = (int) (screenXoffset * imageXScaling + 0.5);
        int imgYoffset = (int) (screenYoffset * imageYScaling + 0.5);
        // Now calculate the other corner of the image that we need
        // by scaling the 'target' rectangle's dimensions.
        int imgXend = imgXoffset + (int) (target.getWidth() * imageXScaling + 0.5);
        int imgYend = imgYoffset + (int) (target.getHeight() * imageYScaling + 0.5);

        if (Main.isDebugEnabled()) {
            Main.debug("drawing image into target rect: " + target);
        }
        g.drawImage(sourceImg, (int) target.getX(), (int) target.getY(), (int) target.getMaxX(), (int) target.getMaxY(),
                imgXoffset, imgYoffset, imgXend, imgYend, layer);
        if (ImageryLayer.PROP_FADE_AMOUNT.get() != 0) {
            // dimm by painting opaque rect...
            g.setColor(ImageryLayer.getFadeColorWithAlpha());
            ((Graphics2D) g).fill(target);
        }
    }

    private Tile getOrCreateTile(TilePosition tilePosition) {
        Tile tile = getTile(tilePosition);
        if (tile == null) {
            tile = new Tile(tileSource, tilePosition.getX(), tilePosition.getY(), tilePosition.getZoom());
            tileCache.addTile(tile);
        }

        if (!tile.isLoaded()) {
            tile.loadPlaceholderFromCache(tileCache);
        }
        return tile;
    }

    /**
     * Returns tile at given position.
     * This can and will return null for tiles that are not already in the cache.
     * @param tilePosition The position
     * @return tile at given position
     */
    private Tile getTile(TilePosition tilePosition) {
        if (!contains(tilePosition)) {
            return null;
        } else {
            return tileCache.getTile(tileSource, tilePosition.getX(), tilePosition.getY(), tilePosition.getZoom());
        }
    }

    /**
     * Check if this tile source contains the given position.
     * @param position The position
     * @return <code>true</code> if that positon is contained.
     */
    private boolean contains(TilePosition position) {
        return position.getZoom() >= tileSource.getMinZoom() && position.getZoom() <= tileSource.getMaxZoom()
                && position.getX() >= tileSource.getTileXMin(position.getZoom())
                && position.getX() <= tileSource.getTileXMax(position.getZoom())
                && position.getY() >= tileSource.getTileYMin(position.getZoom())
                && position.getY() <= tileSource.getTileYMax(position.getZoom());
    }

    protected void loadAllTiles(boolean force) {
        loadTiles(generateCoordinateConverter().getViewAtZoom(zoom.getCurrentZoomLevel()), force);
    }

    private void loadTiles(TileRange range, boolean force) {
        if (getSettings().isAutoLoad() || force) {
            if (isTooLarge(range)) {
                Main.warn("Not downloading all tiles because there are too many tiles on an axis!");
            } else {
                range.tilePositionsSorted().filter(this::contains).forEach(t -> loadTile(t, force));
            }
        }
    }

    private static boolean isTooSmall(TileRange range) {
        return range.tilesSpanned() < 2;
    }

    private boolean isTooLarge(TileRange range) {
        return range.size() > tileCache.getCacheSize() || range.tilesSpanned() > 20;
    }

    private void loadErrorTiles(TileRange range, boolean force) {
        if (getSettings().isAutoLoad() || force) {
            range.tilePositionsSorted().map(this::getOrCreateTile).filter(Tile::hasError)
                    .forEach(t -> tileLoader.createTileLoaderJob(t).submit(force));
        }
    }

    protected void loadAllErrorTiles(boolean force) {
        loadErrorTiles(generateCoordinateConverter().getViewAtZoom(zoom.getCurrentZoomLevel()), force);
    }

    private boolean loadTile(TilePosition tile, boolean force) {
        return loadTile(getOrCreateTile(tile), force);
    }

    private boolean loadTile(Tile tile, boolean force) {
        if (tile == null)
            return false;
        if (!force && (tile.isLoaded() || tile.hasError() || isOverzoomed(tile)))
            return false;
        if (tile.isLoading())
            return false;
        tileLoader.createTileLoaderJob(tile).submit(force);
        return true;
    }

    @Override
    public void zoomChanged() {
        if (tileLoader instanceof TMSCachedTileLoader) {
            ((TMSCachedTileLoader) tileLoader).cancelOutstandingTasks();
        }
    }

    private TileSourceDisplaySettings getSettings() {
        return layer.getDisplaySettings();
    }

    private void allocateCacheMemory() {
        if (memory == null) {
            MemoryManager manager = MemoryManager.getInstance();
            if (manager.isAvailable(getEstimatedCacheSize())) {
                try {
                    memory = manager.allocateMemory("tile source layer", getEstimatedCacheSize(), Object::new);
                } catch (NotEnoughMemoryException e) {
                    Main.warn("Could not allocate tile source memory", e);
                }
            }
        }
    }

    protected long getEstimatedCacheSize() {
        return 4L * tileSource.getTileSize() * tileSource.getTileSize() * estimateTileCacheSize();
    }

    protected int estimateTileCacheSize() {
        Dimension screenSize = GuiHelper.getMaximumScreenSize();
        int height = screenSize.height;
        int width = screenSize.width;
        int tileSize = 256; // default tile size
        if (tileSource != null) {
            tileSize = tileSource.getTileSize();
        }
        // as we can see part of the tile at the top and at the bottom, use Math.ceil(...) + 1 to accommodate for that
        int visibileTiles = (int) (Math.ceil((double) height / tileSize + 1)
                * Math.ceil((double) width / tileSize + 1));
        // add 10% for tiles from different zoom levels
        // use offset to decide, how many tiles are visible
        int ret = (int) Math.ceil(Math.pow(2d, AbstractTileSourceLayer.ZOOM_OFFSET.get()) * visibileTiles * 4);
        Main.info("AbstractTileSourceLayer: estimated visible tiles: {0}, estimated cache size: {1}", visibileTiles,
                ret);
        return ret;
    }

    /**
     * Gets the menu entries for this layer
     * @return The menu entries
     */
    public List<Action> getMenuEntries() {
        return Arrays.asList(zoom.new IncreaseZoomAction(), zoom.new DecreaseZoomAction(),
                zoom.new ZoomToBestAction(mapView), zoom.new ZoomToNativeLevelAction(mapView),
                new FlushTileCacheAction(), new LoadErroneusTilesAction(), new LoadAllTilesAction());
    }

    /**
     * Gets the current zoom level as String
     * @return The zoom level.
     */
    public String getZoomString() {
        return Integer.toString(zoom.getCurrentZoomLevel());
    }

    @Override
    public void detachFromMapView(MapViewEvent event) {
        event.getMapView().removeMouseListener(adapter);
        MapView.removeZoomChangeListener(this);
        if (memory != null) {
            memory.free();
        }
        layer.detach(this);
    }

    private class LoadAllTilesAction extends AbstractAction {
        LoadAllTilesAction() {
            super(tr("Load all tiles"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            loadAllTiles(true);
        }
    }

    private class LoadErroneusTilesAction extends AbstractAction {
        LoadErroneusTilesAction() {
            super(tr("Load all error tiles"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            loadAllErrorTiles(true);
        }
    }

    private class FlushTileCacheAction extends AbstractAction {
        FlushTileCacheAction() {
            super(tr("Flush tile cache"));
            setEnabled(tileLoader instanceof CachedTileLoader);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            new PleaseWaitRunnable(tr("Flush tile cache")) {
                @Override
                protected void realRun() {
                    clearTileCache(getProgressMonitor());
                }

                @Override
                protected void finish() {
                    // empty - flush is instaneus
                }

                @Override
                protected void cancel() {
                    // empty - flush is instaneus
                }

                /**
                 * Clears the tile cache.
                 *
                 * If the current tileLoader is an instance of OsmTileLoader, a new
                 * TmsTileClearController is created and passed to the according clearCache
                 * method.
                 *
                 * @param monitor not used in this implementation - as cache clear is instaneus
                 */
                private void clearTileCache(ProgressMonitor monitor) {
                    if (tileLoader instanceof CachedTileLoader) {
                        ((CachedTileLoader) tileLoader).clearCache(tileSource);
                    }
                    tileCache.clear();
                }
            }.run();
        }
    }

    private final class ShowTileInfoAction extends AbstractAction {

        private final transient Tile clickedTile;

        private ShowTileInfoAction(Tile clickedTile) {
            super(tr("Show tile info"));
            this.clickedTile = clickedTile;
        }

        private String getSizeString(int size) {
            StringBuilder ret = new StringBuilder();
            return ret.append(size).append('x').append(size).toString();
        }

        private JTextField createTextField(String text) {
            JTextField ret = new JTextField(text);
            ret.setEditable(false);
            ret.setBorder(BorderFactory.createEmptyBorder());
            return ret;
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            ExtendedDialog ed = new ExtendedDialog(Main.parent, tr("Tile Info"), new String[] { tr("OK") });
            JPanel panel = new JPanel(new GridBagLayout());
            MapViewLatLonRectangle displaySize = generateCoordinateConverter().getAreaForTile(new TilePosition(clickedTile));
            Rectangle2D bounds = displaySize.getInView().getBounds2D();
            String[][] content = { { "Tile name", clickedTile.getKey() }, { "Tile url", getUrl() },
                    { "Tile size", getSizeString(clickedTile.getTileSource().getTileSize()) },
                    { "Position in view", MessageFormat.format("x={0}..{1}, y={2}..{3}",
                            bounds.getMinX(), bounds.getMaxX(),
                            bounds.getMinY(), bounds.getMaxY())},
                    { "Position on projection",MessageFormat.format("east={0}..{1}, north={2}..{3}",
                            displaySize.getProjectionBounds().minEast, displaySize.getProjectionBounds().maxEast,
                            displaySize.getProjectionBounds().minNorth, displaySize.getProjectionBounds().maxNorth)},
                    { "Position on world",MessageFormat.format("lat={0}..{1}, lon={2}..{3}",
                            displaySize.getLatLonBoundsBox().getMinLat(), displaySize.getLatLonBoundsBox().getMaxLat(),
                            displaySize.getLatLonBoundsBox().getMinLon(), displaySize.getLatLonBoundsBox().getMaxLon())},
            };

            for (String[] entry : content) {
                panel.add(new JLabel(tr(entry[0]) + ':'), GBC.std());
                panel.add(GBC.glue(5, 0), GBC.std());
                panel.add(createTextField(entry[1]), GBC.eol().fill(GBC.HORIZONTAL));
            }

            for (Entry<String, String> e : clickedTile.getMetadata().entrySet()) {
                panel.add(new JLabel(tr("Metadata ") + tr(e.getKey()) + ':'), GBC.std());
                panel.add(GBC.glue(5, 0), GBC.std());
                String value = e.getValue();
                if ("lastModification".equals(e.getKey()) || "expirationTime".equals(e.getKey())) {
                    value = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(Long.parseLong(value)));
                }
                panel.add(createTextField(value), GBC.eol().fill(GBC.HORIZONTAL));

            }
            ed.setIcon(JOptionPane.INFORMATION_MESSAGE);
            ed.setContent(panel);
            ed.showDialog();
        }

        private String getUrl() {
            try {
                return clickedTile.getUrl();
            } catch (IOException e) {
                // silence exceptions
                Main.trace(e);
                return "";
            }
        }
    }

    private final class LoadTileAction extends AbstractAction {

        private final transient TilePosition clickedTile;

        private LoadTileAction(TilePosition clickedTile) {
            super(tr("Load tile"));
            this.clickedTile = clickedTile;
            setEnabled(clickedTile != null);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            loadTile(clickedTile, true);
            layer.invalidate();
        }
    }

    @Override
    public Bounds getBounds(TilePosition tilePos) {
        ICoordinate min = tileSource.tileXYToLatLon(tilePos.getX(), tilePos.getY(), tilePos.getZoom());
        ICoordinate max = tileSource.tileXYToLatLon(tilePos.getX() + 1, tilePos.getY() + 1, tilePos.getZoom());
        Bounds bounds = new Bounds(min.getLat(), min.getLon(), false);
        bounds.extend(max.getLat(), max.getLon());
        return bounds;
    }

    @Override
    public TileRange toRangeAtZoom(Bounds bounds, int zoom) {
        TileXY t1 = tileSource.latLonToTileXY(bounds.getMinLat(), bounds.getMinLon(), zoom);
        TileXY t2 = tileSource.latLonToTileXY(bounds.getMaxLat(), bounds.getMaxLon(), zoom);
        return new TileRange(t1, t2, zoom);
    }

    @Override
    public boolean isAvailable(TilePosition tilePos) {
        Tile tile = getTile(tilePos);
        return tile != null && !tile.hasError()
                && !(isOverzoomed(tile) && tilePos.getZoom() > zoom.getDisplayZoomLevel())
                && isImageAvailable(tile);
    }

    private boolean isImageAvailable(Tile tile) {
        BufferedImage image = tile.getImage();
        return imageLoaded(image);
    }
}