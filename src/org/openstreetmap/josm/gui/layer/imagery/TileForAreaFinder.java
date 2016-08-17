// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.util.ArrayList;
import java.util.stream.Stream;

/**
 * This class helps finding loaded tiles for a tile area.
 * @author Michael Zangl
 * @since xxx
 */
public class TileForAreaFinder {
    private final TileRange area;
    private Stream<TilePosition> tiles;

    public TileForAreaFinder(TileRange area) {
        this.area = area;

        tiles = area.tilePositions();
    }

    public Stream<TilePosition> getAllTiles() {
        // prepared to be lazy
        ArrayList<TilePosition> list = new ArrayList<>();
        area.tilePositions().filter(this::isAvailable).forEach(list::add);
        return list.stream();
    }

    private boolean isAvailable(TilePosition position) {
        return true;
    }

}
