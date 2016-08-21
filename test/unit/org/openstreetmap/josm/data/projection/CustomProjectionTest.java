// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.projection;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.stream.Stream;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Tests for {@link CustomProjection}
 * @author Michael Zangl
 * @since xxx
 */
public class CustomProjectionTest {
    /**
     * Need pref to load pref.
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences();

    /**
     * Test {@link CustomProjection#parseAngle(String, String)}
     * @throws ProjectionConfigurationException
     */
    @Test
    public void testParseAngle() throws ProjectionConfigurationException {
        assertEquals(0, CustomProjection.parseAngle("0", "xxx"), 1e-10);
        assertEquals(1, CustomProjection.parseAngle("1", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("1.1", "xxx"), 1e-10);

        assertEquals(1, CustomProjection.parseAngle("1d", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("1.1d", "xxx"), 1e-10);

        assertEquals(1 / 60.0, CustomProjection.parseAngle("1'", "xxx"), 1e-10);
        assertEquals(1.1 / 60.0, CustomProjection.parseAngle("1.1'", "xxx"), 1e-10);

        assertEquals(1 / 3600.0, CustomProjection.parseAngle("1\"", "xxx"), 1e-10);
        assertEquals(1.1 / 3600.0, CustomProjection.parseAngle("1.1\"", "xxx"), 1e-10);

        // negate
        assertEquals(-1.1, CustomProjection.parseAngle("-1.1", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("1.1N", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("1.1E", "xxx"), 1e-10);
        assertEquals(-1.1, CustomProjection.parseAngle("1.1S", "xxx"), 1e-10);
        assertEquals(-1.1, CustomProjection.parseAngle("1.1W", "xxx"), 1e-10);
        assertEquals(-1.1, CustomProjection.parseAngle("-1.1N", "xxx"), 1e-10);
        assertEquals(-1.1, CustomProjection.parseAngle("-1.1E", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("-1.1S", "xxx"), 1e-10);
        assertEquals(1.1, CustomProjection.parseAngle("-1.1W", "xxx"), 1e-10);

        // combine
        assertEquals(1.1 + 3 / 60.0 + 5.2 / 3600.0, CustomProjection.parseAngle("1.1d3'5.2\"", "xxx"), 1e-10);

        // fail
        Stream.of("", "-", "-N", "N", "1.1 ", "x", "1.1d1.1d", "1.1e","1.1.1",".1", "1.1d3\"5.2'").forEach(
                s -> {
                    try {
                        CustomProjection.parseAngle(s, "xxxx");
                        fail("Expected exception for " + s);
                    } catch (ProjectionConfigurationException e) {
                        // good!
                        assertTrue(e.getMessage().contains("xxx"));
                    }
                });
    }
}
