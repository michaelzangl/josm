// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.actions;

import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Collections;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Deletes the active layer.
 */
public final class DeleteLayerAction extends JosmAction {

    /**
     * Constructs a {@link DeleteLayerAction} which will delete the active layer.
     */
    public DeleteLayerAction() {
        super(tr("Delete Layer"), "dialogs/delete", tr("Delete the active layer. Does not delete the associated file."),
                Shortcut.registerShortcut("system:deletelayer", tr("File: {0}", tr("Delete Layer")), KeyEvent.VK_F4, Shortcut.CTRL),
                true, "delete-layer", true);
        putValue("help", ht("/Action/DeleteLayer"));
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Layer activeLayer = Main.main.getActiveLayer();
        if (activeLayer == null) {
            return;
        }
        if (!Main.saveUnsavedModifications(Collections.singletonList(activeLayer), false)) {
            return;
        }
        Main.main.removeLayer(activeLayer);
    }

    @Override
    protected void updateEnabledState() {
        setEnabled(Main.getLayerManager().getActiveLayer() != null);
    }
}
