// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.projection.datum;

import org.openstreetmap.josm.data.coor.ILatLon;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.projection.Ellipsoid;

/**
 * Datum provides 3 dimensional offset and ellipsoid conversion.
 */
public class ThreeParameterDatum extends AbstractDatum {

    protected double dx, dy, dz;

    public ThreeParameterDatum(String name, String proj4Id, Ellipsoid ellps, double dx, double dy, double dz) {
        super(name, proj4Id, ellps);
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    @Override
    public LatLon toWGS84(ILatLon ll) {
        double[] xyz = ellps.latLon2Cart(ll);
        xyz[0] += dx;
        xyz[1] += dy;
        xyz[2] += dz;
        return Ellipsoid.WGS84.cart2LatLon(xyz);
    }

    @Override
    public ILatLon fromWGS84(ILatLon ll) {
        double[] xyz = Ellipsoid.WGS84.latLon2Cart(ll);
        xyz[0] -= dx;
        xyz[1] -= dy;
        xyz[2] -= dz;
        return this.ellps.cart2LatLon(xyz);
    }

}
