// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;

import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.AbstractTileSourceLayer;

/**
 * This class manages the zoom level of a {@link TileSourcePainter}
 * @author Michael Zangl
 * @since xxx
 */
public class ZoomLevelManager {
    /**
     * Zoomlevel at which tiles is currently downloaded.
     * Initial zoom lvl is set to bestZoom
     */
    private int currentZoomLevel;
    private int displayZoomLevel;
    private TileSourceDisplaySettings settings;
    private TileSource source;

    private int minZoom;
    private int maxZoom;

    /**
     * Create a new zoom level manager
     * @param settings The zoom settings
     * @param source The tile source to use.
     * @param initialZoomState The initial state to compute the zoom factor from.
     */
    public ZoomLevelManager(TileSourceDisplaySettings settings, TileSource source, TileCoordinateConverter initialZoomState) {
        this.settings = settings;
        this.source = source;

        setZoomLevel(clampZoom(initialZoomState.getBestZoom()));
        setZoomBounds(source.getMinZoom(), source.getMaxZoom());
    }

    /**
     * Set the zoom bounds
     * @param bounds An info to get the zoom bounds from
     */
    public void setZoomBounds(ImageryInfo bounds) {
        setZoomBounds(bounds.getMinZoom(), bounds.getMaxZoom());
    }

    /**
     * Sets the zoom bounds
     * @param minZoom The minimum zoom
     * @param maxZoom The maximum zoom.
     */
    public void setZoomBounds(int minZoom, int maxZoom) {
        if (minZoom > maxZoom || minZoom < 0) {
            throw new IllegalArgumentException(MessageFormat.format("Zoom range not valid: {0}..{1}", minZoom, maxZoom));
        }
        this.minZoom = minZoom;
        this.maxZoom = maxZoom;
    }

    /**
     * @return The min zoom that was set.
     */
    public int getMinZoom() {
        return minZoom;
    }

    /**
     * @return The max zoom that was set.
     */
    public int getMaxZoom() {
        return maxZoom;
    }

    protected int getMaxZoomLvl() {
        if (getMaxZoom() != 0)
            return AbstractTileSourceLayer.checkMaxZoomLvl(getMaxZoom(), source);
        else
            return AbstractTileSourceLayer.getMaxZoomLvl(source);
    }

    protected int getMinZoomLvl() {
        if (getMinZoom() != 0)
            return AbstractTileSourceLayer.checkMinZoomLvl(getMinZoom(), source);
        else
            return AbstractTileSourceLayer.getMinZoomLvl(source);
    }

    /**
     *
     * @return if its allowed to zoom in
     */
    public boolean zoomIncreaseAllowed() {
        boolean zia = currentZoomLevel < this.getMaxZoomLvl();
        if (Main.isDebugEnabled()) {
            Main.debug("zoomIncreaseAllowed(): " + zia + ' ' + currentZoomLevel + " vs. " + this.getMaxZoomLvl());
        }
        return zia;
    }

    /**
     * Zoom in, go closer to map.
     *
     * @return    true, if zoom increasing was successful, false otherwise
     */
    public boolean increaseZoomLevel() {
        return setZoomLevel(currentZoomLevel + 1);
    }

    /**
     * Sets the zoom level of the layer
     * @param zoom zoom level
     * @return true, when zoom has changed to desired value, false if it was outside supported zoom levels
     */
    public boolean setZoomLevel(int zoom) {
        if (zoom == currentZoomLevel) {
            return true;
        } else if (zoom < getMinZoomLvl() || zoom > getMaxZoomLvl()) {
            Main.warn("Current zoom level ({0}) could not be changed to {1}: out of range {2} .. {3}", currentZoomLevel,
                    zoom, getMinZoomLvl(), getMaxZoomLvl());
            return false;
        } else {
            Main.debug("changing zoom level to: {0}", currentZoomLevel);
            currentZoomLevel = zoom;
            displayZoomLevel = zoom;
//            zoomChanged();
            return true;
        }
    }

    /**
     * Check if zooming out is allowed
     *
     * @return    true, if zooming out is allowed (currentZoomLevel &gt; minZoomLevel)
     */
    public boolean zoomDecreaseAllowed() {
        boolean zda = currentZoomLevel > this.getMinZoomLvl();
        if (Main.isDebugEnabled()) {
            Main.debug("zoomDecreaseAllowed(): " + zda + ' ' + currentZoomLevel + " vs. " + this.getMinZoomLvl());
        }
        return zda;
    }

    /**
     * Zoom out from map.
     *
     * @return    true, if zoom increasing was successfull, false othervise
     */
    public boolean decreaseZoomLevel() {
        return setZoomLevel(currentZoomLevel - 1);
    }

    public void updateZoomLevel(TileCoordinateConverter currentZoomState) {
        int zoom = currentZoomLevel;
        if (settings.isAutoZoom()) {
            zoom = clampZoom(currentZoomState.getBestZoom());

            if (settings.isAutoLoad()) {
//                // Auto-detection of tilesource maxzoom (currently fully works only for Bing)
//                TileSetInfo tsi = dts.getTileSetInfo(zoom);
//                if (!tsi.hasVisibleTiles && (!tsi.hasLoadingTiles || tsi.hasOverzoomedTiles)) {
//                    noTilesAtZoom = true;
//                }
//                // Find highest zoom level with at least one visible tile
//                for (int tmpZoom = zoom; tmpZoom > dts.minZoom; tmpZoom--) {
//                    if (dts.getTileSetInfo(tmpZoom).hasVisibleTiles) {
//                        displayZoomLevel = tmpZoom;
//                        break;
//                    }
//                }
//                // Do binary search between currentZoomLevel and displayZoomLevel
//                while (zoom > displayZoomLevel && !tsi.hasVisibleTiles && tsi.hasOverzoomedTiles) {
//                    zoom = (zoom + displayZoomLevel) / 2;
//                    tsi = dts.getTileSetInfo(zoom);
//                }
//
//                setZoomLevel(zoom);
//
//                // If all tiles at displayZoomLevel is loaded, load all tiles at next zoom level
//                // to make sure there're really no more zoom levels
//                // loading is done in the next if section
//                if (zoom == displayZoomLevel && !tsi.hasLoadingTiles && zoom < dts.maxZoom) {
//                    zoom++;
//                    tsi = dts.getTileSetInfo(zoom);
//                }
//                // When we have overzoomed tiles and all tiles at current zoomlevel is loaded,
//                // load tiles at previovus zoomlevels until we have all tiles on screen is loaded.
//                // loading is done in the next if section
//                while (zoom > dts.minZoom && tsi.hasOverzoomedTiles && !tsi.hasLoadingTiles) {
//                    zoom--;
//                    tsi = dts.getTileSetInfo(zoom);
//                }
//                ts = dts.getTileSet(zoom);
            } else {
                setZoomLevel(zoom);
            }
        }
    }

    private int clampZoom(int intResult) {
        intResult = Math.min(intResult, getMaxZoomLvl());
        intResult = Math.max(intResult, getMinZoomLvl());
        return intResult;
    }

    public int getCurrentZoomLevel() {
        return currentZoomLevel;
    }

    public int getDisplayZoomLevel() {
        return displayZoomLevel;
    }

    public List<String> getDebugInfo(TileCoordinateConverter currentZoomState) {
        int bestZoom = currentZoomState.getBestZoom();
        return Arrays.asList(
                tr("Current zoom: {0}", getCurrentZoomLevel()),
                tr("Display zoom: {0}", displayZoomLevel),
                tr("Pixel scale: {0}", currentZoomState.getScaleFactor(getCurrentZoomLevel())),
                tr("Best zoom: {0} (clamped to: {1})", bestZoom, clampZoom(bestZoom))
                );
    }

    public class ZoomToNativeLevelAction extends AbstractAction {
        private final MapView forView;

        public ZoomToNativeLevelAction(MapView forView) {
            super(tr("Zoom to native resolution"));
            this.forView = forView;
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            TileCoordinateConverter converter = new TileCoordinateConverter(forView.getState(), source, settings);
            double newFactor = Math.sqrt(converter.getScaleFactor(currentZoomLevel));
            forView.zoomToFactor(newFactor);
        }
    }

    public class ZoomToBestAction extends AbstractAction {
        private final int bestZoom;

        public ZoomToBestAction(MapView forView) {
            super(tr("Change resolution"));
            bestZoom = clampZoom(new TileCoordinateConverter(forView.getState(), source, settings).getBestZoom());
            setEnabled(!settings.isAutoZoom() && bestZoom != currentZoomLevel);
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            setZoomLevel(bestZoom);
        }
    }

    public class IncreaseZoomAction extends AbstractAction {
        public IncreaseZoomAction() {
            super(tr("Increase zoom"));
            setEnabled(!settings.isAutoZoom() && zoomIncreaseAllowed());
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            increaseZoomLevel();
        }
    }

    public class DecreaseZoomAction extends AbstractAction {
        public DecreaseZoomAction() {
            super(tr("Decrease zoom"));
            setEnabled(!settings.isAutoZoom() && zoomDecreaseAllowed());
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            decreaseZoomLevel();
        }
    }

}
