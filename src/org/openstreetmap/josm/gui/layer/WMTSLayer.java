// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import java.io.IOException;

import org.apache.commons.jcs.access.CacheAccess;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.cache.BufferedImageCacheEntry;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.ImageryInfo.ImageryType;
import org.openstreetmap.josm.data.imagery.WMSCachedTileLoader;
import org.openstreetmap.josm.data.imagery.WMTSTileSource;
import org.openstreetmap.josm.data.projection.Projection;
import org.openstreetmap.josm.data.projection.ProjectionChangeListener;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings;
import org.openstreetmap.josm.gui.layer.imagery.TileSourcePainter;

/**
 * WMTS layer based on AbstractTileSourceLayer. Overrides few methods to align WMTS to Tile based computations
 * but most magic is done within WMTSTileSource class.
 *
 * Full specification of the protocol available at:
 * http://www.opengeospatial.org/standards/wmts
 *
 * @author Wiktor NiesiobÄ™dzki
 *
 */
public class WMTSLayer extends AbstractCachedTileSourceLayer<WMTSTileSource> {
    private static final String PREFERENCE_PREFIX = "imagery.wmts";

    /**
     * Registers all setting properties
     */
    static {
        new TileSourceDisplaySettings(PREFERENCE_PREFIX);
    }

    private static final String CACHE_REGION_NAME = "WMTS";

    /**
     * Creates WMTS layer from ImageryInfo
     * @param info Imagery Info describing the layer
     */
    public WMTSLayer(ImageryInfo info) {
        super(info);
    }

    @Override
    protected TileSourceDisplaySettings createDisplaySettings() {
        return new TileSourceDisplaySettings(PREFERENCE_PREFIX);
    }

    //  TODO  @Override
    //    protected int getBestZoom() {
    //        if (!Main.isDisplayingMapView())
    //            return 0;
    //        ScaleList scaleList = getNativeScales();
    //        if (scaleList == null) {
    //            return getMaxZoomLvl();
    //        }
    //        Scale snap = scaleList.getSnapScale(Main.map.mapView.getScale(), false);
    //        return Math.max(
    //                getMinZoomLvl(),
    //                Math.min(
    //                        snap != null ? snap.getIndex() : getMaxZoomLvl(),
    //                        getMaxZoomLvl()
    //                        )
    //                );
    //    }

    //
    //    @Override
    //    public String nameSupportedProjections() {
    //        StringBuilder ret = new StringBuilder();
    //        for (String e: tileSource.getSupportedProjections()) {
    //            ret.append(e).append(", ");
    //        }
    //        return ret.length() > 2 ? ret.substring(0, ret.length()-2) : ret.toString();
    //    }

    @Override
    protected Class<? extends TileLoader> getTileLoaderClass() {
        return WMSCachedTileLoader.class;
    }

    @Override
    protected String getCacheName() {
        return CACHE_REGION_NAME;
    }

    /**
     * @return cache region for WMTS layer
     */
    public static CacheAccess<String, BufferedImageCacheEntry> getCache() {
        return AbstractCachedTileSourceLayer.getCache(CACHE_REGION_NAME);
    }

    //  TODO  @Override
    //    public ScaleList getNativeScales() {
    //        return tileSource.getNativeScales();
    //    }

    @Override
    protected TileSourcePainter<WMTSTileSource> createMapViewPainter(MapViewEvent event) {
        return new WMTSPainter(this, event.getMapView());
    }

    private static class WMTSPainter extends TileSourcePainter<WMTSTileSource> {
        private final ProjectionChangeListener initOnProjectionChange = (oldValue, newValue) -> tileSource
                .initProjection(newValue);

        public WMTSPainter(AbstractTileSourceLayer<WMTSTileSource> abstractTileSourceLayer, MapView mapView) {
            super(abstractTileSourceLayer, mapView);
            Main.addProjectionChangeListener(initOnProjectionChange);

            zoom.setZoomBounds(0, zoom.getMaxZoom());
        }

        @Override
        protected WMTSTileSource generateTileSource(AbstractTileSourceLayer<WMTSTileSource> layer) {
            try {
                ImageryInfo layerInfo = layer.getInfo();
                if (layerInfo.getImageryType() == ImageryType.WMTS && layerInfo.getUrl() != null) {
                    WMTSTileSource.checkUrl(layerInfo.getUrl());
                    WMTSTileSource tileSource = new WMTSTileSource(layerInfo);
                    layerInfo.setAttribution(tileSource);
                    return tileSource;
                }
                return null;
            } catch (IOException e) {
                Main.warn(e);
                throw new IllegalArgumentException(e);
            }
        }

        @Override
        public void detachFromMapView(MapViewEvent event) {
            Main.removeProjectionChangeListener(initOnProjectionChange);
            super.detachFromMapView(event);
        }

        @Override
        public boolean isProjectionSupported(Projection proj) {
            return tileSource.getSupportedProjections().contains(proj.toCode());
        }
    }
}
