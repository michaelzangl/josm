// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.projection.datum;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Ellipsoid;

/**
 * A datum with different ellipsoid than WGS84, but does not require
 * shift, rotation or scaling.
 */
public class CentricDatum extends AbstractDatum {

    public CentricDatum(String name, String proj4Id, Ellipsoid ellps) {
        super(name, proj4Id, ellps);
    }

    @Override
    public LatLon toWGS84(ILatLon ll) {
        return Ellipsoid.WGS84.cart2LatLon(ellps.latLon2Cart(ll));
    }

    @Override
    public ILatLon fromWGS84(ILatLon ll) {
        return this.ellps.cart2LatLon(Ellipsoid.WGS84.latLon2Cart(ll));
    }

    @Override
    public String toString() {
        return "CentricDatum{ellipsoid="+ellps+'}';
    }
}
