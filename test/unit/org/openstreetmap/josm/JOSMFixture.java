// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.TimeZone;

import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.layer.LayerManagerTest.TestLayer;
import org.openstreetmap.josm.gui.preferences.ToolbarPreferences;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.CertificateAmendment;
import org.openstreetmap.josm.io.OsmApi;
import org.openstreetmap.josm.tools.I18n;

/**
 * Fixture to define a proper and safe environment before running tests.
 */
public class JOSMFixture {

    /**
     * Returns a new test fixture initialized to "unit" home.
     * @return A new test fixture for unit tests
     */
    public static JOSMFixture createUnitTestFixture() {
        return new JOSMFixture("test/config/unit-josm.home");
    }

    /**
     * Returns a new test fixture initialized to "functional" home.
     * @return A new test fixture for functional tests
     */
    public static JOSMFixture createFunctionalTestFixture() {
        return new JOSMFixture("test/config/functional-josm.home");
    }

    /**
     * Returns a new test fixture initialized to "performance" home.
     * @return A new test fixture for performance tests
     */
    public static JOSMFixture createPerformanceTestFixture() {
        return new JOSMFixture("test/config/performance-josm.home");
    }

    private final String josmHome;

    /**
     * Constructs a new text fixture initialized to a given josm home.
     * @param josmHome The user home where preferences are to be read/written
     */
    public JOSMFixture(String josmHome) {
        this.josmHome = josmHome;
    }

    /**
     * Initializes the test fixture, without GUI.
     */
    public void init() {
        init(false);
    }

    /**
     * Initializes the test fixture, with or without GUI.
     * @param createGui if {@code true} creates main GUI components
     */
    public void init(boolean createGui) {

        // check josm.home
        //
        if (josmHome == null) {
            fail(MessageFormat.format("property ''{0}'' not set in test environment", "josm.home"));
        } else {
            File f = new File(josmHome);
            if (!f.exists() || !f.canRead()) {
                fail(MessageFormat.format(
                        // CHECKSTYLE.OFF: LineLength
                        "property ''{0}'' points to ''{1}'' which is either not existing ({2}) or not readable ({3}). Current directory is ''{4}''.",
                        // CHECKSTYLE.ON: LineLength
                        "josm.home", josmHome, f.exists(), f.canRead(), Paths.get("").toAbsolutePath()));
            }
        }
        System.setProperty("josm.home", josmHome);
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        Main.initApplicationPreferences();
        Main.pref.enableSaveOnPut(false);
        I18n.init();
        // initialize the plaform hook, and
        Main.determinePlatformHook();
        // call the really early hook before we anything else
        Main.platform.preStartupHook();

        Main.logLevel = 3;
        Main.pref.init(false);
        Main.pref.put("osm-server.url", "http://api06.dev.openstreetmap.org/api");
        I18n.set(Main.pref.get("language", "en"));

        try {
            CertificateAmendment.addMissingCertificates();
        } catch (IOException | GeneralSecurityException ex) {
            throw new RuntimeException(ex);
        }

        // init projection
        Main.setProjection(Projections.getProjectionByCode("EPSG:3857")); // Mercator

        // make sure we don't upload to or test against production
        //
        String url = OsmApi.getOsmApi().getBaseUrl().toLowerCase(Locale.ENGLISH).trim();
        if (url.startsWith("http://www.openstreetmap.org") || url.startsWith("http://api.openstreetmap.org")
            || url.startsWith("https://www.openstreetmap.org") || url.startsWith("https://api.openstreetmap.org")) {
            fail(MessageFormat.format("configured server url ''{0}'' seems to be a productive url, aborting.", url));
        }

        if (createGui) {
            GuiHelper.runInEDTAndWaitWithException(new Runnable() {
                @Override
                public void run() {
                    setupGUI();
                }
            });
        }
    }

    private void setupGUI() {
        Main.getLayerManager().resetState();
        assertTrue(Main.getLayerManager().getLayers().isEmpty());
        assertNull(Main.getLayerManager().getEditLayer());
        assertNull(Main.getLayerManager().getActiveLayer());

        if (Main.toolbar == null) {
            Main.toolbar = new ToolbarPreferences();
        }
        if (Main.main == null) {
            new MainApplication().initialize();
        }
        // Add a test layer to the layer manager to get the MapFrame
        Main.getLayerManager().addLayer(new TestLayer());
    }
}
