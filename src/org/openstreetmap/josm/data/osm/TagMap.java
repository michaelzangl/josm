// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.data.osm;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Arrays;
import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.NoSuchElementException;

/**
 * This class provides a read/write map that uses the same format as {@link AbstractPrimitive#keys}.
 * It offers good performance for few keys.
 * It uses copy on write, so there cannot be a {@link ConcurrentModificationException} while iterating through it.
 *
 * @author Michael Zangl
 */
public class TagMap extends AbstractMap<String, String> {
    private static final String[] EMPTY_TAGS = new String[0];

    private static class TagEntryInterator implements Iterator<Entry<String, String>> {

        private final String[] tags;
        int currentIndex = 0;

        public TagEntryInterator(String[] tags) {
            super();
            this.tags = tags;
        }

        @Override
        public boolean hasNext() {
            return currentIndex < tags.length;
        }

        @Override
        public Entry<String, String> next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }

            Tag tag = new Tag(tags[currentIndex], tags[currentIndex + 1]);
            currentIndex += 2;
            return tag;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }

    }

    private static class TagEntrySet extends AbstractSet<Entry<String, String>> {
        private final String[] tags;

        public TagEntrySet(String[] tags) {
            super();
            this.tags = tags;
        }

        @Override
        public Iterator<Entry<String, String>> iterator() {
            return new TagEntryInterator(tags);
        }

        @Override
        public int size() {
            return tags.length / 2;
        }

    }

    /**
     * The tags field. This field is guarded using RCU.
     */
    private volatile String[] tags;

    /**
     * Creates a new, empty tag map.
     */
    public TagMap() {
        this(null);
    }

    /**
     * Creates a new read only tag map using a key/value/key/value/... array.
     * <p>
     * The array that is passed as parameter may not be modified after passing it to this map.
     * @param tags The tags array.
     */
    public TagMap(String[] tags) {
        if (tags == null || tags.length == 0) {
            this.tags = EMPTY_TAGS;
        } else {
            if (tags.length % 2 != 0) {
                throw new IllegalArgumentException("tags array length needs to be multiple of two.");
            }
            this.tags = tags;
        }
    }

    @Override
    public TagEntrySet entrySet() {
        return new TagEntrySet(tags);
    }

    @Override
    public boolean containsKey(Object key) {
        return indexOf(tags, key) >= 0;
    }

    @Override
    public String get(Object key) {
        String[] tags = this.tags;
        int index = indexOf(tags, key);
        return index < 0 ? null : tags[index + 1];
    }

    @Override
    public synchronized String put(String key, String value) {
        if (key == null) {
            throw new NullPointerException();
        }
        if (value == null) {
            throw new NullPointerException();
        }
        int index = indexOf(tags, key);
        int newTagArrayLength = tags.length;
        if (index < 0) {
            index = newTagArrayLength;
            newTagArrayLength += 2;
        }

        String[] newTags = Arrays.copyOf(tags, newTagArrayLength);
        String old = newTags[index + 1];
        newTags[index] = key;
        newTags[index + 1] = value;
        tags = newTags;
        return old;
    }

    @Override
    public synchronized String remove(Object key) {
        int index = indexOf(tags, key);
        if (index < 0) {
            return null;
        }
        String old = tags[index + 1];
        int newLength = tags.length - 2;
        if (newLength == 0) {
            tags = EMPTY_TAGS;
        } else {
            String[] newTags = new String[newLength];
            System.arraycopy(tags, 0, newTags, 0, index);
            System.arraycopy(tags, index + 2, newTags, index, newLength - index);
            tags = newTags;
        }

        return old;
    }

    @Override
    public synchronized void clear() {
        tags = EMPTY_TAGS;
    }

    private static int indexOf(String[] tags, Object key) {
        for (int i = 0; i < tags.length; i += 2) {
            if (tags[i].equals(key)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int size() {
        return tags.length / 2;
    }
}
