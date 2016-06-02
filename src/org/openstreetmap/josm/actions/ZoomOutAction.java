// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Zoom out map.
 * @since 770
 */
public final class ZoomOutAction extends JosmAction {

    /**
     * Constructs a new {@code ZoomOutAction}.
     */
    public ZoomOutAction() {
        super(tr("Zoom Out"), "dialogs/zoomout", tr("Zoom Out"),
                Shortcut.registerShortcut("view:zoomout", tr("View: {0}", tr("Zoom Out")), KeyEvent.VK_MINUS, Shortcut.DIRECT), true);
        putValue("help", ht("/Action/ZoomOut"));
        // make numpad - behave like -
        Main.registerActionShortcut(this,
            Shortcut.registerShortcut("view:zoomoutkeypad", tr("View: {0}", tr("Zoom Out (Keypad)")),
                KeyEvent.VK_SUBTRACT, Shortcut.DIRECT));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (!Main.isDisplayingMapView()) return;
        Main.map.mapView.zoomOut();
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(!Main.getLayerManager().getLayers().isEmpty());
    }
}
