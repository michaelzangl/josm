// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.commons.jcs.access.CacheAccess;
import org.openstreetmap.gui.jmapviewer.OsmMercator;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.ScanexTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TMSTileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.TemplatedTMSTileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.imagery.CachedAttributionBingAerialTileSource;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.gui.layer.NativeScaleLayer.ScaleList;

/**
 * Class that displays a slippy map layer.
 *
 * @author Frederik Ramm
 * @author LuVar &lt;lubomir.varga@freemap.sk&gt;
 * @author Dave Hansen &lt;dave@sr71.net&gt;
 * @author Upliner &lt;upliner@gmail.com&gt;
 * @since 3715
 */
public class TMSLayer extends AbstractCachedTileSourceLayer<TMSTileSource> {
    private static final String CACHE_REGION_NAME = "TMS";

    private static final String PREFERENCE_PREFIX = "imagery.tms";

    /** minimum zoom level for TMS layer */
    public static final IntegerProperty PROP_MIN_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".min_zoom_lvl",
            AbstractTileSourceLayer.PROP_MIN_ZOOM_LVL.get());
    /** maximum zoom level for TMS layer */
    public static final IntegerProperty PROP_MAX_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".max_zoom_lvl",
            AbstractTileSourceLayer.PROP_MAX_ZOOM_LVL.get());
    /** shall TMS layers be added to download dialog */
    public static final BooleanProperty PROP_ADD_TO_SLIPPYMAP_CHOOSER = new BooleanProperty(PREFERENCE_PREFIX + ".add_to_slippymap_chooser",
            true);

    private static final ScaleList nativeScaleList = initNativeScaleList();

    /**
     * Create a layer based on ImageryInfo
     * @param info description of the layer
     */
    public TMSLayer(ImageryInfo info) {
        super(info);
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the passed ImageryInfo object.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     *
     * @param info imagery info
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    @Override
    protected TMSTileSource getTileSource(ImageryInfo info) {
        return getTileSourceStatic(info, () -> {
            Main.debug("Attribution loaded, running loadAllErrorTiles");
//       TODO     this.loadAllErrorTiles(false);
        });
    }

    @Override
    public final boolean isProjectionSupported(Projection proj) {
        return "EPSG:3857".equals(proj.toCode()) || "EPSG:4326".equals(proj.toCode());
    }

    @Override
    public final String nameSupportedProjections() {
        return tr("EPSG:4326 and Mercator projection are supported");
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the passed ImageryInfo object.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     * @param info imagery info
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    public static AbstractTMSTileSource getTileSourceStatic(ImageryInfo info) {
        return getTileSourceStatic(info, null);
    }

    /**
     * Creates and returns a new TileSource instance depending on the {@link ImageryType}
     * of the passed ImageryInfo object.
     *
     * If no appropriate TileSource is found, null is returned.
     * Currently supported ImageryType are {@link ImageryType#TMS},
     * {@link ImageryType#BING}, {@link ImageryType#SCANEX}.
     *
     * @param info imagery info
     * @param attributionLoadedTask task to be run once attribution is loaded, might be null, if nothing special shall happen
     * @return a new TileSource instance or null if no TileSource for the ImageryInfo/ImageryType could be found.
     * @throws IllegalArgumentException if url from imagery info is null or invalid
     */
    public static TMSTileSource getTileSourceStatic(ImageryInfo info, Runnable attributionLoadedTask) {
        if (info.getImageryType() == ImageryType.TMS) {
            TemplatedTMSTileSource.checkUrl(info.getUrl());
            TMSTileSource t = new TemplatedTMSTileSource(info);
            info.setAttribution(t);
            return t;
        } else if (info.getImageryType() == ImageryType.BING) {
            return new CachedAttributionBingAerialTileSource(info, attributionLoadedTask);
        } else if (info.getImageryType() == ImageryType.SCANEX) {
            return new ScanexTileSource(info);
        }
        return null;
    }

    @Override
    protected Class<? extends TileLoader> getTileLoaderClass() {
        return TMSCachedTileLoader.class;
    }

    @Override
    protected String getCacheName() {
        return CACHE_REGION_NAME;
    }

    /**
     * @return cache for TMS region
     */
    public static CacheAccess<String, BufferedImageCacheEntry> getCache() {
        return AbstractCachedTileSourceLayer.getCache(CACHE_REGION_NAME);
    }

//  TODO  @Override
//    public ScaleList getNativeScales() {
//        return nativeScaleList;
//    }

    private static ScaleList initNativeScaleList() {
        Collection<Double> scales = new ArrayList<>(AbstractTileSourceLayer.MAX_ZOOM);
        for (int zoom = AbstractTileSourceLayer.MIN_ZOOM; zoom <= AbstractTileSourceLayer.MAX_ZOOM; zoom++) {
            double scale = OsmMercator.EARTH_RADIUS * Math.PI * 2 / Math.pow(2, zoom) / OsmMercator.DEFAUL_TILE_SIZE;
            scales.add(scale);
        }
        return new ScaleList(scales);
    }
 }
