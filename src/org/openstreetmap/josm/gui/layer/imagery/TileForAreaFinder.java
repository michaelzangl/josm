// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.util.ArrayList;
import java.util.Collections;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.Bounds;

/**
 * This class helps finding loaded tiles for a tile area.
 * @author Michael Zangl
 * @since xxx
 */
public class TileForAreaFinder implements Supplier<Stream<TilePosition>>{
    protected final TileRange initialRange;
    protected final TileAreaProducer rangeProducer;

    public TileForAreaFinder(TileRange initialRange, TileAreaProducer rangeProducer) {
        this.initialRange = initialRange;
        this.rangeProducer = rangeProducer;
    }

    @Override
    public Stream<TilePosition> get() {
        return initialRange.tilePositions().filter(rangeProducer::isAvailable);
    }

    public static class TileForAreaWithFallback extends TileForAreaFinder {
        protected final ZoomLevelManager zoom;

        public TileForAreaWithFallback(TileRange initialRange, TileAreaProducer rangeProducer, ZoomLevelManager zoom) {
            super(initialRange, rangeProducer);
            this.zoom = zoom;
        }

        @Override
        public Stream<TilePosition> get() {
            ArrayList<TilePosition> list = new ArrayList<>();
            ArrayList<Bounds> missedOnFirstRun = new ArrayList<>();
            initialRange.tilePositions().forEach(pos -> addPosition(pos, list, missedOnFirstRun));
            ArrayList<Bounds> lastMissed = missedOnFirstRun;

            for (int delta : new int[] { -1, 1, -2, 2, -3, -4, -5 }) {
                int zoomLevel = delta + initialRange.getZoom();
                if (zoomLevel < zoom.getMinZoom() || zoomLevel > zoom.getMaxZoom()) {
                    ArrayList<Bounds> newlyMissed = new ArrayList<>();
                    lastMissed.stream().flatMap(b -> rangeProducer.toRangeAtZoom(b, zoomLevel).tilePositions())
                            .forEach(pos -> addPosition(pos, list, newlyMissed));
                    lastMissed = newlyMissed;
                }
            }

            Collections.reverse(list);
            return list.stream();
        }

        private void addPosition(TilePosition pos, ArrayList<TilePosition> addTo, ArrayList<Bounds> missed) {
            if (rangeProducer.isAvailable(pos)) {
                addTo.add(pos);
            } else {
                missed.add(rangeProducer.getBounds(pos));
            }
        }

    }

    public interface TileAreaProducer {
        /**
         * Gets a tile range that is enclosing this tile at the given zoom level.
         * @param tile
         * @param zoom
         * @return
         */
        public TileRange toRangeAtZoom(Bounds bounds, int zoom);

        public Bounds getBounds(TilePosition tile);

        public boolean isAvailable(TilePosition tile);

    }

}
