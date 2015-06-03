// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.validation.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.data.osm.OsmUtils;
import org.openstreetmap.josm.data.validation.TestError;

/**
 * JUnit Test of "Name mismatch" validation test.
 */
public class NameMismatchTest {

    /**
     * Setup test.
     */
    @Before
    public void setUp() {
        JOSMFixture.createUnitTestFixture().init();
    }

    List<TestError> test(String primitive) {
        final NameMismatch test = new NameMismatch();
        test.check(OsmUtils.createPrimitive(primitive));
        return test.getErrors();
    }

    /**
     * Test "A name is missing, even though name:* exists."
     */
    @Test
    public void test0() {
        final List<TestError> errors = test("node name:de=Europa");
        assertSame(1, errors.size());
        assertEquals("A name is missing, even though name:* exists.", errors.get(0).getMessage());
    }

    /**
     * Test "Missing name:*={0}. Add tag with correct language key."
     */
    @Test
    public void test1() {
        final List<TestError> errors = test("node name=Europe name:de=Europa");
        assertSame(1, errors.size());
        assertEquals("Missing name:*=Europe. Add tag with correct language key.", errors.get(0).getDescription());
    }

    /**
     * Test no error
     */
    @Test
    public void test2() {
        final List<TestError> errors = test("node name=Europe name:de=Europa name:en=Europe");
        assertSame(0, errors.size());
    }

    /**
     * Various other tests
     */
    @Test
    public void test3() {
        List<TestError> errors;
        errors = test("node \"name\"=\"Italia - Italien - Italy\"");
        assertSame(0, errors.size());
        errors = test("node name=\"Italia - Italien - Italy\" name:it=Italia");
        assertSame(2, errors.size());
        errors = test("node name=\"Italia - Italien - Italy\" name:it=Italia name:de=Italien");
        assertSame(1, errors.size());
        assertEquals("Missing name:*=Italy. Add tag with correct language key.", errors.get(0).getDescription());
        errors = test("node name=\"Italia - Italien - Italy\" name:it=Italia name:de=Italien name:en=Italy");
        assertSame(0, errors.size());
    }
}
