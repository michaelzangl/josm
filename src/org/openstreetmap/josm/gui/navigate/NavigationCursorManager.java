// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.navigate;

import java.awt.Component;
import java.awt.Cursor;
import java.util.Iterator;
import java.util.LinkedList;

/**
 * This class manages multiple cursors for a single component.
 * @author michael
 *
 */
public class NavigationCursorManager {

    private static class CursorInfo {
        private final Cursor cursor;
        private final Object object;
        public CursorInfo(Cursor c, Object o) {
            cursor = c;
            object = o;
        }
    }

    private final LinkedList<CursorInfo> cursors = new LinkedList<>();
    private Component forComponent;

    public NavigationCursorManager(Component forComponent) {
        this.forComponent = forComponent;
    }
    /**
     * Set new cursor.
     */
    public synchronized void setNewCursor(Cursor cursor, Object reference) {
        if (!cursors.isEmpty()) {
            CursorInfo l = cursors.getLast();
            if(l != null && l.cursor == cursor && l.object == reference)
                return;
            stripCursors(reference);
        }
        cursors.add(new CursorInfo(cursor, reference));
        forComponent.setCursor(cursor);
    }

    /**
     * Remove the new cursor and reset to previous
     */
    public synchronized void resetCursor(Object reference) {
        if (cursors.isEmpty()) {
            forComponent.setCursor(null);
            return;
        }
        CursorInfo l = cursors.getLast();
        stripCursors(reference);
        if (l != null && l.object == reference) {
            if (cursors.isEmpty()) {
                forComponent.setCursor(null);
            } else {
                forComponent.setCursor(cursors.getLast().cursor);
            }
        }
    }

    private void stripCursors(Object reference) {
        for (Iterator<CursorInfo> iterator = cursors.iterator(); iterator.hasNext();) {
            CursorInfo i = iterator.next();
            if(i.object == reference) {
                iterator.remove();
            }
        }
    }

}
