// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.util;

import java.awt.event.KeyEvent;

/**
 * Interface that is used to detect key pressing and releasing
 */
public interface KeyPressReleaseListener {
    /**
     * This is called when key press event is actually pressed
     * (no fake events while holding key)
     */
    void doKeyPressed(KeyEvent e);

    /**
     * This is called when key press event is actually released
     * (no fake events while holding key)
     */
    void doKeyReleased(KeyEvent e);
}
