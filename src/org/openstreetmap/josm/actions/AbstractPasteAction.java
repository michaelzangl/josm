// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.datatransfer.FlavorEvent;
import java.awt.datatransfer.FlavorListener;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.datatransfer.ClipboardUtils;
import org.openstreetmap.josm.gui.datatransfer.OsmTransferHandler;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * This is the base class for all actions that paste objects.
 * @author Michael Zangl
 * @since xxx
 */
public abstract class AbstractPasteAction extends JosmAction implements FlavorListener {

    protected final OsmTransferHandler transferHandler;

    /**
     * Create a new {@link AbstractPasteAction}
     * @param name See {@link JosmAction#JosmAction(String, String, String, Shortcut, boolean, String, boolean)}
     * @param iconName See {@link JosmAction#JosmAction(String, String, String, Shortcut, boolean, String, boolean)}
     * @param tooltip See {@link JosmAction#JosmAction(String, String, String, Shortcut, boolean, String, boolean)}
     * @param shortcut See {@link JosmAction#JosmAction(String, String, String, Shortcut, boolean, String, boolean)}
     * @param registerInToolbar See {@link JosmAction#JosmAction(String, String, String, Shortcut, boolean, String, boolean)}
     */
    public AbstractPasteAction(String name, String iconName, String tooltip, Shortcut shortcut,
            boolean registerInToolbar) {
        super(name, iconName, tooltip, shortcut, registerInToolbar);
        transferHandler = new OsmTransferHandler();
        ClipboardUtils.getClipboard().addFlavorListener(this);
    }

    /**
     * Compute the location the objects should be pasted at.
     * @param e The action event that triggered the paste
     * @return The paste position.
     */
    protected EastNorth computePastePosition(ActionEvent e) {
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
        return mPosition;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Transferable contents = ClipboardUtils.getClipboard().getContents(null);
        doPaste(e, contents);
    }

    protected void doPaste(ActionEvent e, Transferable contents) {
        transferHandler.pasteOn(Main.getLayerManager().getEditLayer(), computePastePosition(e), contents);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(getLayerManager().getEditDataSet() != null && transferHandler.isDataAvailable());
    }

    @Override
    public void flavorsChanged(FlavorEvent e) {
        updateEnabledState();
    }

}
