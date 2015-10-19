// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mappaint.mapcss;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.AbstractPrimitive.KeyValueVisitor;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.mappaint.Cascade;
import org.openstreetmap.josm.gui.mappaint.Environment;
import org.openstreetmap.josm.gui.mappaint.LineElemStyle;
import org.openstreetmap.josm.gui.mappaint.MultiCascade;
import org.openstreetmap.josm.gui.mappaint.Range;
import org.openstreetmap.josm.gui.mappaint.StyleKeys;
import org.openstreetmap.josm.gui.mappaint.StyleSetting;
import org.openstreetmap.josm.gui.mappaint.StyleSetting.BooleanStyleSetting;
import org.openstreetmap.josm.gui.mappaint.StyleSource;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.KeyCondition;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.KeyMatchType;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.KeyValueCondition;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.Op;
import org.openstreetmap.josm.gui.mappaint.mapcss.Condition.SimpleKeyValueCondition;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector.ChildOrParentSelector;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector.GeneralSelector;
import org.openstreetmap.josm.gui.mappaint.mapcss.Selector.OptimizedGeneralSelector;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.MapCSSParser;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.ParseException;
import org.openstreetmap.josm.gui.mappaint.mapcss.parsergen.TokenMgrError;
import org.openstreetmap.josm.gui.preferences.SourceEntry;
import org.openstreetmap.josm.io.CachedFile;
import org.openstreetmap.josm.tools.CheckParameterUtil;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.Utils;

/**
 * This is a mappaint style that is based on MapCSS rules.
 */
public class MapCSSStyleSource extends StyleSource {

    /**
     * The accepted MIME types sent in the HTTP Accept header.
     * @since 6867
     */
    public static final String MAPCSS_STYLE_MIME_TYPES = "text/x-mapcss, text/mapcss, text/css; q=0.9, text/plain; q=0.8, application/zip, application/octet-stream; q=0.5";

    // all rules
    public final List<MapCSSRule> rules = new ArrayList<>();
    // rule indices, filtered by primitive type
    public final MapCSSRuleIndex nodeRules = new MapCSSRuleIndex(); // nodes
    public final MapCSSRuleIndex wayRules = new MapCSSRuleIndex(); // ways without tag area=no
    public final MapCSSRuleIndex wayNoAreaRules = new MapCSSRuleIndex(); // ways with tag area=no
    public final MapCSSRuleIndex relationRules = new MapCSSRuleIndex(); // relations that are not multipolygon relations
    public final MapCSSRuleIndex multipolygonRules = new MapCSSRuleIndex(); // multipolygon relations
    public final MapCSSRuleIndex canvasRules = new MapCSSRuleIndex(); // rules to apply canvas properties

    private Color backgroundColorOverride;
    private String css;
    private ZipFile zipFile;

    /**
     * This lock prevents concurrent execution of {@link MapCSSRuleIndex#clear() } /
     * {@link MapCSSRuleIndex#initIndex()} and {@link MapCSSRuleIndex#getRuleCandidates }.
     *
     * For efficiency reasons, these methods are synchronized higher up the
     * stack trace.
     */
    public static final ReadWriteLock STYLE_SOURCE_LOCK = new ReentrantReadWriteLock();

    /**
     * Set of all supported MapCSS keys.
     */
    protected static final Set<String> SUPPORTED_KEYS = new HashSet<>();
    static {
        Field[] declaredFields = StyleKeys.class.getDeclaredFields();
        for (Field f : declaredFields) {
            try {
                SUPPORTED_KEYS.add((String) f.get(null));
                if (!f.getName().toLowerCase(Locale.ENGLISH).replace('_', '-').equals(f.get(null))) {
                    throw new RuntimeException(f.getName());
                }
            } catch (IllegalArgumentException | IllegalAccessException ex) {
                throw new RuntimeException(ex);
            }
        }
        for (LineElemStyle.LineType lt : LineElemStyle.LineType.values()) {
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.COLOR);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.DASHES);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.DASHES_BACKGROUND_COLOR);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.DASHES_BACKGROUND_OPACITY);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.DASHES_OFFSET);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.LINECAP);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.LINEJOIN);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.MITERLIMIT);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.OFFSET);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.OPACITY);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.REAL_WIDTH);
            SUPPORTED_KEYS.add(lt.prefix + StyleKeys.WIDTH);
        }
    }

    /**
     * A collection of {@link MapCSSRule}s, that are indexed by tag key and value.
     *
     * Speeds up the process of finding all rules that match a certain primitive.
     *
     * Rules with a {@link SimpleKeyValueCondition} [key=value] or rules that require a specific key to be set are
     * indexed. Now you only need to loop the tags of a primitive to retrieve the possibly matching rules.
     *
     * To use this index, you need to {@link #add(MapCSSRule)} all rules to it. You then need to call
     * {@link #initIndex()}. Afterwards, you can use {@link #getRuleCandidates(OsmPrimitive)} to get an iterator over
     * all rules that might be applied to that primitive.
     */
    public static class MapCSSRuleIndex {

        /**
         * This is subset of rules.
         * @author Michael Zangl
         */
        public abstract class MapCSSRuleSubset {

            /**
             * Add a rule to this set.
             * @param ruleIndex The index of the rule.
             */
            public void set(int ruleIndex) {
                throw new UnsupportedOperationException();
            }

            /**
             * Sets all rules that are in this set in the given {@link MapCSSRuleBitSubset}
             * @param setIn The bitset to set our rules in.
             */
            public abstract void setIn(MapCSSRuleBitSubset setIn);

            /**
             * Gets an optimized version of this rule set, e.g. bit sets that only have few entries are converted to a list set.
             * @return The optimized version.
             */
            public abstract MapCSSRuleSubset optimized();
        }

        /**
         * This is a subset of rules represented by a bitmask. It takes O(len(rules)) storage space.
         * @author Michael Zangl
         */
        public class MapCSSRuleBitSubset extends MapCSSRuleSubset implements Iterable<MapCSSRule> {
            protected final static int LONG_BITS = 64;
            protected final long[] bitData;

            /**
             * Creates a new, empty {@link MapCSSRuleBitSubset}.
             */
            public MapCSSRuleBitSubset() {
                this(new long[(rules.size() + (LONG_BITS - 1)) / LONG_BITS]);
            }

            private MapCSSRuleBitSubset(long[] bitData) {
                this.bitData = bitData;
            }

            /**
             * Creates a new rule set from a given {@link MapCSSRuleBitSubset}
             * @param copyFrom The set to get the contents from.
             */
            public MapCSSRuleBitSubset(MapCSSRuleBitSubset copyFrom) {
                this(Arrays.copyOf(copyFrom.bitData, copyFrom.bitData.length));
            }

            @Override
            public void set(int ruleIndex) {
                bitData[ruleIndex / LONG_BITS] |= (1l << (ruleIndex % LONG_BITS));
            }

            @Override
            public void setIn(MapCSSRuleBitSubset setIn) {
                for (int i = 0; i < bitData.length; i++) {
                    setIn.bitData[i] |= bitData[i];
                }
            }

            @Override
            public MapCSSRuleSubsetIterator iterator() {
                return new MapCSSRuleSubsetIterator(this);
            }

            /**
             * Gets the size of this set.
             * @return The number of rules in this set.
             */
            public int size() {
                int size = 0;
                for (long data : bitData) {
                    size += Long.bitCount(data);
                }
                return size;
            }

            @Override
            public MapCSSRuleSubset optimized() {
                if (size() > bitData.length) {
                    return this;
                } else {
                    return new MapCSSRuleListSubset(this);
                }
            }

            /**
             * Check if a rule is in this set.
             * @param ruleIndex The index of the rule.
             * @return true if it is set.
             */
            public boolean isSet(int ruleIndex) {
                return (bitData[ruleIndex / LONG_BITS] & (1l << (ruleIndex % LONG_BITS))) != 0;
            }

            @Override
            public String toString() {
                return "MapCSSRuleBitSubset [" + toStringHelper() + "]";
            }

            protected String toStringHelper() {
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < rules.size(); i++) {
                    if (isSet(i)) {
                        if (builder.length() > 0) {
                            builder.append(",");
                        }
                        builder.append(i);
                    }
                }
                return builder.toString();
            }
        }

        /**
         * This is an iterator over a subset of rules. It is backed by a bitset.
         * <p>
         * This contains a copy of the bitset. Before using the iterator, you can change the content of the bitset.
         * <p>
         * By calling #visitKeyValue, you can automatically add the rules required for this key/value pair.
         * @author Michael Zangl
         */
        public class MapCSSRuleSubsetIterator extends MapCSSRuleBitSubset implements Iterator<MapCSSRule>,
                KeyValueVisitor {
            private int next = -2;

            /**
             * Create a new iterator of the given {@link MapCSSRuleBitSubset}
             * @param copyFrom The bitset.
             */
            public MapCSSRuleSubsetIterator(MapCSSRuleBitSubset copyFrom) {
                super(copyFrom);
            }

            private int findNext(int afterIncluding) {
                if (afterIncluding > rules.size() || rules.isEmpty()) {
                    return -1;
                }

                int wordIndex = afterIncluding / 64;
                long data = bitData[wordIndex];
                data &= (-1l << afterIncluding);

                while (data == 0) {
                    wordIndex++;
                    if (wordIndex >= bitData.length) {
                        return -1;
                    }
                    data = bitData[wordIndex];
                }

                return (wordIndex * LONG_BITS) + Long.numberOfTrailingZeros(data);
            }

            @Override
            public boolean hasNext() {
                if (next == -2) {
                    next = findNext(0);
                }
                return next >= 0;
            }

            @Override
            public MapCSSRule next() {
                if (next == -2) {
                    next = findNext(0);
                }
                if (next == -1) {
                    throw new IllegalStateException();
                }
                MapCSSRule rule = rules.get(next);
                next = findNext(next + 1);
                return rule;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void visitKeyValue(AbstractPrimitive p, String key, String value) {
                MapCSSKeyRules v = index.get(key);
                if (v != null) {
                    MapCSSRuleSubset rs = v.get(value);
                    rs.setIn(this);
                }
            }

            @Override
            public String toString() {
                return "MapCSSRuleSubsetIterator [" + toStringHelper() + ", next=" + next + "]";
            }
        }

        /**
         * This is a special subset of rules represented by a list of rule indexes. This datastructures size is linear to the nuber of rules it contains.
         * @author Michael Zangl
         */
        public class MapCSSRuleListSubset extends MapCSSRuleSubset {
            private int[] subRules;

            /**
             * Creates a new list subset.
             * @param copyFrom The bit subset to copy the data from.
             */
            public MapCSSRuleListSubset(MapCSSRuleBitSubset copyFrom) {
                subRules = new int[copyFrom.size()];
                int j = 0;
                // Note: We might optimize this some time.
                for (int i = 0; i < rules.size(); i++) {
                    if (copyFrom.isSet(i)) {
                        subRules[j++] = i;
                    }
                }
            }

            @Override
            public void setIn(MapCSSRuleBitSubset setIn) {
                for (int ruleIndex : subRules) {
                    setIn.set(ruleIndex);
                }
            }

            @Override
            public String toString() {
                return "MapCSSRuleListSubset [" + Arrays.toString(subRules) + "]";
            }

            @Override
            public MapCSSRuleSubset optimized() {
                return this;
            }
        }

        /**
         * This is a map of all rules that are only applied if the primitive has a given key (and possibly value)
         *
         * @author Michael Zangl
         */
        private final class MapCSSKeyRules {
            MapCSSRuleBitSubset generalRulesBuilder = new MapCSSRuleBitSubset();

            /**
             * The indexes of rules that might be applied if this tag is present and the value has no special handling.
             */
            MapCSSRuleSubset generalRules = generalRulesBuilder;

            /**
             * A map that sores the indexes of rules that might be applied if the key=value pair is present on this
             * primitive. This includes all key=* rules.
             * <p>
             * TODO: Use a {@link IdentityHashMap} for this. Lookup will be 5 times faster then.
             */
            final HashMap<String, MapCSSRuleSubset> specialRules = new HashMap<>();

            public void addForKey(int ruleIndex) {
                generalRulesBuilder.set(ruleIndex);
                for (MapCSSRuleSubset r : specialRules.values()) {
                    r.set(ruleIndex);
                }
            }

            public void addForKeyAndValue(String value, int ruleIndex) {
                MapCSSRuleSubset forValue = specialRules.get(value);
                if (forValue == null) {
                    forValue = new MapCSSRuleBitSubset(generalRulesBuilder);
                    String interned = value.intern();
                    specialRules.put(interned, forValue);
                }
                forValue.set(ruleIndex);
            }

            /**
             * Gets the rules for the key=value pair using this key. Call {@link #optimize()} before calling this.
             * @param value The value for this key.
             * @return A subset of rules.
             */
            public MapCSSRuleSubset get(String value) {
                MapCSSRuleSubset forValue = specialRules.get(value);
                if (forValue != null)
                    return forValue;
                else
                    return generalRules;
            }

            /**
             * Optimize this map. Any changes afterwards may have unpredictable results.
             */
            public void optimize() {
                generalRules = generalRulesBuilder.optimized();
                generalRulesBuilder = null;
                for (Entry<String, MapCSSRuleSubset> v : specialRules.entrySet()) {
                    MapCSSRuleSubset optimized = v.getValue().optimized();
                    specialRules.put(v.getKey().intern(), optimized);
                }
            }
        }

        /**
         * All rules this index is for. Once this index is built, this list is sorted.
         */
        protected final List<MapCSSRule> rules = new ArrayList<>();
        /**
         * All rules that only apply when the given key is present.
         * <p>
         * TODO: Use a {@link IdentityHashMap} for this. Lookup will be 5 times faster then. This requires all keys to be interned.
         */
        protected final Map<String, MapCSSKeyRules> index = new HashMap<>();
        /**
         * Rules that do not require any key to be present. Only the index in the {@link #rules} array is stored.
         */
        private MapCSSRuleBitSubset remaining = null;

        /**
         * Add a rule to this index. This needs to be called before {@link #initIndex()} is called.
         * @param rule The rule to add.
         */
        public void add(MapCSSRule rule) {
            rules.add(rule);
            remaining = null;
        }

        /**
         * Initialize the index.
         * <p>
         * You must own the write lock of STYLE_SOURCE_LOCK when calling this method.
         */
        public void initIndex() {
            Collections.sort(rules);
            remaining = new MapCSSRuleBitSubset();
            for (int ruleIndex = 0; ruleIndex < rules.size(); ruleIndex++) {
                MapCSSRule r = rules.get(ruleIndex);
                // find the rightmost selector, this must be a GeneralSelector
                Selector selRightmost = r.selector;
                while (selRightmost instanceof ChildOrParentSelector) {
                    selRightmost = ((ChildOrParentSelector) selRightmost).right;
                }
                OptimizedGeneralSelector s = (OptimizedGeneralSelector) selRightmost;
                if (s.conds == null) {
                    remaining.set(ruleIndex);
                    continue;
                }
                List<SimpleKeyValueCondition> sk = new ArrayList<>(Utils.filteredCollection(s.conds,
                        SimpleKeyValueCondition.class));
                if (!sk.isEmpty()) {
                    SimpleKeyValueCondition c = sk.get(sk.size() - 1);
                    getEntryInIndex(c.k).addForKeyAndValue(c.v, ruleIndex);
                } else {
                    String key = findAnyRequiredKey(s.conds);
                    if (key != null) {
                        getEntryInIndex(key).addForKey(ruleIndex);
                    } else {
                        remaining.set(ruleIndex);
                    }
                }
            }

            // optimize all sets
            for (MapCSSKeyRules rules : index.values()) {
                rules.optimize();
            }
        }

        /**
         * Search for any key that condition might depend on.
         *
         * @param conds The conditions to search through.
         * @return An arbitrary key this rule depends on or <code>null</code> if there is no such key.
         */
        private String findAnyRequiredKey(List<Condition> conds) {
            String key = null;
            for (Condition c : conds) {
                if (c instanceof KeyCondition) {
                    KeyCondition keyCondition = (KeyCondition) c;
                    if (!keyCondition.negateResult && conditionRequiresKeyPresence(keyCondition.matchType)) {
                        key = keyCondition.label;
                    }
                } else if (c instanceof KeyValueCondition) {
                    KeyValueCondition keyValueCondition = (KeyValueCondition) c;
                    if (!Op.NEGATED_OPS.contains(keyValueCondition.op)) {
                        key = keyValueCondition.k;
                    }
                }
            }
            return key;
        }

        private static boolean conditionRequiresKeyPresence(KeyMatchType matchType) {
            return matchType != KeyMatchType.REGEX;
        }

        private MapCSSKeyRules getEntryInIndex(String key) {
            MapCSSKeyRules rulesWithMatchingKey = index.get(key);
            if (rulesWithMatchingKey == null) {
                rulesWithMatchingKey = new MapCSSKeyRules();
                index.put(key.intern(), rulesWithMatchingKey);
            }
            return rulesWithMatchingKey;
        }

        /**
         * Get a subset of all rules that might match the primitive. Rules not included in the result are guaranteed to
         * not match this primitive.
         * <p>
         * You must have a read lock of STYLE_SOURCE_LOCK when calling this method.
         *
         * @param osm the primitive to match
         * @return An iterator over possible rules in the right order.
         */
        public Iterator<MapCSSRule> getRuleCandidates(OsmPrimitive osm) {
            MapCSSRuleSubsetIterator ruleCandidates = remaining.iterator();
            osm.visitKeys(ruleCandidates);
            return ruleCandidates;
        }

        /**
         * Clear the index.
         * <p>
         * You must own the write lock STYLE_SOURCE_LOCK when calling this method.
         */
        public void clear() {
            rules.clear();
            index.clear();
            remaining = null;
        }
    }

    /**
     * Constructs a new, active {@link MapCSSStyleSource}.
     * @param url URL that {@link org.openstreetmap.josm.io.CachedFile} understands
     * @param name The name for this StyleSource
     * @param shortdescription The title for that source.
     */
    public MapCSSStyleSource(String url, String name, String shortdescription) {
        super(url, name, shortdescription);
    }

    /**
     * Constructs a new {@link MapCSSStyleSource}
     * @param entry The entry to copy the data (url, name, ...) from.
     */
    public MapCSSStyleSource(SourceEntry entry) {
        super(entry);
    }

    /**
     * <p>Creates a new style source from the MapCSS styles supplied in
     * {@code css}</p>
     *
     * @param css the MapCSS style declaration. Must not be null.
     * @throws IllegalArgumentException if {@code css} is null
     */
    public MapCSSStyleSource(String css) {
        super(null, null, null);
        CheckParameterUtil.ensureParameterNotNull(css);
        this.css = css;
    }

    @Override
    public void loadStyleSource() {
        STYLE_SOURCE_LOCK.writeLock().lock();
        try {
            init();
            rules.clear();
            nodeRules.clear();
            wayRules.clear();
            wayNoAreaRules.clear();
            relationRules.clear();
            multipolygonRules.clear();
            canvasRules.clear();
            try (InputStream in = getSourceInputStream()) {
                try {
                    // evaluate @media { ... } blocks
                    MapCSSParser preprocessor = new MapCSSParser(in, "UTF-8", MapCSSParser.LexicalState.PREPROCESSOR);
                    String mapcss = preprocessor.pp_root(this);

                    // do the actual mapcss parsing
                    InputStream in2 = new ByteArrayInputStream(mapcss.getBytes(StandardCharsets.UTF_8));
                    MapCSSParser parser = new MapCSSParser(in2, "UTF-8", MapCSSParser.LexicalState.DEFAULT);
                    parser.sheet(this);

                    loadMeta();
                    loadCanvas();
                    loadSettings();
                } finally {
                    closeSourceInputStream(in);
                }
            } catch (IOException e) {
                Main.warn(tr("Failed to load Mappaint styles from ''{0}''. Exception was: {1}", url, e.toString()));
                Main.error(e);
                logError(e);
            } catch (TokenMgrError e) {
                Main.warn(tr("Failed to parse Mappaint styles from ''{0}''. Error was: {1}", url, e.getMessage()));
                Main.error(e);
                logError(e);
            } catch (ParseException e) {
                Main.warn(tr("Failed to parse Mappaint styles from ''{0}''. Error was: {1}", url, e.getMessage()));
                Main.error(e);
                logError(new ParseException(e.getMessage())); // allow e to be garbage collected, it links to the entire token stream
            }
            // optimization: filter rules for different primitive types
            for (MapCSSRule r : rules) {
                // find the rightmost selector, this must be a GeneralSelector
                Selector selRightmost = r.selector;
                while (selRightmost instanceof ChildOrParentSelector) {
                    selRightmost = ((ChildOrParentSelector) selRightmost).right;
                }
                MapCSSRule optRule = new MapCSSRule(r.selector.optimizedBaseCheck(), r.declaration);
                final String base = ((GeneralSelector) selRightmost).getBase();
                switch (base) {
                case "node":
                    nodeRules.add(optRule);
                    break;
                case "way":
                    wayNoAreaRules.add(optRule);
                    wayRules.add(optRule);
                    break;
                case "area":
                    wayRules.add(optRule);
                    multipolygonRules.add(optRule);
                    break;
                case "relation":
                    relationRules.add(optRule);
                    multipolygonRules.add(optRule);
                    break;
                case "*":
                    nodeRules.add(optRule);
                    wayRules.add(optRule);
                    wayNoAreaRules.add(optRule);
                    relationRules.add(optRule);
                    multipolygonRules.add(optRule);
                    break;
                case "canvas":
                    canvasRules.add(r);
                    break;
                case "meta":
                case "setting":
                    break;
                default:
                    final RuntimeException e = new RuntimeException(MessageFormat.format(
                            "Unknown MapCSS base selector {0}", base));
                    Main.warn(tr("Failed to parse Mappaint styles from ''{0}''. Error was: {1}", url, e.getMessage()));
                    Main.error(e);
                    logError(e);
                }
            }
            nodeRules.initIndex();
            wayRules.initIndex();
            wayNoAreaRules.initIndex();
            relationRules.initIndex();
            multipolygonRules.initIndex();
            canvasRules.initIndex();
        } finally {
            STYLE_SOURCE_LOCK.writeLock().unlock();
        }
    }

    @Override
    public InputStream getSourceInputStream() throws IOException {
        if (css != null) {
            return new ByteArrayInputStream(css.getBytes(StandardCharsets.UTF_8));
        }
        CachedFile cf = getCachedFile();
        if (isZip) {
            File file = cf.getFile();
            zipFile = new ZipFile(file, StandardCharsets.UTF_8);
            zipIcons = file;
            ZipEntry zipEntry = zipFile.getEntry(zipEntryPath);
            return zipFile.getInputStream(zipEntry);
        } else {
            zipFile = null;
            zipIcons = null;
            return cf.getInputStream();
        }
    }

    @Override
    public CachedFile getCachedFile() throws IOException {
        return new CachedFile(url).setHttpAccept(MAPCSS_STYLE_MIME_TYPES);
    }

    @Override
    public void closeSourceInputStream(InputStream is) {
        super.closeSourceInputStream(is);
        if (isZip) {
            Utils.close(zipFile);
        }
    }

    /**
     * load meta info from a selector "meta"
     */
    private void loadMeta() {
        Cascade c = constructSpecial("meta");
        String pTitle = c.get("title", null, String.class);
        if (title == null) {
            title = pTitle;
        }
        String pIcon = c.get("icon", null, String.class);
        if (icon == null) {
            icon = pIcon;
        }
    }

    private void loadCanvas() {
        Cascade c = constructSpecial("canvas");
        backgroundColorOverride = c.get("fill-color", null, Color.class);
        if (backgroundColorOverride == null) {
            backgroundColorOverride = c.get("background-color", null, Color.class);
            if (backgroundColorOverride != null) {
                Main.warn(tr(
                        "Detected deprecated ''{0}'' in ''{1}'' which will be removed shortly. Use ''{2}'' instead.",
                        "canvas{background-color}", url, "fill-color"));
            }
        }
    }

    private void loadSettings() {
        settings.clear();
        settingValues.clear();
        MultiCascade mc = new MultiCascade();
        Node n = new Node();
        String code = LanguageInfo.getJOSMLocaleCode();
        n.put("lang", code);
        // create a fake environment to read the meta data block
        Environment env = new Environment(n, mc, "default", this);

        for (MapCSSRule r : rules) {
            if (r.selector instanceof GeneralSelector) {
                GeneralSelector gs = (GeneralSelector) r.selector;
                if ("setting".equals(gs.getBase())) {
                    if (!gs.matchesConditions(env)) {
                        continue;
                    }
                    env.layer = null;
                    env.layer = gs.getSubpart().getId(env);
                    r.execute(env);
                }
            }
        }
        for (Entry<String, Cascade> e : mc.getLayers()) {
            if ("default".equals(e.getKey())) {
                Main.warn("setting requires layer identifier e.g. 'setting::my_setting {...}'");
                continue;
            }
            Cascade c = e.getValue();
            String type = c.get("type", null, String.class);
            StyleSetting set = null;
            if ("boolean".equals(type)) {
                set = BooleanStyleSetting.create(c, this, e.getKey());
            } else {
                Main.warn("Unkown setting type: " + type);
            }
            if (set != null) {
                settings.add(set);
                settingValues.put(e.getKey(), set.getValue());
            }
        }
    }

    private Cascade constructSpecial(String type) {

        MultiCascade mc = new MultiCascade();
        Node n = new Node();
        String code = LanguageInfo.getJOSMLocaleCode();
        n.put("lang", code);
        // create a fake environment to read the meta data block
        Environment env = new Environment(n, mc, "default", this);

        for (MapCSSRule r : rules) {
            if (r.selector instanceof GeneralSelector) {
                GeneralSelector gs = (GeneralSelector) r.selector;
                if (gs.getBase().equals(type)) {
                    if (!gs.matchesConditions(env)) {
                        continue;
                    }
                    r.execute(env);
                }
            }
        }
        return mc.getCascade("default");
    }

    @Override
    public Color getBackgroundColorOverride() {
        return backgroundColorOverride;
    }

    @Override
    public void apply(MultiCascade mc, OsmPrimitive osm, double scale, boolean pretendWayIsClosed) {
        Environment env = new Environment(osm, mc, null, this);
        MapCSSRuleIndex matchingRuleIndex;
        if (osm instanceof Node) {
            matchingRuleIndex = nodeRules;
        } else if (osm instanceof Way) {
            if (osm.isKeyFalse("area")) {
                matchingRuleIndex = wayNoAreaRules;
            } else {
                matchingRuleIndex = wayRules;
            }
        } else {
            if (((Relation) osm).isMultipolygon()) {
                matchingRuleIndex = multipolygonRules;
            } else if (osm.hasKey("#canvas")) {
                matchingRuleIndex = canvasRules;
            } else {
                matchingRuleIndex = relationRules;
            }
        }

        // the declaration indices are sorted, so it suffices to save the
        // last used index
        int lastDeclUsed = -1;

        Iterator<MapCSSRule> candidates = matchingRuleIndex.getRuleCandidates(osm);
        while (candidates.hasNext()) {
            MapCSSRule r = candidates.next();
            env.clearSelectorMatchingInformation();
            env.layer = null;
            String sub = env.layer = r.selector.getSubpart().getId(env);
            if (r.selector.matches(env)) { // as side effect env.parent will be set (if s is a child selector)
                Selector s = r.selector;
                if (s.getRange().contains(scale)) {
                    mc.range = Range.cut(mc.range, s.getRange());
                } else {
                    mc.range = mc.range.reduceAround(scale, s.getRange());
                    continue;
                }

                if (r.declaration.idx == lastDeclUsed)
                    continue; // don't apply one declaration more than once
                lastDeclUsed = r.declaration.idx;
                if ("*".equals(sub)) {
                    for (Entry<String, Cascade> entry : mc.getLayers()) {
                        env.layer = entry.getKey();
                        if ("*".equals(env.layer)) {
                            continue;
                        }
                        r.execute(env);
                    }
                }
                env.layer = sub;
                r.execute(env);
            }
        }
    }

    public boolean evalSupportsDeclCondition(String feature, Object val) {
        if (feature == null)
            return false;
        if (SUPPORTED_KEYS.contains(feature))
            return true;
        switch (feature) {
        case "user-agent": {
            String s = Cascade.convertTo(val, String.class);
            return "josm".equals(s);
        }
        case "min-josm-version": {
            Float v = Cascade.convertTo(val, Float.class);
            return v != null && Math.round(v) <= Version.getInstance().getVersion();
        }
        case "max-josm-version": {
            Float v = Cascade.convertTo(val, Float.class);
            return v != null && Math.round(v) >= Version.getInstance().getVersion();
        }
        default:
            return false;
        }
    }

    @Override
    public String toString() {
        return Utils.join("\n", rules);
    }
}
