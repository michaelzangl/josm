// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.util.Comparator;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.openstreetmap.gui.jmapviewer.TileXY;

/**
 * This is a rectangular range of tiles.
 */
class TileRange {
    int minX;
    int maxX;
    int minY;
    int maxY;
    int zoom;

    TileRange() {
    }

    protected TileRange(TileXY t1, TileXY t2, int zoom) {
        minX = (int) Math.floor(Math.min(t1.getX(), t2.getX()));
        minY = (int) Math.floor(Math.min(t1.getY(), t2.getY()));
        maxX = (int) Math.ceil(Math.max(t1.getX(), t2.getX()));
        maxY = (int) Math.ceil(Math.max(t1.getY(), t2.getY()));
        this.zoom = zoom;
    }

    protected double tilesSpanned() {
        return Math.sqrt(1.0 * this.size());
    }

    protected int size() {
        int xSpan = maxX - minX + 1;
        int ySpan = maxY - minY + 1;
        return xSpan * ySpan;
    }

    /**
     * @return comparator, that sorts the tiles from the center to the edge of the current screen
     */
    private Comparator<TilePosition> getTileDistanceComparator() {
        final int centerX = (int) Math.ceil((minX + maxX) / 2d);
        final int centerY = (int) Math.ceil((minY + maxY) / 2d);
        return Comparator.comparingInt(t -> Math.abs(t.getX() - centerX) + Math.abs(t.getY() - centerY));
    }

    /**
     * Gets a stream of all tile positions in this set
     * @return A stream of all positions
     */
    public Stream<TilePosition> tilePositions() {
        if (zoom == 0) {
            return Stream.empty();
        } else {
            return IntStream.rangeClosed(minX, maxX).mapToObj(
                    x -> IntStream.rangeClosed(minY, maxY).mapToObj(y -> new TilePosition(x, y, zoom))
                    ).flatMap(Function.identity());
        }
    }

    public Stream<TilePosition> tilePositionsSorted() {
        return tilePositions().sorted(getTileDistanceComparator());
    }
}