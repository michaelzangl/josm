// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io.remotecontrol.handler;

import static org.junit.Assert.assertEquals;

import java.io.File;

import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.TestUtils;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.io.remotecontrol.handler.RequestHandler.RequestHandlerBadRequestException;
import org.openstreetmap.josm.tools.Utils;

/**
 * Unit tests of {@link ImportHandler} class.
 */
public class ImportHandlerTest {

    /**
     * Rule used for tests throwing exceptions.
     */
    @Rule
    public ExpectedException thrown = ExpectedException.none();

    /**
     * Setup test.
     */
    @BeforeClass
    public static void setUpBeforeClass() {
        JOSMFixture.createUnitTestFixture().init(true);
    }

    private static ImportHandler newHandler(String url) {
        ImportHandler req = new ImportHandler();
        if (url != null)
            req.setUrl(url);
        return req;
    }

    /**
     * Non-regression test for bug #7434.
     */
    @Test
    public void testTicket7434() {
        ImportHandler req = newHandler("http://localhost:8111/import?url=http://localhost:8888/relations?relations=19711&mode=recursive");
        assertEquals("http://localhost:8888/relations?relations=19711&mode=recursive", req.args.get("url"));
    }

    /**
     * Unit test for bad request - no param.
     * @throws Exception if any error occurs
     */
    @Test
    public void testBadRequestNoParam() throws Exception {
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("MalformedURLException: null");
        newHandler(null).handle();
    }

    /**
     * Unit test for bad request - invalid URL.
     * @throws Exception if any error occurs
     */
    @Test
    public void testBadRequestInvalidUrl() throws Exception {
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("MalformedURLException: no protocol: invalid_url");
        newHandler("https://localhost?url=invalid_url").handle();
    }

    /**
     * Unit test for bad request - incomplete URL.
     * @throws Exception if any error occurs
     */
    @Test
    public void testBadRequestIncompleteUrl() throws Exception {
        thrown.expect(RequestHandlerBadRequestException.class);
        thrown.expectMessage("The following keys are mandatory, but have not been provided: url");
        newHandler("https://localhost").handle();
    }

    /**
     * Unit test for nominal request - local data file.
     * @throws Exception if any error occurs
     */
    @Test
    public void testNominalRequest() throws Exception {
        String url = new File(TestUtils.getRegressionDataFile(11957, "data.osm")).toURI().toURL().toExternalForm();
        try {
            newHandler("https://localhost?url=" + Utils.encodeUrl(url)).handle();
        } finally {
            for (OsmDataLayer layer : Main.getLayerManager().getLayersOfType(OsmDataLayer.class)) {
                Main.main.removeLayer(layer);
            }
        }
    }
}
