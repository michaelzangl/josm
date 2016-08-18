// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.util.function.Predicate;

import javax.swing.AbstractAction;

import org.openstreetmap.gui.jmapviewer.AttributionSupport;
import org.openstreetmap.gui.jmapviewer.MemoryTileCache;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.interfaces.CachedTileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileCache;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.PleaseWaitRunnable;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;
import org.openstreetmap.josm.gui.layer.imagery.TileForAreaFinder.TileForAreaGetter;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.MemoryManager;
import org.openstreetmap.josm.tools.MemoryManager.MemoryHandle;
import org.openstreetmap.josm.tools.MemoryManager.NotEnoughMemoryException;

/**
 * This class backs the {@link TileSourcePainter} by handling the loading / acces of the tile images
 * @author Michael Zangl
 * @param <T> The imagery type to use
 * @since xxx
 */
public abstract class AbstractTileSourceLoader<T extends AbstractTMSTileSource> implements TileForAreaGetter, ZoomChangeListener {

    /*
     *  use MemoryTileCache instead of tileLoader JCS cache, as tileLoader caches only content (byte[] of image)
     *  and MemoryTileCache caches whole Tile. This gives huge performance improvement when a lot of tiles are visible
     *  in MapView (for example - when limiting min zoom in imagery)
     *
     *  Use per-layer tileCache instance, as the more layers there are, the more tiles needs to be cached
     */
    private final TileCache tileCache; // initialized together with tileSource
    protected final T tileSource;
    protected final TileLoader tileLoader;
    protected final AttributionSupport attribution = new AttributionSupport();

    /**
     * The memory handle that will hold our tile source.
     */
    private MemoryHandle<?> memory;

    protected AbstractTileSourceLoader(AbstractTileSourceLayer<T> layer) {
        tileSource = generateTileSource(layer);
        if (tileSource == null) {
            throw new IllegalArgumentException(tr("Failed to create tile source"));
        }

        attribution.initialize(tileSource);

        tileLoader = layer.generateTileLoader(tileSource);

        tileCache = new MemoryTileCache(estimateTileCacheSize());
        MapView.addZoomChangeListener(this);
    }

    protected T generateTileSource(AbstractTileSourceLayer<T> layer) {
        return layer.getTileSource();
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

    protected Tile getOrCreateTile(TilePosition tilePosition) {
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
    protected Tile getTile(TilePosition tilePosition) {
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

    protected void loadTiles(TileRange range, boolean force) {
        if (force) {
            if (isTooLarge(range)) {
                Main.warn("Not downloading all tiles because there are too many tiles on an axis!");
            } else {
                range.tilePositionsSorted().filter(this::contains).forEach(t -> loadTile(t, force));
            }
        }
    }

    protected static boolean isTooSmall(TileRange range) {
        return range.tilesSpanned() < 2;
    }

    protected boolean isTooLarge(TileRange range) {
        return range.size() > tileCache.getCacheSize() || range.tilesSpanned() > 20;
    }

    protected boolean loadTile(TilePosition tile, boolean force) {
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

    /**
     * Reserve the memory for the cache
     * @return <code>true</code> if it is reserved.
     */
    protected boolean allocateCacheMemory() {
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
        return memory != null;
    }

    /**
     * Free the cache memeory
     */
    protected void freeCacheMemory() {
        if (memory != null) {
            memory.free();
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


    protected class FlushTileCacheAction extends AbstractAction {
        FlushTileCacheAction() {
            super(tr("Flush tile cache"));
            setEnabled(tileLoader instanceof CachedTileLoader);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            new PleaseWaitRunnable(tr("Flush tile cache")) {
                @Override
                protected void realRun() {
                    clearTileCache();
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
                 */
                private void clearTileCache() {
                    if (tileLoader instanceof CachedTileLoader) {
                        ((CachedTileLoader) tileLoader).clearCache(tileSource);
                    }
                    tileCache.clear();
                }
            }.run();
        }
    }
}
