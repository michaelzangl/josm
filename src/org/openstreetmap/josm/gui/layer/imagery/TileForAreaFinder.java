// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;

/**
 * This class helps finding loaded tiles for a tile area.
 * @author Michael Zangl
 * @since xxx
 */
public final class TileForAreaFinder {

    private TileForAreaFinder() {
        // hidden
    }

    /**
     * Get a stream of all tile positions to paint for the given zoom level.
     * @param initialRange The range
     * @param rangeProducer An object that converts between {@link Bounds} and {@link TilePosition}
     * @return A stream of tiles to paint.
     */
    public static Stream<TilePosition> getAtDefaultZoom(TileRange initialRange, TileForAreaGetter rangeProducer) {
        return initialRange.tilePositions().filter(rangeProducer::isAvailable);
    }

    /**
     * Gets a stream of all positions to be painted taking the fallback zoom levels into account.
     * <p>
     * Limiting the resulting stream won't change the performance of this method. It returns a stream so that this may be changed in the future.
     *
     * @param initialRange The range
     * @param rangeProducer An object that converts between {@link Bounds} and {@link TilePosition}
     * @param zoom The zoom levels to try at.
     * @return A stream of tiles to paint.
     */
    public static Stream<TilePosition> getWithFallbackZoom(TileRange initialRange, TileForAreaGetter rangeProducer, ZoomLevelManager zoom) {
        ArrayList<TilePosition> list = new ArrayList<>();
        List<List<Bounds>> missedInPreviousRuns = new ArrayList<>();
        List<Bounds> missed = initialRange.tilePositions().flatMap(pos -> addPosition(pos, rangeProducer, list)).collect(Collectors.toList());
        List<Bounds> missedInLastRun = missed;

        for (int delta : new int[] { -1, 1, -2, 2, -3, -4, -5 }) {
            int zoomLevel = delta + initialRange.getZoom();
            if (zoomLevel >= zoom.getMinZoom() && zoomLevel <= zoom.getMaxZoom()) {
                missed = missedInLastRun
                    .stream()
                    .flatMap(b -> rangeProducer.toRangeAtZoom(b, zoomLevel).tilePositions())
                    .distinct()
                    .filter(tile -> missedInPreviousRuns.stream().allMatch(l -> l.stream().anyMatch(rangeProducer.getBounds(tile)::intersects)))
                    .flatMap(pos -> addPosition(pos, rangeProducer, list))
                    .collect(Collectors.toList());
                Main.trace("Still missed {0} tile areas at zoom {1}.", missed.size(), zoomLevel);
                if (missed.isEmpty()) {
                    break;
                }

                missedInPreviousRuns.add(missedInLastRun);
                missedInLastRun = missed;
            }
            // no break condition. But missed will be empty, so flatMap should not be costy.
        }

        Collections.reverse(list);
        return list.stream().distinct();
    }

    private static Stream<Bounds> addPosition(TilePosition pos, TileForAreaGetter rangeProducer, ArrayList<TilePosition> addTo) {
        if (rangeProducer.isAvailable(pos)) {
            addTo.add(pos);
            return Stream.empty();
        } else {
            return Stream.of(rangeProducer.getBounds(pos));
        }
    }

    /**
     * Classes implementing this interface allow us to convert between a tile range and {@link Bounds}.
     * @author Michael Zangl
     * @since xxx
     */
    public interface TileForAreaGetter {
        /**
         * Gets a tile range that is enclosing this tile at the given zoom level.
         * @param bounds The bounds to get the range for
         * @param zoom The zoom the range should be at
         * @return The range for the given bounds.
         */
        public TileRange toRangeAtZoom(Bounds bounds, int zoom);

        /**
         * Gets the bounds for a tile
         * @param tile The tile
         * @return The bounds for that tile
         */
        public Bounds getBounds(TilePosition tile);

        /**
         * Checks if an image is available for the given tile
         * @param tile The tile to check
         * @return True if it is available.
         */
        public boolean isAvailable(TilePosition tile);

    }

}
