// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.preferences;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.awt.BasicStroke;

import org.junit.Rule;
import org.junit.Test;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.testutils.JOSMTestRules;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Test {@link StrokeProperty}
 * @author Michael Zangl
 * @since xxx
 */
public class StrokePropertyTest {
    /**
     * This is a preference test
     */
    @Rule
    @SuppressFBWarnings(value = "URF_UNREAD_PUBLIC_OR_PROTECTED_FIELD")
    public JOSMTestRules test = new JOSMTestRules().preferences();

    /**
     * Test {@link StrokeProperty#get()}
     */
    @Test
    public void testGetValue() {
        StrokeProperty property = new StrokeProperty("x", "1");

        Main.pref.put("x", "11");
        BasicStroke bs = property.get();
        assertWide(bs);
        assertEquals(11, bs.getLineWidth(), 1e-10);
        assertEquals(null, bs.getDashArray());

        Main.pref.put("x", ".5");
        bs = property.get();
        assertThin(bs);
        assertEquals(.5, bs.getLineWidth(), 1e-10);
        assertEquals(null, bs.getDashArray());

        Main.pref.put("x", "2 1");
        bs = property.get();
        assertWide(bs);
        assertEquals(2, bs.getLineWidth(), 1e-10);
        assertArrayEquals(new float[] {1}, bs.getDashArray(), 1e-10f);

        Main.pref.put("x", "2 0.1 1 10");
        bs = property.get();
        assertWide(bs);
        assertEquals(2, bs.getLineWidth(), 1e-10);
        assertArrayEquals(new float[] {0.1f, 1, 10}, bs.getDashArray(), 1e-10f);

        Main.pref.put("x", "x");
        bs = property.get();
        assertThin(bs);
        assertEquals(1, bs.getLineWidth(), 1e-10);
        assertEquals(null, bs.getDashArray());

        // ignore dashes
        Main.pref.put("x", "11 0 0 0.0001");
        bs = property.get();
        assertWide(bs);
        assertEquals(11, bs.getLineWidth(), 1e-10);
        assertEquals(null, bs.getDashArray());
    }

    /**
     * Test {@link StrokeProperty#put(BasicStroke)}
     */
    @Test
    public void testPutValue() {
        StrokeProperty property = new StrokeProperty("x", new BasicStroke(12));
        BasicStroke bs = property.get();

        assertWide(bs);
        assertEquals(12, bs.getLineWidth(), 1e-10);
        assertEquals(null, bs.getDashArray());

        property.put(new BasicStroke(2, 0, 0, 1, new float[] {0.1f, 1, 10}, 0));
        bs = property.get();
        assertWide(bs);
        assertEquals(2, bs.getLineWidth(), 1e-10);
        assertArrayEquals(new float[] {0.1f, 1, 10}, bs.getDashArray(), 1e-10f);
    }

    private void assertThin(BasicStroke bs) {
        assertBase(bs);
        assertEquals(BasicStroke.CAP_BUTT, bs.getEndCap());
        assertEquals(BasicStroke.JOIN_MITER, bs.getLineJoin());
    }

    private void assertWide(BasicStroke bs) {
        assertBase(bs);
        assertEquals(BasicStroke.CAP_ROUND, bs.getEndCap());
        assertEquals(BasicStroke.JOIN_ROUND, bs.getLineJoin());
    }

    private void assertBase(BasicStroke bs) {
        assertEquals(10, bs.getMiterLimit(), 1e-10);
        assertEquals(0, bs.getDashPhase(), 1e-10);
    }
}
