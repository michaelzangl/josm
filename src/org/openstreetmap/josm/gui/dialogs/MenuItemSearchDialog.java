// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.MainMenu;
import org.openstreetmap.josm.gui.menu.search.SearchResult;
import org.openstreetmap.josm.gui.menu.search.SearchTask;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.SearchTextResultListPanel;
import org.openstreetmap.josm.tools.Shortcut;

public final class MenuItemSearchDialog extends ExtendedDialog {

    private final Selector selector;
    private static final MenuItemSearchDialog INSTANCE = new MenuItemSearchDialog(Main.main.menu);

    private MenuItemSearchDialog(MainMenu menu) {
        super(Main.parent, tr("Search menu items"), new String[]{tr("Select"), tr("Cancel")});
        this.selector = new Selector(menu);
        this.selector.setDblClickListener(e -> buttonAction(0, null));
        setContent(selector, false);
        setPreferredSize(new Dimension(600, 300));
    }

    /**
     * Returns the unique instance of {@code MenuItemSearchDialog}.
     *
     * @return the unique instance of {@code MenuItemSearchDialog}.
     */
    public static synchronized MenuItemSearchDialog getInstance() {
        return INSTANCE;
    }

    @Override
    public ExtendedDialog showDialog() {
        selector.init();
        super.showDialog();
        selector.clearSelection();
        return this;
    }

    @Override
    protected void buttonAction(int buttonIndex, ActionEvent evt) {
        super.buttonAction(buttonIndex, evt);
        if (buttonIndex == 0 && selector.getSelectedItem() != null && selector.getSelectedItem().isEnabled()) {
            selector.getSelectedItem().getAction().actionPerformed(evt);
        }
    }

    private static class Selector extends SearchTextResultListPanel<SearchResult> {

        private final MainMenu menu;
        private SearchTask task;

        Selector(MainMenu menu) {
            super();
            this.menu = menu;
            lsResult.setCellRenderer(new CellRenderer());
        }

        @Override
        public synchronized void init() {
            task = new SearchTask(results -> GuiHelper.runInEDT(() -> updateResults(results)));
            task.start();
            super.init();
        }

        public SearchResult getSelectedItem() {
            final SearchResult selected = lsResult.getSelectedValue();
            if (selected != null) {
                return selected;
            } else if (!lsResultModel.isEmpty()) {
                return lsResultModel.getElementAt(0);
            } else {
                return null;
            }
        }

        @Override
        protected void filterItems() {
            task.search(edSearchText.getText());
        }

        private void updateResults(List<SearchResult> results) {
            lsResultModel.setItems(results);
        }
    }

    private static class CellRenderer implements ListCellRenderer<SearchResult> {

        private final DefaultListCellRenderer def = new DefaultListCellRenderer();

        @Override
        public Component getListCellRendererComponent(JList<? extends SearchResult> list, SearchResult value, int index,
                                                      boolean isSelected, boolean cellHasFocus) {


            final JLabel label = (JLabel) def.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            label.setText(value.getCommandName());
            label.setIcon(value.getIcon());
            label.setEnabled(value.isEnabled());
//            final JMenuItem item = new JMenuItem(value.getText());
//            item.setAction(value.getAction());
            if (isSelected) {
                label.setBackground(list.getSelectionBackground());
                label.setForeground(list.getSelectionForeground());
            } else {
                label.setBackground(list.getBackground());
                label.setForeground(list.getForeground());
            }
            return label;
        }
    }

    public static class Action extends JosmAction {

        public Action() {
            super(tr("Search menu items"), "dialogs/search", null,
                    Shortcut.registerShortcut("help:search-items", "Search menu items", KeyEvent.VK_SPACE, Shortcut.CTRL),
                    true, "dialogs/search-items", false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            MenuItemSearchDialog.getInstance().showDialog();
        }
    }
}
