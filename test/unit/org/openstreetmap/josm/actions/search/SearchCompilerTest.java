// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions.search;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.hamcrest.CoreMatchers;
import org.junit.Before;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.actions.search.SearchCompiler.Match;
import org.openstreetmap.josm.actions.search.SearchCompiler.ParseError;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationData;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WayData;

/**
 * Unit tests for class {@link SearchCompiler}.
 */
public class SearchCompilerTest {

    /**
     * Setup test.
     */
    @Before
    public void setUp() {
        JOSMFixture.createUnitTestFixture().init();
    }

    private static final class SearchContext {
        final DataSet ds = new DataSet();
        final Node n1 = new Node(new LatLon(0, 0));
        final Node n2 = new Node(new LatLon(5, 5));
        final Way w1 = new Way();
        final Way w2 = new Way();
        final Relation r1 = new Relation();
        final Relation r2 = new Relation();

        private final Match m;
        private final Match n;

        private SearchContext(String state) throws ParseError {
            m = SearchCompiler.compile(state);
            n = SearchCompiler.compile('-' + state);
            ds.addPrimitive(n1);
            ds.addPrimitive(n2);
            w1.addNode(n1);
            w1.addNode(n2);
            w2.addNode(n1);
            w2.addNode(n2);
            ds.addPrimitive(w1);
            ds.addPrimitive(w2);
            r1.addMember(new RelationMember("", w1));
            r1.addMember(new RelationMember("", w2));
            r2.addMember(new RelationMember("", w1));
            r2.addMember(new RelationMember("", w2));
            ds.addPrimitive(r1);
            ds.addPrimitive(r2);
        }

        private void match(OsmPrimitive p, boolean cond) {
            if (cond) {
                assertTrue(p.toString(), m.match(p));
                assertFalse(p.toString(), n.match(p));
            } else {
                assertFalse(p.toString(), m.match(p));
                assertTrue(p.toString(), n.match(p));
            }
        }
    }

    protected OsmPrimitive newPrimitive(String key, String value) {
        final Node p = new Node();
        p.put(key, value);
        return p;
    }

    /**
     * Search anything.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testAny() throws ParseError {
        final SearchCompiler.Match c = SearchCompiler.compile("foo");
        assertTrue(c.match(newPrimitive("foobar", "true")));
        assertTrue(c.match(newPrimitive("name", "hello-foo-xy")));
        assertFalse(c.match(newPrimitive("name", "X")));
    }

    /**
     * Search by equality key=value.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testEquals() throws ParseError {
        final SearchCompiler.Match c = SearchCompiler.compile("foo=bar");
        assertFalse(c.match(newPrimitive("foobar", "true")));
        assertTrue(c.match(newPrimitive("foo", "bar")));
        assertFalse(c.match(newPrimitive("fooX", "bar")));
        assertFalse(c.match(newPrimitive("foo", "barX")));
    }

    /**
     * Search by comparison.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testCompare() throws ParseError {
        final SearchCompiler.Match c1 = SearchCompiler.compile("start_date>1950");
        assertTrue(c1.match(newPrimitive("start_date", "1950-01-01")));
        assertTrue(c1.match(newPrimitive("start_date", "1960")));
        assertFalse(c1.match(newPrimitive("start_date", "1950")));
        assertFalse(c1.match(newPrimitive("start_date", "1000")));
        assertTrue(c1.match(newPrimitive("start_date", "101010")));

        final SearchCompiler.Match c2 = SearchCompiler.compile("start_date<1960");
        assertTrue(c2.match(newPrimitive("start_date", "1950-01-01")));
        assertFalse(c2.match(newPrimitive("start_date", "1960")));
        assertTrue(c2.match(newPrimitive("start_date", "1950")));
        assertTrue(c2.match(newPrimitive("start_date", "1000")));
        assertTrue(c2.match(newPrimitive("start_date", "200")));

        final SearchCompiler.Match c3 = SearchCompiler.compile("name<I");
        assertTrue(c3.match(newPrimitive("name", "Alpha")));
        assertFalse(c3.match(newPrimitive("name", "Sigma")));

        final SearchCompiler.Match c4 = SearchCompiler.compile("\"start_date\"<1960");
        assertTrue(c4.match(newPrimitive("start_date", "1950-01-01")));
        assertFalse(c4.match(newPrimitive("start_date", "2000")));

        final SearchCompiler.Match c5 = SearchCompiler.compile("height>180");
        assertTrue(c5.match(newPrimitive("height", "200")));
        assertTrue(c5.match(newPrimitive("height", "99999")));
        assertFalse(c5.match(newPrimitive("height", "50")));
        assertFalse(c5.match(newPrimitive("height", "-9999")));
        assertFalse(c5.match(newPrimitive("height", "fixme")));

        final SearchCompiler.Match c6 = SearchCompiler.compile("name>C");
        assertTrue(c6.match(newPrimitive("name", "Delta")));
        assertFalse(c6.match(newPrimitive("name", "Alpha")));
    }

    /**
     * Search by nth.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testNth() throws ParseError {
        final DataSet dataSet = new DataSet();
        final Way way = new Way();
        final Node node0 = new Node(new LatLon(1, 1));
        final Node node1 = new Node(new LatLon(2, 2));
        final Node node2 = new Node(new LatLon(3, 3));
        dataSet.addPrimitive(way);
        dataSet.addPrimitive(node0);
        dataSet.addPrimitive(node1);
        dataSet.addPrimitive(node2);
        way.addNode(node0);
        way.addNode(node1);
        way.addNode(node2);
        assertFalse(SearchCompiler.compile("nth:2").match(node1));
        assertTrue(SearchCompiler.compile("nth:1").match(node1));
        assertFalse(SearchCompiler.compile("nth:0").match(node1));
        assertTrue(SearchCompiler.compile("nth:0").match(node0));
        assertTrue(SearchCompiler.compile("nth:2").match(node2));
        assertTrue(SearchCompiler.compile("nth:-1").match(node2));
        assertTrue(SearchCompiler.compile("nth:-2").match(node1));
        assertTrue(SearchCompiler.compile("nth:-3").match(node0));
    }

    /**
     * Search by negative nth.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testNthParseNegative() throws ParseError {
        assertThat(SearchCompiler.compile("nth:-1").toString(), CoreMatchers.is("Nth{nth=-1, modulo=false}"));
    }

    /**
     * Search by modified status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testModified() throws ParseError {
        SearchContext sc = new SearchContext("modified");
        // Not modified but new
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            assertFalse(p.toString(), p.isModified());
            assertTrue(p.toString(), p.isNewOrUndeleted());
            sc.match(p, true);
        }
        // Modified and new
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            p.setModified(true);
            assertTrue(p.toString(), p.isModified());
            assertTrue(p.toString(), p.isNewOrUndeleted());
            sc.match(p, true);
        }
        // Modified but not new
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            p.setOsmId(1, 1);
            assertTrue(p.toString(), p.isModified());
            assertFalse(p.toString(), p.isNewOrUndeleted());
            sc.match(p, true);
        }
        // Not modified nor new
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n2, sc.w2, sc.r2}) {
            p.setOsmId(2, 2);
            assertFalse(p.toString(), p.isModified());
            assertFalse(p.toString(), p.isNewOrUndeleted());
            sc.match(p, false);
        }
    }

    /**
     * Search by selected status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testSelected() throws ParseError {
        SearchContext sc = new SearchContext("selected");
        // Not selected
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            assertFalse(p.toString(), p.isSelected());
            sc.match(p, false);
        }
        // Selected
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n2, sc.w2, sc.r2}) {
            sc.ds.addSelected(p);
            assertTrue(p.toString(), p.isSelected());
            sc.match(p, true);
        }
    }

    /**
     * Search by incomplete status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testIncomplete() throws ParseError {
        SearchContext sc = new SearchContext("incomplete");
        // Not incomplete
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            assertFalse(p.toString(), p.isIncomplete());
            sc.match(p, false);
        }
        // Incomplete
        sc.n2.setCoor(null);
        WayData wd = new WayData();
        wd.setIncomplete(true);
        sc.w2.load(wd);
        RelationData rd = new RelationData();
        rd.setIncomplete(true);
        sc.r2.load(rd);
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n2, sc.w2, sc.r2}) {
            assertTrue(p.toString(), p.isIncomplete());
            sc.match(p, true);
        }
    }

    /**
     * Search by untagged status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testUntagged() throws ParseError {
        SearchContext sc = new SearchContext("untagged");
        // Untagged
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            assertFalse(p.toString(), p.isTagged());
            sc.match(p, true);
        }
        // Tagged
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n2, sc.w2, sc.r2}) {
            p.put("foo", "bar");
            assertTrue(p.toString(), p.isTagged());
            sc.match(p, false);
        }
    }

    /**
     * Search by closed status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testClosed() throws ParseError {
        SearchContext sc = new SearchContext("closed");
        // Closed
        sc.w1.addNode(sc.n1);
        for (Way w : new Way[]{sc.w1}) {
            assertTrue(w.toString(), w.isClosed());
            sc.match(w, true);
        }
        // Unclosed
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.n2, sc.w2, sc.r1, sc.r2}) {
            sc.match(p, false);
        }
    }

    /**
     * Search by new status.
     * @throws ParseError if an error has been encountered while compiling
     */
    @Test
    public void testNew() throws ParseError {
        SearchContext sc = new SearchContext("new");
        // New
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n1, sc.w1, sc.r1}) {
            assertTrue(p.toString(), p.isNew());
            sc.match(p, true);
        }
        // Not new
        for (OsmPrimitive p : new OsmPrimitive[]{sc.n2, sc.w2, sc.r2}) {
            p.setOsmId(2, 2);
            assertFalse(p.toString(), p.isNew());
            sc.match(p, false);
        }
    }
}
