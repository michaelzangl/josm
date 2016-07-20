// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.cache;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileLock;
import java.util.Properties;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.apache.commons.jcs.access.CacheAccess;
import org.apache.commons.jcs.auxiliary.AuxiliaryCache;
import org.apache.commons.jcs.auxiliary.AuxiliaryCacheFactory;
import org.apache.commons.jcs.auxiliary.disk.behavior.IDiskCacheAttributes;
import org.apache.commons.jcs.auxiliary.disk.block.BlockDiskCacheAttributes;
import org.apache.commons.jcs.auxiliary.disk.block.BlockDiskCacheFactory;
import org.apache.commons.jcs.auxiliary.disk.indexed.IndexedDiskCacheAttributes;
import org.apache.commons.jcs.auxiliary.disk.indexed.IndexedDiskCacheFactory;
import org.apache.commons.jcs.engine.CompositeCacheAttributes;
import org.apache.commons.jcs.engine.behavior.ICompositeCacheAttributes.DiskUsagePattern;
import org.apache.commons.jcs.engine.control.CompositeCache;
import org.apache.commons.jcs.engine.control.CompositeCacheManager;
import org.apache.commons.jcs.utils.serialization.StandardSerializer;
import org.openstreetmap.gui.jmapviewer.FeatureAdapter;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.preferences.BooleanProperty;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.tools.Utils;

/**
 * @author Wiktor Niesiobędzki
 *
 * Wrapper class for JCS Cache. Sets some sane environment and returns instances of cache objects.
 * Static configuration for now assumes some small LRU cache in memory and larger LRU cache on disk
 * @since 8168
 */
public final class JCSCacheManager {
    private static final Logger LOG = FeatureAdapter.getLogger(JCSCacheManager.class.getCanonicalName());

    private static volatile CompositeCacheManager cacheManager;
    private static long maxObjectTTL = -1;
    private static final String PREFERENCE_PREFIX = "jcs.cache";
    public static BooleanProperty USE_BLOCK_CACHE = new BooleanProperty(PREFERENCE_PREFIX + ".use_block_cache", true);

    private static final AuxiliaryCacheFactory diskCacheFactory =
            USE_BLOCK_CACHE.get() ? new BlockDiskCacheFactory() : new IndexedDiskCacheFactory();
    private static FileLock cacheDirLock;

    /**
     * default objects to be held in memory by JCS caches (per region)
     */
    public static final IntegerProperty DEFAULT_MAX_OBJECTS_IN_MEMORY = new IntegerProperty(PREFERENCE_PREFIX + ".max_objects_in_memory", 1000);

    private JCSCacheManager() {
        // Hide implicit public constructor for utility classes
    }

    @SuppressWarnings("resource")
    private static void initialize() throws IOException {
        File cacheDir = new File(Main.pref.getCacheDirectory(), "jcs");

        if (!cacheDir.exists() && !cacheDir.mkdirs())
            throw new IOException("Cannot access cache directory");

        File cacheDirLockPath = new File(cacheDir, ".lock");
        if (!cacheDirLockPath.exists() && !cacheDirLockPath.createNewFile()) {
            LOG.log(Level.WARNING, "Cannot create cache dir lock file");
        }
        cacheDirLock = new FileOutputStream(cacheDirLockPath).getChannel().tryLock();

        if (cacheDirLock == null)
            LOG.log(Level.WARNING, "Cannot lock cache directory. Will not use disk cache");

        // raising logging level gives ~500x performance gain
        // http://westsworld.dk/blog/2008/01/jcs-and-performance/
        final Logger jcsLog = Logger.getLogger("org.apache.commons.jcs");
        jcsLog.setLevel(Level.INFO);
        jcsLog.setUseParentHandlers(false);
        // we need a separate handler from Main's, as we downgrade LEVEL.INFO to DEBUG level
        jcsLog.addHandler(new Handler() {
            final SimpleFormatter formatter = new SimpleFormatter();

            @Override
            public void publish(LogRecord record) {
                String msg = formatter.formatMessage(record);
                if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                    Main.error(msg);
                } else if (record.getLevel().intValue() >= Level.WARNING.intValue()) {
                    Main.warn(msg);
                    // downgrade INFO level to debug, as JCS is too verbose at INFO level
                } else if (record.getLevel().intValue() >= Level.INFO.intValue()) {
                    Main.debug(msg);
                } else {
                    Main.trace(msg);
                }
            }

            @Override
            public void flush() {
                // nothing to be done on flush
            }

            @Override
            public void close() {
                // nothing to be done on close
            }
        });

        // this could be moved to external file
        Properties props = new Properties();
        // these are default common to all cache regions
        // use of auxiliary cache and sizing of the caches is done with giving proper geCache(...) params
        // CHECKSTYLE.OFF: SingleSpaceSeparator
        props.setProperty("jcs.default.cacheattributes",                      CompositeCacheAttributes.class.getCanonicalName());
        props.setProperty("jcs.default.cacheattributes.MaxObjects",           DEFAULT_MAX_OBJECTS_IN_MEMORY.get().toString());
        props.setProperty("jcs.default.cacheattributes.UseMemoryShrinker",    "true");
        props.setProperty("jcs.default.cacheattributes.DiskUsagePatternName", "UPDATE"); // store elements on disk on put
        props.setProperty("jcs.default.elementattributes",                    CacheEntryAttributes.class.getCanonicalName());
        props.setProperty("jcs.default.elementattributes.IsEternal",          "false");
        props.setProperty("jcs.default.elementattributes.MaxLife",            Long.toString(maxObjectTTL));
        props.setProperty("jcs.default.elementattributes.IdleTime",           Long.toString(maxObjectTTL));
        props.setProperty("jcs.default.elementattributes.IsSpool",            "true");
        // CHECKSTYLE.ON: SingleSpaceSeparator
        CompositeCacheManager cm = CompositeCacheManager.getUnconfiguredInstance();
        cm.configure(props);
        cacheManager = cm;
    }

    /**
     * Returns configured cache object for named cache region
     * @param <K> key type
     * @param <V> value type
     * @param cacheName region name
     * @return cache access object
     * @throws IOException if directory is not found
     */
    public static <K, V> CacheAccess<K, V> getCache(String cacheName) throws IOException {
        return getCache(cacheName, DEFAULT_MAX_OBJECTS_IN_MEMORY.get().intValue(), 0, null);
    }

    /**
     * Returns configured cache object with defined limits of memory cache and disk cache
     * @param <K> key type
     * @param <V> value type
     * @param cacheName         region name
     * @param maxMemoryObjects  number of objects to keep in memory
     * @param maxDiskObjects    maximum size of the objects stored on disk in kB
     * @param cachePath         path to disk cache. if null, no disk cache will be created
     * @return cache access object
     * @throws IOException if directory is not found
     */
    public static <K, V> CacheAccess<K, V> getCache(String cacheName, int maxMemoryObjects, int maxDiskObjects, String cachePath)
            throws IOException {
        if (cacheManager != null)
            return getCacheInner(cacheName, maxMemoryObjects, maxDiskObjects, cachePath);

        synchronized (JCSCacheManager.class) {
            if (cacheManager == null)
                initialize();
            return getCacheInner(cacheName, maxMemoryObjects, maxDiskObjects, cachePath);
        }
    }

    @SuppressWarnings("unchecked")
    private static <K, V> CacheAccess<K, V> getCacheInner(String cacheName, int maxMemoryObjects, int maxDiskObjects, String cachePath)
            throws IOException {
        CompositeCache<K, V> cc = cacheManager.getCache(cacheName, getCacheAttributes(maxMemoryObjects));

        if (cachePath != null && cacheDirLock != null) {
            IDiskCacheAttributes diskAttributes = getDiskCacheAttributes(maxDiskObjects, cachePath, cacheName);
            /*
             * BlockDiskCache never optimizes the file, so when file size is reduced, it will never be truncated to desired size.
             *
             * If for some mysterious reason, file size is greater than the value set in preferences, just use the whole file. If the user
             * wants to reduce the file size, (s)he may just go to preferences and there it should be handled (by removing old file)
             */
            if (USE_BLOCK_CACHE.get()) {
                File diskCacheFile = new File(cachePath + File.separator + diskAttributes.getCacheName() + ".data");
                maxDiskObjects = (int) Math.max(maxDiskObjects, diskCacheFile.length()/1024);
            }
            try {
                if (cc.getAuxCaches().length == 0) {
                    AuxiliaryCache<K, V> diskCache = diskCacheFactory.createCache(diskAttributes, cacheManager, null, new StandardSerializer());
                    cc.setAuxCaches(new AuxiliaryCache[]{diskCache});
                }
            } catch (IOException e) {
                throw e;
            } catch (Exception e) {
                throw new IOException(e);
            }
        }
        return new CacheAccess<>(cc);
    }

    /**
     * Close all files to ensure, that all indexes and data are properly written
     */
    public static void shutdown() {
        // use volatile semantics to get consistent object
        CompositeCacheManager localCacheManager = cacheManager;
        if (localCacheManager != null) {
            localCacheManager.shutDown();
        }
    }

    private static IDiskCacheAttributes getDiskCacheAttributes(int maxDiskObjects, String cachePath, String cacheName) {
        IDiskCacheAttributes ret;
        if (USE_BLOCK_CACHE.get()) {
            BlockDiskCacheAttributes blockAttr = new BlockDiskCacheAttributes();
            blockAttr.setMaxKeySize(maxDiskObjects);
            blockAttr.setBlockSizeBytes(4096); // use 4k blocks
            ret = blockAttr;
        } else {
            IndexedDiskCacheAttributes indexAttr = new IndexedDiskCacheAttributes();
            indexAttr.setMaxKeySize(maxDiskObjects);
            ret = indexAttr;
        }
        ret.setDiskLimitType(IDiskCacheAttributes.DiskLimitType.SIZE);
        File path = new File(cachePath);
        if (!path.exists() && !path.mkdirs()) {
            LOG.log(Level.WARNING, "Failed to create cache path: {0}", cachePath);
        } else {
            ret.setDiskPath(cachePath);
        }
        ret.setCacheName(cacheName + (USE_BLOCK_CACHE.get() ? "_BLOCK_v2" : "_INDEX_v2"));

        removeStaleFiles(cachePath + File.separator + cacheName, (USE_BLOCK_CACHE.get() ? "_INDEX_v2" : "_BLOCK_v2"));
        return ret;
    }

    private static void removeStaleFiles(String basePathPart, String suffix) {
        deleteCacheFiles(basePathPart); // TODO: this can be removed around 2016.09
        deleteCacheFiles(basePathPart + "_BLOCK"); // TODO: this can be removed around 2016.09
        deleteCacheFiles(basePathPart + "_INDEX"); // TODO: this can be removed around 2016.09
        deleteCacheFiles(basePathPart + suffix);
    }

    private static void deleteCacheFiles(String basePathPart) {
        Utils.deleteFileIfExists(new File(basePathPart + ".key"));
        Utils.deleteFileIfExists(new File(basePathPart + ".data"));
    }

    private static CompositeCacheAttributes getCacheAttributes(int maxMemoryElements) {
        CompositeCacheAttributes ret = new CompositeCacheAttributes();
        ret.setMaxObjects(maxMemoryElements);
        ret.setDiskUsagePattern(DiskUsagePattern.UPDATE);
        return ret;
    }
}
