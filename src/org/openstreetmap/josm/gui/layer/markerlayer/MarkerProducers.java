// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.markerlayer;

import java.io.File;
import java.util.Collection;

import org.openstreetmap.josm.data.gpx.WayPoint;

/**
 * This interface has to be implemented by anyone who wants to create markers.
 *
 * When reading a gpx file, all implementations of MarkerMaker registered with
 * the Marker are consecutively called until one returns a Marker object.
 *
 * @author Frederik Ramm
 */
public interface MarkerProducers {
    /**
     * Returns a collection of Marker objects if this implementation wants to create one for the
     * given input data, or <code>null</code> otherwise.
     *
     * @param wp waypoint data
     * @param relativePath An path to use for constructing relative URLs or
     *        <code>null</code> for no relative URLs
     * @return A collection of Marker objects, or <code>null</code>.
     */
    Collection<Marker> createMarkers(WayPoint wp, File relativePath, MarkerLayer parentLayer, double time, double offset);
}
