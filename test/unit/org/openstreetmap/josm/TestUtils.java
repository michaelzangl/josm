// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.awt.geom.Point2D;
import java.util.Arrays;
import java.util.Comparator;

import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Various utils, useful for unit tests.
 */
public final class TestUtils {

    private TestUtils() {
        // Hide constructor for utility classes
    }

    /**
     * Returns the path to test data root directory.
     * @return path to test data root directory
     */
    public static String getTestDataRoot() {
        String testDataRoot = System.getProperty("josm.test.data");
        if (testDataRoot == null || testDataRoot.isEmpty()) {
            testDataRoot = "test/data";
            System.out.println("System property josm.test.data is not set, using '" + testDataRoot + "'");
        }
        return testDataRoot.endsWith("/") ? testDataRoot : testDataRoot + "/";
    }

    /**
     * Gets path to test data directory for given ticket id.
     * @param ticketid Ticket numeric identifier
     * @return path to test data directory for given ticket id
     */
    public static String getRegressionDataDir(int ticketid) {
        return TestUtils.getTestDataRoot() + "/regress/" + ticketid;
    }

    /**
     * Gets path to given file in test data directory for given ticket id.
     * @param ticketid Ticket numeric identifier
     * @param filename File name
     * @return path to given file in test data directory for given ticket id
     */
    public static String getRegressionDataFile(int ticketid, String filename) {
        return getRegressionDataDir(ticketid) + '/' + filename;
    }

    /**
     * Checks that the given Comparator respects its contract on the given table.
     * @param comparator The comparator to test
     * @param array The array sorted for test purpose
     */
    public static <T> void checkComparableContract(Comparator<T> comparator, T[] array) {
        System.out.println("Validating Comparable contract on array of "+array.length+" elements");
        // Check each compare possibility
        for (int i = 0; i < array.length; i++) {
            T r1 = array[i];
            for (int j = i; j < array.length; j++) {
                T r2 = array[j];
                int a = comparator.compare(r1, r2);
                int b = comparator.compare(r2, r1);
                if (i == j || a == b) {
                    if (a != 0 || b != 0) {
                        fail(getFailMessage(r1, r2, a, b));
                    }
                } else {
                    if (a != -b) {
                        fail(getFailMessage(r1, r2, a, b));
                    }
                }
                for (int k = j; k < array.length; k++) {
                    T r3 = array[k];
                    int c = comparator.compare(r1, r3);
                    int d = comparator.compare(r2, r3);
                    if (a > 0 && d > 0) {
                        if (c <= 0) {
                           fail(getFailMessage(r1, r2, r3, a, b, c, d));
                        }
                    } else if (a == 0 && d == 0) {
                        if (c != 0) {
                            fail(getFailMessage(r1, r2, r3, a, b, c, d));
                        }
                    } else if (a < 0 && d < 0) {
                        if (c >= 0) {
                            fail(getFailMessage(r1, r2, r3, a, b, c, d));
                        }
                    }
                }
            }
        }
        // Sort relation array
        Arrays.sort(array, comparator);
    }

    private static <T> String getFailMessage(T o1, T o2, int a, int b) {
        return new StringBuilder("Compared\no1: ").append(o1).append("\no2: ")
        .append(o2).append("\ngave: ").append(a).append("/").append(b)
        .toString();
    }

    private static <T> String getFailMessage(T o1, T o2, T o3, int a, int b, int c, int d) {
        return new StringBuilder(getFailMessage(o1, o2, a, b))
        .append("\nCompared\no1: ").append(o1).append("\no3: ").append(o3).append("\ngave: ").append(c)
        .append("\nCompared\no2: ").append(o2).append("\no3: ").append(o3).append("\ngave: ").append(d)
        .toString();
    }

    /**
     * An assertion that fails if the provided coordinates are not the same (within the default server precision).
     * @param expected The expected EastNorth coordinate.
     * @param actual The actual value.
     */
    public static void assertEastNorthEquals(EastNorth expected, EastNorth actual) {
        assertEquals("Wrong x coordinate.", expected.getX(), actual.getX(), LatLon.MAX_SERVER_PRECISION);
        assertEquals("Wrong y coordinate.", expected.getY(), actual.getY(), LatLon.MAX_SERVER_PRECISION);
    }

    /**
     * An assertion that fails if the provided coordinates are not the same (within the default server precision).
     * @param expected The expected LatLon coordinate.
     * @param actual The actual value.
     */
    public static void assertLatLonEquals(LatLon expected, LatLon actual) {
        assertEquals("Wrong lat coordinate.", expected.getX(), actual.getX(), LatLon.MAX_SERVER_PRECISION);
        assertEquals("Wrong lon coordinate.", expected.getY(), actual.getY(), LatLon.MAX_SERVER_PRECISION);
    }

    /**
     * An assertion that fails if the provided points are not the same.
     * @param expected The expected Point2D
     * @param actual The actual value.
     */
    public static void assertPointEquals(Point2D expected, Point2D actual) {
        if (expected.distance(actual) > 0.0000001) {
            throw new AssertionError("Expected " + expected + " but got " + actual);
        }
    }
}
