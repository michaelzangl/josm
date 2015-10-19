// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.KeyMatchType;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSRule.Declaration;
import org.openstreetmap.josm.gui.mappaint.mapcss.MapCSSStyleSource.MapCSSRuleIndex;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector.GeneralSelector;

/**
 * This test case tests the  MapCSS rule index.
 * @author Michael Zangl
 *
 */
public class MapCSSRuleIndexTest {

    private int declarationIndex;

    /**
     * Prepare the test..
     */
    @BeforeClass
    public static void createJOSMFixture() {
        // This has just too many side effects, including using the MapCSSRuleIndex
        //JOSMFixture.createPerformanceTestFixture().init(true);
        // This is enough for our setup.
        Main.pref = new Preferences();
    }

    @Before
    public void reset() {
        declarationIndex = 0;
    }

    @Test
    public void testRulesSort() {
        MapCSSRuleIndex ruleIndex = new MapCSSRuleIndex();
        MapCSSRule rules[] = new MapCSSRule[] { nextFakeKVRule("node", "key1", "value1"),
                nextFakeKVRule("node", "key2", "value1"), nextFakeKVRule("node", "key3", "value1"),
                nextFakeKVRule("node", "key4", "value1") };
        ruleIndex.add(rules[1]);
        ruleIndex.add(rules[3]);
        ruleIndex.add(rules[2]);
        ruleIndex.add(rules[0]);
        ruleIndex.initIndex();
        assertArrayEquals(rules, ruleIndex.rules.toArray());
    }

    @Test
    public void testClear() {
        MapCSSRuleIndex ruleIndex = new MapCSSRuleIndex();
        ruleIndex.add(nextFakeKVRule("node", "key1", "value1"));
        ruleIndex.add(nextFakeKVRule("node", "key2", "value1"));
        assertEquals(2, ruleIndex.rules.size());
        ruleIndex.initIndex();
        assertEquals(2, ruleIndex.rules.size());
        ruleIndex.clear();
        assertArrayEquals(new Object[0], ruleIndex.rules.toArray());
        ruleIndex.initIndex();
        assertArrayEquals(new Object[0], ruleIndex.rules.toArray());
    }

    /**
     * Test that an empty index is possible.
     */
    @Test
    public void testEmpty() {
        MapCSSRuleIndex ruleIndex = new MapCSSRuleIndex();
        ruleIndex.initIndex();

        Node n2 = new Node();
        assertCandidateEquals(ruleIndex.getRuleCandidates(n2));
        n2.put("key2", "value2");
        assertCandidateEquals(ruleIndex.getRuleCandidates(n2));
    }

    /**
     * Create a test index and retrive values from it.
     */
    @Test
    public void testRulesPutAndGet() {
        MapCSSRuleIndex ruleIndex = new MapCSSRuleIndex();
        MapCSSRule rules[] = new MapCSSRule[] {
                nextFakeKVRule("node", "key1", "value1"), // 0
                nextFakeKVRule("node", "key2", "value2"),// 1
                nextFakeKVRule("node", "key3", "value1"),// 2
                nextFakeKVRule("node", "key1", "value2"),// 3
                nextFakeKeyRule("node", "key2", false),// 4
                nextFakeKeyRule("node", "key4", false),// 5
                nextFakeKeyRule("node", "key4", true),// 6
                nextFakeRule(new GeneralSelector("node", null, null, null)),// 7
                nextFakeKVRule("node", "key1", "value1"), // 8
                nextFakeKVRule("node", "key1", "value1"), // 9
                nextFakeKVRule("node", "key1", "value1"), // 10
                nextFakeRule("node", new Condition.KeyValueCondition("key5", "va", Condition.Op.BEGINS_WITH, false)),// 11
                nextFakeRule("node", new Condition.KeyValueCondition("key5", "value1", Condition.Op.NEQ, false)),// 12
                nextFakeRule("node", new Condition.ClassCondition("clazz1", false)),// 13
                nextFakeRule("node", new Condition.KeyValueCondition("key6", "va", Condition.Op.BEGINS_WITH, false),
                        new Condition.KeyValueCondition("key7", "va", Condition.Op.BEGINS_WITH, false)),// 14
                        nextFakeRule("node", new Condition.KeyCondition("keyXY", false, KeyMatchType.REGEX)), //15
        };
        for (MapCSSRule rule : rules) {
            ruleIndex.add(rule);
        }
        // add some more data to get more than 64 rules.
        for (int i = 0; i < 80; i++) {
            ruleIndex.add(nextFakeKVRule("node", "somekey", "value" + i));
        }

        ruleIndex.initIndex();

        {
            Node n1 = new Node();
            assertCandidateEquals(ruleIndex.getRuleCandidates(n1), rules[6], rules[7], rules[12], rules[13], rules[15]);
            n1.put("key1", "xxx");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n1), rules[6], rules[7], rules[12], rules[13], rules[15]);
            n1.put("key1", "value1");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n1), rules[0], rules[6], rules[7], rules[8], rules[9],
                    rules[10], rules[12], rules[13], rules[15]);
        }
        {
            Node n2 = new Node();
            n2.put("key2", "value2");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n2), rules[1], rules[4], rules[6], rules[7], rules[12],
                    rules[13], rules[15]);
        }
        {
            // Using non-interned strings
            Node n2 = new Node();
            n2.put(new String("key2"), new String("value2"));
            assertCandidateEquals(ruleIndex.getRuleCandidates(n2), rules[1], rules[4], rules[6], rules[7], rules[12],
                    rules[13], rules[15]);
        }
        {
            Node n3 = new Node();
            n3.put("keyX", "valueY");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n3), rules[6], rules[7], rules[12], rules[13], rules[15]);
        }
        {
            Node n = new Node();
            n.put("key3", "value1");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n), rules[2], rules[6], rules[7], rules[12], rules[13], rules[15]);
        }
        {
            Node n = new Node();
            n.put("key5", "value1");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n), rules[6], rules[7], rules[11], rules[12], rules[13], rules[15]);
        }
        {
            // In this case, rule 14 should be registered on key7
            Node n = new Node();
            n.put("key6", "value1");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n), rules[6], rules[7], rules[12], rules[13], rules[15]);
            Node n2 = new Node();
            n2.put("key7", "value1");
            assertCandidateEquals(ruleIndex.getRuleCandidates(n2), rules[6], rules[7], rules[12], rules[13], rules[14], rules[15]);
        }
    }

    private void assertCandidateEquals(Iterator<MapCSSRule> ruleCandidates, MapCSSRule... mapCSSRule) {
        ArrayList<MapCSSRule> list = new ArrayList<>();
        while (ruleCandidates.hasNext()) {
            list.add(ruleCandidates.next());
        }
        assertArrayEquals(mapCSSRule, list.toArray());
    }

    private MapCSSRule nextFakeKVRule(String base, String key, String value) {
        return nextFakeRule(base, new Condition.SimpleKeyValueCondition(key, value));
    }

    private MapCSSRule nextFakeKeyRule(String base, String key, boolean negate) {
        return nextFakeRule(base, new Condition.KeyCondition(key, negate, KeyMatchType.EQ));
    }

    private MapCSSRule nextFakeRule(String base, Condition... condition) {
        Selector selector = new GeneralSelector(base, null, Arrays.asList(condition), null);
        return nextFakeRule(selector);
    }

    private MapCSSRule nextFakeRule(Selector selector) {
        return new MapCSSRule(selector, nextFakeDeclaration());
    }

    private Declaration nextFakeDeclaration() {
        return new Declaration(Collections.<Instruction> emptyList(), declarationIndex++);
    }

}
