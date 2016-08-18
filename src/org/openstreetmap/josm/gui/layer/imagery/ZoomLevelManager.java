// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;

import javax.swing.AbstractAction;

import org.openstreetmap.gui.jmapviewer.Tile;
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
     * Zoomlevel selected by the user.
     */
    private int currentZoomLevel;
    /**
     * The zoom level at which tiles are currently displayed
     */
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
        this.minZoom = AbstractTileSourceLayer.checkMinZoomLvl(minZoom, source);
        this.maxZoom = AbstractTileSourceLayer.checkMaxZoomLvl(maxZoom, source);
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

    /**
     *
     * @return if its allowed to zoom in
     */
    public boolean zoomIncreaseAllowed() {
        boolean zia = currentZoomLevel < this.getMaxZoom();
        if (Main.isDebugEnabled()) {
            Main.debug("zoomIncreaseAllowed(): " + zia + ' ' + currentZoomLevel + " vs. " + this.getMaxZoom());
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
        if (zoom == currentZoomLevel && zoom == displayZoomLevel) {
            return true;
        } else if (zoom < getMinZoom() || zoom > getMaxZoom()) {
            Main.warn("Current zoom level ({0}) could not be changed to {1}: out of range {2} .. {3}", currentZoomLevel,
                    zoom, getMinZoom(), getMaxZoom());
            return false;
        } else {
            Main.debug("changing zoom level to: {0}", currentZoomLevel);
            currentZoomLevel = zoom;
            displayZoomLevel = zoom;
            return true;
        }
    }

    /**
     * Check if zooming out is allowed
     *
     * @return    true, if zooming out is allowed (currentZoomLevel &gt; minZoomLevel)
     */
    public boolean zoomDecreaseAllowed() {
        boolean zda = currentZoomLevel > this.getMinZoom();
        if (Main.isDebugEnabled()) {
            Main.debug("zoomDecreaseAllowed(): " + zda + ' ' + currentZoomLevel + " vs. " + this.getMinZoom());
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

    /**
     * Update the zoom level that should be displayed
     * @param currentZoomState The coordinate converter that holds the current view
     * @param viewStatus An accessor for finding out if the given tiles are available.
     */
    public void updateZoomLevel(TileCoordinateConverter currentZoomState, TileSourcePainter<?> viewStatus) {
        if (settings.isAutoZoom()) {
            int zoom = clampZoom(currentZoomState.getBestZoom());
            setZoomLevel(zoom);
            if (settings.isAutoLoad()) {
                // Find highest zoom level with at least one visible tile

                for (int tmpZoom = zoom; tmpZoom >= getMinZoom(); tmpZoom--) {
                    TileRange area = currentZoomState.getViewAtZoom(zoom);
                    if (viewStatus.hasTiles(area, ZoomLevelManager::visibleOrOverzoomed)) {
                        displayZoomLevel = tmpZoom;
                        break;
                    }
                }
            }
        } else {
            displayZoomLevel = currentZoomLevel;
        }
    }

    private static boolean visibleOrOverzoomed(Tile t) {
        return TileSourcePainter.isVisible(t) || TileSourcePainter.isOverzoomed(t);
    }

    private int clampZoom(int intResult) {
        return Math.max(Math.min(intResult, getMaxZoom()), getMinZoom());
    }

    /**
     * Gets the current zoom level that is requested by the user for displaying the tiles.
     * @return The current zoom level of the view
     */
    public int getCurrentZoomLevel() {
        return currentZoomLevel;
    }

    /**
     * Gets the zoom level that is suggested to be displayed. This may be different depending on the tile loading settings.
     * @return The suggested zoom.
     */
    public int getDisplayZoomLevel() {
        return displayZoomLevel;
    }

    /**
     * Gets the debug information that should be added about the zoom level.
     * @param currentZoomState A coordinate converter
     * @return The current zoom status.
     */
    public List<String> getDebugInfo(TileCoordinateConverter currentZoomState) {
        int bestZoom = currentZoomState.getBestZoom();
        return Arrays.asList(
                tr("Current zoom: {0}", getCurrentZoomLevel()),
                tr("Display zoom: {0}", displayZoomLevel),
                tr("Pixel scale: {0}", currentZoomState.getScaleFactor(getCurrentZoomLevel())),
                tr("Best zoom: {0} (clamped to: {1})", bestZoom, clampZoom(bestZoom))
                );
    }

    /**
     * Zooms to the native level of the current view
     */
    public class ZoomToNativeLevelAction extends AbstractAction {
        private final MapView forView;

        /**
         * Create a new {@link ZoomToNativeLevelAction}
         * @param forView The map view to zoom
         */
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

    /**
     * Zooms the layer to the best display zoom for the current map view state
     */
    public class ZoomToBestAction extends AbstractAction {
        private final int bestZoom;

        /**
         * Create a new {@link ZoomToBestAction}
         * @param forView The view to use as reference.
         */
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

    /**
     * Increase the zoom by 1.
     */
    public class IncreaseZoomAction extends AbstractAction {
        /**
         * Create a new {@link IncreaseZoomAction}
         */
        public IncreaseZoomAction() {
            super(tr("Increase zoom"));
            setEnabled(!settings.isAutoZoom() && zoomIncreaseAllowed());
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            increaseZoomLevel();
        }
    }

    /**
     * Decrease the zoom by 1.
     */
    public class DecreaseZoomAction extends AbstractAction {
        /**
         * Create a new {@link DecreaseZoomAction}
         */
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
