// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresets;

/**
 * Unit test of {@link ConditionalKeys}.
 */
public class ConditionalKeysTest {

    private final ConditionalKeys test = new ConditionalKeys();

    /**
     * Setup test
     * @throws Exception if an error occurs
     */
    @Before
    public void setUp() throws Exception {
        JOSMFixture.createUnitTestFixture().init();
        TaggingPresets.readFromPreferences();
        test.initialize();
    }

    /**
     * Unit test of {@link ConditionalKeys#isKeyValid}.
     */
    @Test
    public void testKeyValid() {
        assertTrue(test.isKeyValid("maxspeed:conditional"));
        assertTrue(test.isKeyValid("motor_vehicle:conditional"));
        assertTrue(test.isKeyValid("bicycle:conditional"));
        assertTrue(test.isKeyValid("overtaking:hgv:conditional"));
        assertTrue(test.isKeyValid("maxspeed:hgv:backward:conditional"));
        assertTrue(test.isKeyValid("oneway:backward:conditional"));
        assertTrue(test.isKeyValid("fee:conditional"));
        assertFalse(test.isKeyValid("maxspeed:hgv:conditional:backward"));
    }

    /**
     * Unit test of {@link ConditionalKeys#isValueValid}.
     */
    @Test
    public void testValueValid() {
        assertTrue(test.isValueValid("maxspeed:conditional", "120 @ (06:00-19:00)"));
        assertFalse(test.isValueValid("maxspeed:conditional", " @ (06:00-19:00)"));
        assertFalse(test.isValueValid("maxspeed:conditional", "120 (06:00-19:00)"));
        assertFalse(test.isValueValid("maxspeed:conditional", "120 @ ()"));
        assertFalse(test.isValueValid("maxspeed:conditional", "120 @ "));
        assertFalse(test.isValueValid("maxspeed:conditional", "120 @ (06:00/19:00)"));
        assertTrue(test.isValueValid("maxspeed:conditional", "120 @ (06:00-20:00); 100 @ (22:00-06:00)"));
        assertTrue(test.isValueValid("motor_vehicle:conditional", "delivery @ (Mo-Fr 06:00-11:00,17:00-19:00;Sa 03:30-19:00)"));
        assertTrue(test.isValueValid("motor_vehicle:conditional", "no @ (10:00-18:00 AND length>5)"));
        assertFalse(test.isValueValid("motor_vehicle:conditional", "foo @ (10:00-18:00 AND length>5)"));
        assertFalse(test.isValueValid("motor_vehicle:conditional", "no @ (10:00until18:00 AND length>5)"));
        assertTrue(test.isValueValid("maxspeed:hgv:conditional", "60 @ (weight>7.5)"));
    }
}
