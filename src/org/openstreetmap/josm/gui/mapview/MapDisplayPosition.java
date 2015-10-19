// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mapview;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * Defines the position of a map display on the window/screen.
 * @author michael
 *
 */
public class MapDisplayPosition {
    private final int x, y, windowX, windowY;

    private final int width, height;

    public MapDisplayPosition(JComponent comp) {
        Window window = comp == null ? null : SwingUtilities.getWindowAncestor(comp);
        Point windowPos = getScreenLocSafe(window, new Point(0, 0));
        Point myLocation = getScreenLocSafe(comp, windowPos);
        Dimension size = comp == null ? new Dimension() : comp.getSize();
        this.x = myLocation.x - windowPos.x;
        this.y = myLocation.y - windowPos.y;
        this.width = Math.max(1, size.width);
        this.height = Math.max(1, size.height);
        this.windowX = windowPos.x;
        this.windowY = windowPos.y;
    }

    private Point getScreenLocSafe(Component comp, Point fallback) {
        if (comp == null || !comp.isShowing()) {
            return fallback;
        } else {
            return comp.getLocationOnScreen();
        }
    }

    public int getHeight() {
        return height;
    }

    public int getWidth() {
        return width;
    }

    public int getWindowX() {
        return windowX;
    }

    public int getWindowY() {
        return windowY;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }
}
