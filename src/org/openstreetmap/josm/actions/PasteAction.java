// License: GPL. For details, see LICENSE file.
// Author: David Earl
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.osm.PrimitiveDeepCopy;
import org.openstreetmap.josm.data.osm.PrimitiveDeepCopy.PasteBufferChangedListener;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Paste OSM primitives from clipboard to the current edit layer.
 * @since 404
 */
public final class PasteAction extends JosmAction implements PasteBufferChangedListener {

    private final OsmTransferHandler transferHandler;

    /**
     * Constructs a new {@code PasteAction}.
     */
    public PasteAction() {
        super(tr("Paste"), "paste", tr("Paste contents of paste buffer."),
                Shortcut.registerShortcut("system:paste", tr("Edit: {0}", tr("Paste")), KeyEvent.VK_V, Shortcut.CTRL), true);
        putValue("help", ht("/Action/Paste"));
        // CUA shortcut for paste (https://en.wikipedia.org/wiki/IBM_Common_User_Access#Description)
        Main.registerActionShortcut(this,
                Shortcut.registerShortcut("system:paste:cua", tr("Edit: {0}", tr("Paste")), KeyEvent.VK_INSERT, Shortcut.SHIFT));
        Main.pasteBuffer.addPasteBufferChangedListener(this);
        transferHandler = new OsmTransferHandler();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        // default to paste in center of map (pasted via menu or cursor not in MapView)
        EastNorth mPosition = Main.map.mapView.getCenter();
        // We previously checked for modifier to know if the action has been trigerred via shortcut or via menu
        // But this does not work if the shortcut is changed to a single key (see #9055)
        // Observed behaviour: getActionCommand() returns Action.NAME when triggered via menu, but shortcut text when triggered with it
        if (e != null && !getValue(NAME).equals(e.getActionCommand())) {
            final Point mp = MouseInfo.getPointerInfo().getLocation();
            final Point tl = Main.map.mapView.getLocationOnScreen();
            final Point pos = new Point(mp.x-tl.x, mp.y-tl.y);
            if (Main.map.mapView.contains(pos)) {
                mPosition = Main.map.mapView.getEastNorth(pos.x, pos.y);
            }
        }

        transferHandler.pasteOn(Main.getLayerManager().getEditLayer(), mPosition);
    }

    @Override
    protected void updateEnabledState() {
        if (getCurrentDataSet() == null || Main.pasteBuffer == null) {
            setEnabled(false);
            return;
        }
        setEnabled(!Main.pasteBuffer.isEmpty());
    }

    @Override
    public void pasteBufferChanged(PrimitiveDeepCopy pasteBuffer) {
        updateEnabledState();
    }
}
