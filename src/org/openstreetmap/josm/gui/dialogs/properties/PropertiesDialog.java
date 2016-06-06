// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs.properties;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.RowSorterEvent;
import javax.swing.event.RowSorterListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.relation.DownloadMembersAction;
import org.openstreetmap.josm.actions.relation.DownloadSelectedIncompleteMembersAction;
import org.openstreetmap.josm.actions.relation.SelectInRelationListAction;
import org.openstreetmap.josm.actions.relation.SelectMembersAction;
import org.openstreetmap.josm.actions.relation.SelectRelationAction;
import org.openstreetmap.josm.actions.search.SearchAction.SearchSetting;
import org.openstreetmap.josm.actions.search.SearchCompiler;
import org.openstreetmap.josm.command.ChangeCommand;
import org.openstreetmap.josm.command.ChangePropertyCommand;
import org.openstreetmap.josm.command.Command;
import org.openstreetmap.josm.data.Preferences.PreferenceChangeEvent;
import org.openstreetmap.josm.data.SelectionChangedListener;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.RelationMember;
import org.openstreetmap.josm.data.osm.Tag;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent;
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager;
import org.openstreetmap.josm.data.osm.event.DatasetEventManager.FireMode;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.preferences.StringProperty;
import org.openstreetmap.josm.gui.ConditionalOptionPaneUtil;
import org.openstreetmap.josm.gui.DefaultNameFormatter;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.PopupMenuHandler;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.dialogs.relation.RelationEditor;
import org.openstreetmap.josm.gui.help.HelpUtil;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPreset;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetHandler;
import org.openstreetmap.josm.gui.tagging.presets.TaggingPresetType;
import org.openstreetmap.josm.gui.util.HighlightHelper;
import org.openstreetmap.josm.gui.widgets.CompileSearchTextDecorator;
import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.tools.AlphanumComparator;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.LanguageInfo;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.openstreetmap.josm.tools.Predicates;
import org.openstreetmap.josm.tools.Shortcut;
import org.openstreetmap.josm.tools.Utils;

/**
 * This dialog displays the tags of the current selected primitives.
 *
 * If no object is selected, the dialog list is empty.
 * If only one is selected, all tags of this object are selected.
 * If more than one object are selected, the sum of all tags are displayed. If the
 * different objects share the same tag, the shared value is displayed. If they have
 * different values, all of them are put in a combo box and the string "&lt;different&gt;"
 * is displayed in italic.
 *
 * Below the list, the user can click on an add, modify and delete tag button to
 * edit the table selection value.
 *
 * The command is applied to all selected entries.
 *
 * @author imi
 */
public class PropertiesDialog extends ToggleDialog
implements SelectionChangedListener, ActiveLayerChangeListener, DataSetListenerAdapter.Listener {

    /**
     * hook for roadsigns plugin to display a small button in the upper right corner of this dialog
     */
    public static final JPanel pluginHook = new JPanel();

    /**
     * The tag data of selected objects.
     */
    private final ReadOnlyTableModel tagData = new ReadOnlyTableModel();
    private final PropertiesCellRenderer cellRenderer = new PropertiesCellRenderer();
    private final transient TableRowSorter<ReadOnlyTableModel> tagRowSorter = new TableRowSorter<>(tagData);
    private final JosmTextField tagTableFilter;

    /**
     * The membership data of selected objects.
     */
    private final DefaultTableModel membershipData = new ReadOnlyTableModel();

    /**
     * The tags table.
     */
    private final JTable tagTable = new JTable(tagData);

    /**
     * The membership table.
     */
    private final JTable membershipTable = new JTable(membershipData);

    /** JPanel containing both previous tables */
    private final JPanel bothTables = new JPanel(new GridBagLayout());

    // Popup menus
    private final JPopupMenu tagMenu = new JPopupMenu();
    private final JPopupMenu membershipMenu = new JPopupMenu();
    private final JPopupMenu blankSpaceMenu = new JPopupMenu();

    // Popup menu handlers
    private final transient PopupMenuHandler tagMenuHandler = new PopupMenuHandler(tagMenu);
    private final transient PopupMenuHandler membershipMenuHandler = new PopupMenuHandler(membershipMenu);
    private final transient PopupMenuHandler blankSpaceMenuHandler = new PopupMenuHandler(blankSpaceMenu);

    private final transient Map<String, Map<String, Integer>> valueCount = new TreeMap<>();
    /**
     * This sub-object is responsible for all adding and editing of tags
     */
    private final transient TagEditHelper editHelper = new TagEditHelper(tagTable, tagData, valueCount);

    private final transient DataSetListenerAdapter dataChangedAdapter = new DataSetListenerAdapter(this);
    private final HelpAction helpAction = new HelpAction();
    private final TaginfoAction taginfoAction = new TaginfoAction();
    private final PasteValueAction pasteValueAction = new PasteValueAction();
    private final CopyValueAction copyValueAction = new CopyValueAction();
    private final CopyKeyValueAction copyKeyValueAction = new CopyKeyValueAction();
    private final CopyAllKeyValueAction copyAllKeyValueAction = new CopyAllKeyValueAction();
    private final SearchAction searchActionSame = new SearchAction(true);
    private final SearchAction searchActionAny = new SearchAction(false);
    private final AddAction addAction = new AddAction();
    private final EditAction editAction = new EditAction();
    private final DeleteAction deleteAction = new DeleteAction();
    private final JosmAction[] josmActions = new JosmAction[]{addAction, editAction, deleteAction};

    // relation actions
    private final SelectInRelationListAction setRelationSelectionAction = new SelectInRelationListAction();
    private final SelectRelationAction selectRelationAction = new SelectRelationAction(false);
    private final SelectRelationAction addRelationToSelectionAction = new SelectRelationAction(true);

    private final DownloadMembersAction downloadMembersAction = new DownloadMembersAction();
    private final DownloadSelectedIncompleteMembersAction downloadSelectedIncompleteMembersAction =
            new DownloadSelectedIncompleteMembersAction();

    private final SelectMembersAction selectMembersAction = new SelectMembersAction(false);
    private final SelectMembersAction addMembersToSelectionAction = new SelectMembersAction(true);

    private final transient HighlightHelper highlightHelper = new HighlightHelper();

    /**
     * The Add button (needed to be able to disable it)
     */
    private final SideButton btnAdd = new SideButton(addAction);
    /**
     * The Edit button (needed to be able to disable it)
     */
    private final SideButton btnEdit = new SideButton(editAction);
    /**
     * The Delete button (needed to be able to disable it)
     */
    private final SideButton btnDel = new SideButton(deleteAction);
    /**
     * Matching preset display class
     */
    private final PresetListPanel presets = new PresetListPanel();

    /**
     * Text to display when nothing selected.
     */
    private final JLabel selectSth = new JLabel("<html><p>"
            + tr("Select objects for which to change tags.") + "</p></html>");

    private final transient TaggingPresetHandler presetHandler = new TaggingPresetHandler() {
        @Override
        public void updateTags(List<Tag> tags) {
            Command command = TaggingPreset.createCommand(getSelection(), tags);
            if (command != null) {
                Main.main.undoRedo.add(command);
            }
        }

        @Override
        public Collection<OsmPrimitive> getSelection() {
            return Main.main == null ? Collections.<OsmPrimitive>emptyList() : Main.main.getInProgressSelection();
        }
    };

    /**
     * Create a new PropertiesDialog
     */
    public PropertiesDialog() {
        super(tr("Tags/Memberships"), "propertiesdialog", tr("Tags for selected objects."),
                Shortcut.registerShortcut("subwindow:properties", tr("Toggle: {0}", tr("Tags/Memberships")), KeyEvent.VK_P,
                        Shortcut.ALT_SHIFT), 150, true);

        HelpUtil.setHelpContext(this, HelpUtil.ht("/Dialog/TagsMembership"));

        setupTagsMenu();
        buildTagsTable();

        setupMembershipMenu();
        buildMembershipTable();

        tagTableFilter = setupFilter();

        // combine both tables and wrap them in a scrollPane
        boolean top = Main.pref.getBoolean("properties.presets.top", true);
        if (top) {
            bothTables.add(presets, GBC.std().fill(GBC.HORIZONTAL).insets(5, 2, 5, 2).anchor(GBC.NORTHWEST));
            double epsilon = Double.MIN_VALUE; // need to set a weight or else anchor value is ignored
            bothTables.add(pluginHook, GBC.eol().insets(0, 1, 1, 1).anchor(GBC.NORTHEAST).weight(epsilon, epsilon));
        }
        bothTables.add(selectSth, GBC.eol().fill().insets(10, 10, 10, 10));
        bothTables.add(tagTableFilter, GBC.eol().fill(GBC.HORIZONTAL));
        bothTables.add(tagTable.getTableHeader(), GBC.eol().fill(GBC.HORIZONTAL));
        bothTables.add(tagTable, GBC.eol().fill(GBC.BOTH));
        bothTables.add(membershipTable.getTableHeader(), GBC.eol().fill(GBC.HORIZONTAL));
        bothTables.add(membershipTable, GBC.eol().fill(GBC.BOTH));
        if (!top) {
            bothTables.add(presets, GBC.eol().fill(GBC.HORIZONTAL).insets(5, 2, 5, 2));
        }

        setupBlankSpaceMenu();
        setupKeyboardShortcuts();

        // Let the actions know when selection in the tables change
        tagTable.getSelectionModel().addListSelectionListener(editAction);
        membershipTable.getSelectionModel().addListSelectionListener(editAction);
        tagTable.getSelectionModel().addListSelectionListener(deleteAction);
        membershipTable.getSelectionModel().addListSelectionListener(deleteAction);

        JScrollPane scrollPane = (JScrollPane) createLayout(bothTables, true,
                Arrays.asList(this.btnAdd, this.btnEdit, this.btnDel));

        MouseClickWatch mouseClickWatch = new MouseClickWatch();
        tagTable.addMouseListener(mouseClickWatch);
        membershipTable.addMouseListener(mouseClickWatch);
        scrollPane.addMouseListener(mouseClickWatch);

        selectSth.setPreferredSize(scrollPane.getSize());
        presets.setSize(scrollPane.getSize());

        editHelper.loadTagsIfNeeded();

        Main.pref.addPreferenceChangeListener(this);
    }

    private void buildTagsTable() {
        // setting up the tags table
        tagData.setColumnIdentifiers(new String[]{tr("Key"), tr("Value")});
        tagTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        tagTable.getTableHeader().setReorderingAllowed(false);

        tagTable.getColumnModel().getColumn(0).setCellRenderer(cellRenderer);
        tagTable.getColumnModel().getColumn(1).setCellRenderer(cellRenderer);
        tagTable.setRowSorter(tagRowSorter);

        final RemoveHiddenSelection removeHiddenSelection = new RemoveHiddenSelection();
        tagTable.getSelectionModel().addListSelectionListener(removeHiddenSelection);
        tagRowSorter.addRowSorterListener(removeHiddenSelection);
        tagRowSorter.setComparator(0, AlphanumComparator.getInstance());
        tagRowSorter.setComparator(1, new Comparator<Object>() {
            @Override
            public int compare(Object o1, Object o2) {
                if (o1 instanceof Map && o2 instanceof Map) {
                    final String v1 = ((Map) o1).size() == 1 ? (String) ((Map) o1).keySet().iterator().next() : tr("<different>");
                    final String v2 = ((Map) o2).size() == 1 ? (String) ((Map) o2).keySet().iterator().next() : tr("<different>");
                    return AlphanumComparator.getInstance().compare(v1, v2);
                } else {
                    return AlphanumComparator.getInstance().compare(String.valueOf(o1), String.valueOf(o2));
                }
            }
        });
    }

    private void buildMembershipTable() {
        membershipData.setColumnIdentifiers(new String[]{tr("Member Of"), tr("Role"), tr("Position")});
        membershipTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);

        TableColumnModel mod = membershipTable.getColumnModel();
        membershipTable.getTableHeader().setReorderingAllowed(false);
        mod.getColumn(0).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                if (value == null)
                    return this;
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    Relation r = (Relation) value;
                    label.setText(r.getDisplayName(DefaultNameFormatter.getInstance()));
                    if (r.isDisabledAndHidden()) {
                        label.setFont(label.getFont().deriveFont(Font.ITALIC));
                    }
                }
                return c;
            }
        });

        mod.getColumn(1).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                if (value == null)
                    return this;
                Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                boolean isDisabledAndHidden = ((Relation) table.getValueAt(row, 0)).isDisabledAndHidden();
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setText(((MemberInfo) value).getRoleString());
                    if (isDisabledAndHidden) {
                        label.setFont(label.getFont().deriveFont(Font.ITALIC));
                    }
                }
                return c;
            }
        });

        mod.getColumn(2).setCellRenderer(new DefaultTableCellRenderer() {
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, false, row, column);
                boolean isDisabledAndHidden = ((Relation) table.getValueAt(row, 0)).isDisabledAndHidden();
                if (c instanceof JLabel) {
                    JLabel label = (JLabel) c;
                    label.setText(((MemberInfo) table.getValueAt(row, 1)).getPositionString());
                    if (isDisabledAndHidden) {
                        label.setFont(label.getFont().deriveFont(Font.ITALIC));
                    }
                }
                return c;
            }
        });
        mod.getColumn(2).setPreferredWidth(20);
        mod.getColumn(1).setPreferredWidth(40);
        mod.getColumn(0).setPreferredWidth(200);
    }

    /**
     * Creates the popup menu @field blankSpaceMenu and its launcher on main panel.
     */
    private void setupBlankSpaceMenu() {
        if (Main.pref.getBoolean("properties.menu.add_edit_delete", true)) {
            blankSpaceMenuHandler.addAction(addAction);
            PopupMenuLauncher launcher = new PopupMenuLauncher(blankSpaceMenu) {
                @Override
                protected boolean checkSelection(Component component, Point p) {
                    if (component instanceof JTable) {
                        return ((JTable) component).rowAtPoint(p) == -1;
                    }
                    return true;
                }
            };
            bothTables.addMouseListener(launcher);
            tagTable.addMouseListener(launcher);
        }
    }

    /**
     * Creates the popup menu @field membershipMenu and its launcher on membership table.
     */
    private void setupMembershipMenu() {
        // setting up the membership table
        if (Main.pref.getBoolean("properties.menu.add_edit_delete", true)) {
            membershipMenuHandler.addAction(editAction);
            membershipMenuHandler.addAction(deleteAction);
            membershipMenu.addSeparator();
        }
        membershipMenuHandler.addAction(setRelationSelectionAction);
        membershipMenuHandler.addAction(selectRelationAction);
        membershipMenuHandler.addAction(addRelationToSelectionAction);
        membershipMenuHandler.addAction(selectMembersAction);
        membershipMenuHandler.addAction(addMembersToSelectionAction);
        membershipMenu.addSeparator();
        membershipMenuHandler.addAction(downloadMembersAction);
        membershipMenuHandler.addAction(downloadSelectedIncompleteMembersAction);
        membershipMenu.addSeparator();
        membershipMenu.add(helpAction);
        membershipMenu.add(taginfoAction);

        membershipTable.addMouseListener(new PopupMenuLauncher(membershipMenu) {
            @Override
            protected int checkTableSelection(JTable table, Point p) {
                int row = super.checkTableSelection(table, p);
                List<Relation> rels = new ArrayList<>();
                for (int i: table.getSelectedRows()) {
                    rels.add((Relation) table.getValueAt(i, 0));
                }
                membershipMenuHandler.setPrimitives(rels);
                return row;
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                //update highlights
                if (Main.isDisplayingMapView()) {
                    int row = membershipTable.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        if (highlightHelper.highlightOnly((Relation) membershipTable.getValueAt(row, 0))) {
                            Main.map.mapView.repaint();
                        }
                    }
                }
                super.mouseClicked(e);
            }

            @Override
            public void mouseExited(MouseEvent me) {
                highlightHelper.clear();
            }
        });
    }

    /**
     * Creates the popup menu @field tagMenu and its launcher on tag table.
     */
    private void setupTagsMenu() {
        if (Main.pref.getBoolean("properties.menu.add_edit_delete", true)) {
            tagMenu.add(addAction);
            tagMenu.add(editAction);
            tagMenu.add(deleteAction);
            tagMenu.addSeparator();
        }
        tagMenu.add(pasteValueAction);
        tagMenu.add(copyValueAction);
        tagMenu.add(copyKeyValueAction);
        tagMenu.add(copyAllKeyValueAction);
        tagMenu.addSeparator();
        tagMenu.add(searchActionAny);
        tagMenu.add(searchActionSame);
        tagMenu.addSeparator();
        tagMenu.add(helpAction);
        tagMenu.add(taginfoAction);
        tagTable.addMouseListener(new PopupMenuLauncher(tagMenu));
    }

    public void setFilter(final SearchCompiler.Match filter) {
        this.tagRowSorter.setRowFilter(new SearchBasedRowFilter(filter));
    }

    /**
     * Assigns all needed keys like Enter and Spacebar to most important actions.
     */
    private void setupKeyboardShortcuts() {

        // ENTER = editAction, open "edit" dialog
        tagTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "onTableEnter");
        tagTable.getActionMap().put("onTableEnter", editAction);
        membershipTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "onTableEnter");
        membershipTable.getActionMap().put("onTableEnter", editAction);

        // INSERT button = addAction, open "add tag" dialog
        tagTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_INSERT, 0), "onTableInsert");
        tagTable.getActionMap().put("onTableInsert", addAction);

        // unassign some standard shortcuts for JTable to allow upload / download / image browsing
        InputMapUtils.unassignCtrlShiftUpDown(tagTable, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        InputMapUtils.unassignPageUpDown(tagTable, JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);

        // unassign some standard shortcuts for correct copy-pasting, fix #8508
        tagTable.setTransferHandler(null);

        tagTable.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_MASK), "onCopy");
        tagTable.getActionMap().put("onCopy", copyKeyValueAction);

        // allow using enter to add tags for all look&feel configurations
        InputMapUtils.enableEnter(this.btnAdd);

        // DEL button = deleteAction
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete"
                );
        getActionMap().put("delete", deleteAction);

        // F1 button = custom help action
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                helpAction.getKeyStroke(), "onHelp");
        getActionMap().put("onHelp", helpAction);
    }

    private JosmTextField setupFilter() {
        final JosmTextField f = new DisableShortcutsOnFocusGainedTextField();
        f.setToolTipText(tr("Tag filter"));
        final CompileSearchTextDecorator decorator = CompileSearchTextDecorator.decorate(f);
        f.addPropertyChangeListener("filter", new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent evt) {
                setFilter(decorator.getMatch());
            }
        });
        return f;
    }

    /**
     * This simply fires up an {@link RelationEditor} for the relation shown; everything else
     * is the editor's business.
     *
     * @param row position
     */
    private void editMembership(int row) {
        Relation relation = (Relation) membershipData.getValueAt(row, 0);
        Main.map.relationListDialog.selectRelation(relation);
        RelationEditor.getEditor(
                Main.main.getEditLayer(),
                relation,
                ((MemberInfo) membershipData.getValueAt(row, 1)).role
        ).setVisible(true);
    }

    private static int findViewRow(JTable table, TableModel model, Object value) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getValueAt(i, 0).equals(value))
                return table.convertRowIndexToView(i);
        }
        return -1;
    }

    /**
     * Update selection status, call @{link #selectionChanged} function.
     */
    private void updateSelection() {
        // Parameter is ignored in this class
        selectionChanged(null);
    }

    @Override
    public void showNotify() {
        DatasetEventManager.getInstance().addDatasetListener(dataChangedAdapter, FireMode.IN_EDT_CONSOLIDATED);
        SelectionEventManager.getInstance().addSelectionListener(this, FireMode.IN_EDT_CONSOLIDATED);
        Main.getLayerManager().addActiveLayerChangeListener(this);
        for (JosmAction action : josmActions) {
            Main.registerActionShortcut(action);
        }
        updateSelection();
    }

    @Override
    public void hideNotify() {
        DatasetEventManager.getInstance().removeDatasetListener(dataChangedAdapter);
        SelectionEventManager.getInstance().removeSelectionListener(this);
        Main.getLayerManager().removeActiveLayerChangeListener(this);
        for (JosmAction action : josmActions) {
            Main.unregisterActionShortcut(action);
        }
    }

    @Override
    public void setVisible(boolean b) {
        super.setVisible(b);
        if (b && Main.main.getCurrentDataSet() != null) {
            updateSelection();
        }
    }

    @Override
    public void destroy() {
        super.destroy();
        Main.pref.removePreferenceChangeListener(this);
        for (JosmAction action : josmActions) {
            action.destroy();
        }
        Container parent = pluginHook.getParent();
        if (parent != null) {
            parent.remove(pluginHook);
        }
    }

    @Override
    public void selectionChanged(Collection<? extends OsmPrimitive> newSelection) {
        if (!isVisible())
            return;
        if (tagTable == null)
            return; // selection changed may be received in base class constructor before init
        if (tagTable.getCellEditor() != null) {
            tagTable.getCellEditor().cancelCellEditing();
        }

        // Ignore parameter as we do not want to operate always on real selection here, especially in draw mode
        Collection<OsmPrimitive> newSel = Main.main.getInProgressSelection();
        if (newSel == null) {
            newSel = Collections.<OsmPrimitive>emptyList();
        }

        String selectedTag;
        Relation selectedRelation = null;
        selectedTag = editHelper.getChangedKey(); // select last added or last edited key by default
        if (selectedTag == null && tagTable.getSelectedRowCount() == 1) {
            selectedTag = editHelper.getDataKey(tagTable.getSelectedRow());
        }
        if (membershipTable.getSelectedRowCount() == 1) {
            selectedRelation = (Relation) membershipData.getValueAt(membershipTable.getSelectedRow(), 0);
        }

        // re-load tag data
        tagData.setRowCount(0);

        final boolean displayDiscardableKeys = Main.pref.getBoolean("display.discardable-keys", false);
        final Map<String, Integer> keyCount = new HashMap<>();
        final Map<String, String> tags = new HashMap<>();
        valueCount.clear();
        Set<TaggingPresetType> types = EnumSet.noneOf(TaggingPresetType.class);
        for (OsmPrimitive osm : newSel) {
            types.add(TaggingPresetType.forPrimitive(osm));
            for (String key : osm.keySet()) {
                if (displayDiscardableKeys || !OsmPrimitive.getDiscardableKeys().contains(key)) {
                    String value = osm.get(key);
                    keyCount.put(key, keyCount.containsKey(key) ? keyCount.get(key) + 1 : 1);
                    if (valueCount.containsKey(key)) {
                        Map<String, Integer> v = valueCount.get(key);
                        v.put(value, v.containsKey(value) ? v.get(value) + 1 : 1);
                    } else {
                        Map<String, Integer> v = new TreeMap<>();
                        v.put(value, 1);
                        valueCount.put(key, v);
                    }
                }
            }
        }
        for (Entry<String, Map<String, Integer>> e : valueCount.entrySet()) {
            int count = 0;
            for (Entry<String, Integer> e1 : e.getValue().entrySet()) {
                count += e1.getValue();
            }
            if (count < newSel.size()) {
                e.getValue().put("", newSel.size() - count);
            }
            tagData.addRow(new Object[]{e.getKey(), e.getValue()});
            tags.put(e.getKey(), e.getValue().size() == 1
                    ? e.getValue().keySet().iterator().next() : tr("<different>"));
        }

        membershipData.setRowCount(0);

        Map<Relation, MemberInfo> roles = new HashMap<>();
        for (OsmPrimitive primitive: newSel) {
            for (OsmPrimitive ref: primitive.getReferrers(true)) {
                if (ref instanceof Relation && !ref.isIncomplete() && !ref.isDeleted()) {
                    Relation r = (Relation) ref;
                    MemberInfo mi = roles.get(r);
                    if (mi == null) {
                        mi = new MemberInfo(newSel);
                    }
                    roles.put(r, mi);
                    int i = 1;
                    for (RelationMember m : r.getMembers()) {
                        if (m.getMember() == primitive) {
                            mi.add(m, i);
                        }
                        ++i;
                    }
                }
            }
        }

        List<Relation> sortedRelations = new ArrayList<>(roles.keySet());
        Collections.sort(sortedRelations, new Comparator<Relation>() {
            @Override
            public int compare(Relation o1, Relation o2) {
                int comp = Boolean.compare(o1.isDisabledAndHidden(), o2.isDisabledAndHidden());
                return comp != 0 ? comp : DefaultNameFormatter.getInstance().getRelationComparator().compare(o1, o2);
            }
        });

        for (Relation r: sortedRelations) {
            membershipData.addRow(new Object[]{r, roles.get(r)});
        }

        presets.updatePresets(types, tags, presetHandler);

        membershipTable.getTableHeader().setVisible(membershipData.getRowCount() > 0);
        membershipTable.setVisible(membershipData.getRowCount() > 0);

        boolean hasSelection = !newSel.isEmpty();
        boolean hasTags = hasSelection && tagData.getRowCount() > 0;
        boolean hasMemberships = hasSelection && membershipData.getRowCount() > 0;
        addAction.setEnabled(hasSelection);
        editAction.setEnabled(hasTags || hasMemberships);
        deleteAction.setEnabled(hasTags || hasMemberships);
        tagTable.setVisible(hasTags);
        tagTable.getTableHeader().setVisible(hasTags);
        tagTableFilter.setVisible(hasTags);
        selectSth.setVisible(!hasSelection);
        pluginHook.setVisible(hasSelection);

        int selectedIndex;
        if (selectedTag != null && (selectedIndex = findViewRow(tagTable, tagData, selectedTag)) != -1) {
            tagTable.changeSelection(selectedIndex, 0, false, false);
        } else if (selectedRelation != null && (selectedIndex = findViewRow(membershipTable, membershipData, selectedRelation)) != -1) {
            membershipTable.changeSelection(selectedIndex, 0, false, false);
        } else if (hasTags) {
            tagTable.changeSelection(0, 0, false, false);
        } else if (hasMemberships) {
            membershipTable.changeSelection(0, 0, false, false);
        }

        if (tagData.getRowCount() != 0 || membershipData.getRowCount() != 0) {
            if (newSel.size() > 1) {
                setTitle(tr("Objects: {2} / Tags: {0} / Memberships: {1}",
                    tagData.getRowCount(), membershipData.getRowCount(), newSel.size()));
            } else {
                setTitle(tr("Tags: {0} / Memberships: {1}",
                    tagData.getRowCount(), membershipData.getRowCount()));
            }
        } else {
            setTitle(tr("Tags / Memberships"));
        }
    }

    /* ---------------------------------------------------------------------------------- */
    /* ActiveLayerChangeListener                                                          */
    /* ---------------------------------------------------------------------------------- */
    @Override
    public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
        if (e.getSource().getEditLayer() != null) {
            editHelper.saveTagsIfNeeded();
        }
        // it is time to save history of tags
        updateSelection();
    }

    @Override
    public void processDatasetEvent(AbstractDatasetChangedEvent event) {
        updateSelection();
    }

    /**
     * Replies the tag popup menu handler.
     * @return The tag popup menu handler
     */
    public PopupMenuHandler getPropertyPopupMenuHandler() {
        return tagMenuHandler;
    }

    /**
     * Returns the selected tag.
     * @return The current selected tag
     */
    public Tag getSelectedProperty() {
        int row = tagTable.getSelectedRow();
        if (row == -1) return null;
        Map<String, Integer> map = editHelper.getDataValues(row);
        return new Tag(
                editHelper.getDataKey(row),
                map.size() > 1 ? "" : map.keySet().iterator().next());
    }

    /**
     * Replies the membership popup menu handler.
     * @return The membership popup menu handler
     */
    public PopupMenuHandler getMembershipPopupMenuHandler() {
        return membershipMenuHandler;
    }

    /**
     * Returns the selected relation membership.
     * @return The current selected relation membership
     */
    public IRelation getSelectedMembershipRelation() {
        int row = membershipTable.getSelectedRow();
        return row > -1 ? (IRelation) membershipData.getValueAt(row, 0) : null;
    }

    /**
     * Adds a custom table cell renderer to render cells of the tags table.
     *
     * If the renderer is not capable performing a {@link TableCellRenderer#getTableCellRendererComponent},
     * it should return {@code null} to fall back to the
     * {@link PropertiesCellRenderer#getTableCellRendererComponent default implementation}.
     * @param renderer the renderer to add
     * @since 9149
     */
    public void addCustomPropertiesCellRenderer(TableCellRenderer renderer) {
        cellRenderer.addCustomRenderer(renderer);
    }

    /**
     * Removes a custom table cell renderer.
     * @param renderer the renderer to remove
     * @since 9149
     */
    public void removeCustomPropertiesCellRenderer(TableCellRenderer renderer) {
        cellRenderer.removeCustomRenderer(renderer);
    }

    /**
     * Class that watches for mouse clicks
     * @author imi
     */
    public class MouseClickWatch extends MouseAdapter {
        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() < 2) {
                // single click, clear selection in other table not clicked in
                if (e.getSource() == tagTable) {
                    membershipTable.clearSelection();
                } else if (e.getSource() == membershipTable) {
                    tagTable.clearSelection();
                }
            } else if (e.getSource() == tagTable) {
                // double click, edit or add tag
                int row = tagTable.rowAtPoint(e.getPoint());
                if (row > -1) {
                    boolean focusOnKey = tagTable.columnAtPoint(e.getPoint()) == 0;
                    editHelper.editTag(row, focusOnKey);
                } else {
                    editHelper.addTag();
                    btnAdd.requestFocusInWindow();
                }
            } else if (e.getSource() == membershipTable) {
                int row = membershipTable.rowAtPoint(e.getPoint());
                if (row > -1) {
                    editMembership(row);
                }
            } else {
                editHelper.addTag();
                btnAdd.requestFocusInWindow();
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getSource() == tagTable) {
                membershipTable.clearSelection();
            } else if (e.getSource() == membershipTable) {
                tagTable.clearSelection();
            }
        }
    }

    static class MemberInfo {
        private final List<RelationMember> role = new ArrayList<>();
        private Set<OsmPrimitive> members = new HashSet<>();
        private List<Integer> position = new ArrayList<>();
        private Iterable<OsmPrimitive> selection;
        private String positionString;
        private String roleString;

        MemberInfo(Iterable<OsmPrimitive> selection) {
            this.selection = selection;
        }

        void add(RelationMember r, Integer p) {
            role.add(r);
            members.add(r.getMember());
            position.add(p);
        }

        String getPositionString() {
            if (positionString == null) {
                positionString = Utils.getPositionListString(position);
                // if not all objects from the selection are member of this relation
                if (Utils.exists(selection, Predicates.not(Predicates.inCollection(members)))) {
                    positionString += ",\u2717";
                }
                members = null;
                position = null;
                selection = null;
            }
            return Utils.shortenString(positionString, 20);
        }

        String getRoleString() {
            if (roleString == null) {
                for (RelationMember r : role) {
                    if (roleString == null) {
                        roleString = r.getRole();
                    } else if (!roleString.equals(r.getRole())) {
                        roleString = tr("<different>");
                        break;
                    }
                }
            }
            return roleString;
        }

        @Override
        public String toString() {
            return "MemberInfo{" +
                    "roles='" + roleString + '\'' +
                    ", positions='" + positionString + '\'' +
                    '}';
        }
    }

    /**
     * Class that allows fast creation of read-only table model with String columns
     */
    public static class ReadOnlyTableModel extends DefaultTableModel {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return String.class;
        }
    }

    /**
     * Action handling delete button press in properties dialog.
     */
    class DeleteAction extends JosmAction implements ListSelectionListener {

        private static final String DELETE_FROM_RELATION_PREF = "delete_from_relation";

        DeleteAction() {
            super(tr("Delete"), /* ICON() */ "dialogs/delete", tr("Delete the selected key in all objects"),
                    Shortcut.registerShortcut("properties:delete", tr("Delete Tags"), KeyEvent.VK_D,
                            Shortcut.ALT_CTRL_SHIFT), false);
            updateEnabledState();
        }

        protected void deleteTags(int[] rows) {
            // convert list of rows to HashMap (and find gap for nextKey)
            Map<String, String> tags = new HashMap<>(rows.length);
            int nextKeyIndex = rows[0];
            for (int row : rows) {
                String key = editHelper.getDataKey(row);
                if (row == nextKeyIndex + 1) {
                    nextKeyIndex = row; // no gap yet
                }
                tags.put(key, null);
            }

            // find key to select after deleting other tags
            String nextKey = null;
            int rowCount = tagData.getRowCount();
            if (rowCount > rows.length) {
                if (nextKeyIndex == rows[rows.length-1]) {
                    // no gap found, pick next or previous key in list
                    nextKeyIndex = nextKeyIndex + 1 < rowCount ? nextKeyIndex + 1 : rows[0] - 1;
                } else {
                    // gap found
                    nextKeyIndex++;
                }
                nextKey = editHelper.getDataKey(nextKeyIndex);
            }

            Collection<OsmPrimitive> sel = Main.main.getInProgressSelection();
            Main.main.undoRedo.add(new ChangePropertyCommand(sel, tags));

            membershipTable.clearSelection();
            if (nextKey != null) {
                tagTable.changeSelection(findViewRow(tagTable, tagData, nextKey), 0, false, false);
            }
        }

        protected void deleteFromRelation(int row) {
            Relation cur = (Relation) membershipData.getValueAt(row, 0);

            Relation nextRelation = null;
            int rowCount = membershipTable.getRowCount();
            if (rowCount > 1) {
                nextRelation = (Relation) membershipData.getValueAt(row + 1 < rowCount ? row + 1 : row - 1, 0);
            }

            ExtendedDialog ed = new ExtendedDialog(Main.parent,
                    tr("Change relation"),
                    new String[] {tr("Delete from relation"), tr("Cancel")});
            ed.setButtonIcons(new String[] {"dialogs/delete", "cancel"});
            ed.setContent(tr("Really delete selection from relation {0}?", cur.getDisplayName(DefaultNameFormatter.getInstance())));
            ed.toggleEnable(DELETE_FROM_RELATION_PREF);
            ed.showDialog();

            if (ed.getValue() != 1)
                return;

            Relation rel = new Relation(cur);
            for (OsmPrimitive primitive: Main.main.getInProgressSelection()) {
                rel.removeMembersFor(primitive);
            }
            Main.main.undoRedo.add(new ChangeCommand(cur, rel));

            tagTable.clearSelection();
            if (nextRelation != null) {
                membershipTable.changeSelection(findViewRow(membershipTable, membershipData, nextRelation), 0, false, false);
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (tagTable.getSelectedRowCount() > 0) {
                int[] rows = tagTable.getSelectedRows();
                deleteTags(rows);
            } else if (membershipTable.getSelectedRowCount() > 0) {
                ConditionalOptionPaneUtil.startBulkOperation(DELETE_FROM_RELATION_PREF);
                int[] rows = membershipTable.getSelectedRows();
                // delete from last relation to conserve row numbers in the table
                for (int i = rows.length-1; i >= 0; i--) {
                    deleteFromRelation(rows[i]);
                }
                ConditionalOptionPaneUtil.endBulkOperation(DELETE_FROM_RELATION_PREF);
            }
        }

        @Override
        protected final void updateEnabledState() {
            setEnabled(
                    (tagTable != null && tagTable.getSelectedRowCount() >= 1)
                    || (membershipTable != null && membershipTable.getSelectedRowCount() > 0)
                    );
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    /**
     * Action handling add button press in properties dialog.
     */
    class AddAction extends JosmAction {
        AddAction() {
            super(tr("Add"), /* ICON() */ "dialogs/add", tr("Add a new key/value pair to all objects"),
                    Shortcut.registerShortcut("properties:add", tr("Add Tag"), KeyEvent.VK_A,
                            Shortcut.ALT), false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            editHelper.addTag();
            btnAdd.requestFocusInWindow();
        }
    }

    /**
     * Action handling edit button press in properties dialog.
     */
    class EditAction extends JosmAction implements ListSelectionListener {
        EditAction() {
            super(tr("Edit"), /* ICON() */ "dialogs/edit", tr("Edit the value of the selected key for all objects"),
                    Shortcut.registerShortcut("properties:edit", tr("Edit Tags"), KeyEvent.VK_S,
                            Shortcut.ALT), false);
            updateEnabledState();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (!isEnabled())
                return;
            if (tagTable.getSelectedRowCount() == 1) {
                int row = tagTable.getSelectedRow();
                editHelper.editTag(row, false);
            } else if (membershipTable.getSelectedRowCount() == 1) {
                int row = membershipTable.getSelectedRow();
                editMembership(row);
            }
        }

        @Override
        protected void updateEnabledState() {
            setEnabled(
                    (tagTable != null && tagTable.getSelectedRowCount() == 1)
                    ^ (membershipTable != null && membershipTable.getSelectedRowCount() == 1)
                    );
        }

        @Override
        public void valueChanged(ListSelectionEvent e) {
            updateEnabledState();
        }
    }

    class HelpAction extends AbstractAction {
        HelpAction() {
            putValue(NAME, tr("Go to OSM wiki for tag help"));
            putValue(SHORT_DESCRIPTION, tr("Launch browser with wiki help for selected object"));
            putValue(SMALL_ICON, ImageProvider.get("dialogs", "search"));
            putValue(ACCELERATOR_KEY, getKeyStroke());
        }

        public KeyStroke getKeyStroke() {
            return KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            try {
                String base = Main.pref.get("url.openstreetmap-wiki", "https://wiki.openstreetmap.org/wiki/");
                String lang = LanguageInfo.getWikiLanguagePrefix();
                final List<URI> uris = new ArrayList<>();
                int row;
                if (tagTable.getSelectedRowCount() == 1) {
                    row = tagTable.getSelectedRow();
                    String key = Utils.encodeUrl(editHelper.getDataKey(row));
                    Map<String, Integer> m = editHelper.getDataValues(row);
                    String val = Utils.encodeUrl(m.entrySet().iterator().next().getKey());

                    uris.add(new URI(String.format("%s%sTag:%s=%s", base, lang, key, val)));
                    uris.add(new URI(String.format("%sTag:%s=%s", base, key, val)));
                    uris.add(new URI(String.format("%s%sKey:%s", base, lang, key)));
                    uris.add(new URI(String.format("%sKey:%s", base, key)));
                    uris.add(new URI(String.format("%s%sMap_Features", base, lang)));
                    uris.add(new URI(String.format("%sMap_Features", base)));
                } else if (membershipTable.getSelectedRowCount() == 1) {
                    row = membershipTable.getSelectedRow();
                    String type = ((Relation) membershipData.getValueAt(row, 0)).get("type");
                    if (type != null) {
                        type = Utils.encodeUrl(type);
                    }

                    if (type != null && !type.isEmpty()) {
                        uris.add(new URI(String.format("%s%sRelation:%s", base, lang, type)));
                        uris.add(new URI(String.format("%sRelation:%s", base, type)));
                    }

                    uris.add(new URI(String.format("%s%sRelations", base, lang)));
                    uris.add(new URI(String.format("%sRelations", base)));
                } else {
                    // give the generic help page, if more than one element is selected
                    uris.add(new URI(String.format("%s%sMap_Features", base, lang)));
                    uris.add(new URI(String.format("%sMap_Features", base)));
                }

                Main.worker.execute(new Runnable() {
                    @Override public void run() {
                        try {
                            // find a page that actually exists in the wiki
                            HttpClient.Response conn;
                            for (URI u : uris) {
                                conn = HttpClient.create(u.toURL(), "HEAD").connect();

                                if (conn.getResponseCode() != 200) {
                                    conn.disconnect();
                                } else {
                                    long osize = conn.getContentLength();
                                    if (osize > -1) {
                                        conn.disconnect();

                                        final URI newURI = new URI(u.toString()
                                                .replace("=", "%3D") /* do not URLencode whole string! */
                                                .replaceFirst("/wiki/", "/w/index.php?redirect=no&title=")
                                        );
                                        conn = HttpClient.create(newURI.toURL(), "HEAD").connect();
                                    }

                                    /* redirect pages have different content length, but retrieving a "nonredirect"
                                     *  page using index.php and the direct-link method gives slightly different
                                     *  content lengths, so we have to be fuzzy.. (this is UGLY, recode if u know better)
                                     */
                                    if (conn.getContentLength() != -1 && osize > -1 && Math.abs(conn.getContentLength() - osize) > 200) {
                                        Main.info("{0} is a mediawiki redirect", u);
                                        conn.disconnect();
                                    } else {
                                        conn.disconnect();

                                        OpenBrowser.displayUrl(u.toString());
                                        break;
                                    }
                                }
                            }
                        } catch (URISyntaxException | IOException e) {
                            Main.error(e);
                        }
                    }
                });
            } catch (URISyntaxException e1) {
                Main.error(e1);
            }
        }
    }

    class TaginfoAction extends JosmAction {

        final transient StringProperty TAGINFO_URL_PROP = new StringProperty("taginfo.url", "https://taginfo.openstreetmap.org/");

        TaginfoAction() {
            super(tr("Go to Taginfo"), "dialogs/taginfo", tr("Launch browser with Taginfo statistics for selected object"), null, false);
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final String url;
            if (tagTable.getSelectedRowCount() == 1) {
                final int row = tagTable.getSelectedRow();
                final String key = Utils.encodeUrl(editHelper.getDataKey(row));
                Map<String, Integer> values = editHelper.getDataValues(row);
                if (values.size() == 1) {
                    url = TAGINFO_URL_PROP.get() + "tags/" + key /* do not URL encode key, otherwise addr:street does not work */
                            + '=' + Utils.encodeUrl(values.keySet().iterator().next());
                } else {
                    url = TAGINFO_URL_PROP.get() + "keys/" + key; /* do not URL encode key, otherwise addr:street does not work */
                }
            } else if (membershipTable.getSelectedRowCount() == 1) {
                final String type = ((Relation) membershipData.getValueAt(membershipTable.getSelectedRow(), 0)).get("type");
                url = TAGINFO_URL_PROP.get() + "relations/" + type;
            } else {
                return;
            }
            OpenBrowser.displayUrl(url);
        }
    }

    class PasteValueAction extends AbstractAction {
        PasteValueAction() {
            putValue(NAME, tr("Paste Value"));
            putValue(SHORT_DESCRIPTION, tr("Paste the value of the selected tag from clipboard"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            if (tagTable.getSelectedRowCount() != 1)
                return;
            String key = editHelper.getDataKey(tagTable.getSelectedRow());
            Collection<OsmPrimitive> sel = Main.main.getInProgressSelection();
            String clipboard = Utils.getClipboardContent();
            if (sel.isEmpty() || clipboard == null)
                return;
            Main.main.undoRedo.add(new ChangePropertyCommand(sel, key, Utils.strip(clipboard)));
        }
    }

    abstract class AbstractCopyAction extends AbstractAction {

        protected abstract Collection<String> getString(OsmPrimitive p, String key);

        @Override
        public void actionPerformed(ActionEvent ae) {
            int[] rows = tagTable.getSelectedRows();
            Set<String> values = new TreeSet<>();
            Collection<OsmPrimitive> sel = Main.main.getInProgressSelection();
            if (rows.length == 0 || sel.isEmpty()) return;

            for (int row: rows) {
                String key = editHelper.getDataKey(row);
                if (sel.isEmpty())
                    return;
                for (OsmPrimitive p : sel) {
                    Collection<String> s = getString(p, key);
                    if (s != null) {
                        values.addAll(s);
                    }
                }
            }
            if (!values.isEmpty()) {
                Utils.copyToClipboard(Utils.join("\n", values));
            }
        }
    }

    class CopyValueAction extends AbstractCopyAction {

        /**
         * Constructs a new {@code CopyValueAction}.
         */
        CopyValueAction() {
            putValue(NAME, tr("Copy Value"));
            putValue(SHORT_DESCRIPTION, tr("Copy the value of the selected tag to clipboard"));
        }

        @Override
        protected Collection<String> getString(OsmPrimitive p, String key) {
            String v = p.get(key);
            return v == null ? null : Collections.singleton(v);
        }
    }

    class CopyKeyValueAction extends AbstractCopyAction {

        CopyKeyValueAction() {
            putValue(NAME, tr("Copy selected Key(s)/Value(s)"));
            putValue(SHORT_DESCRIPTION, tr("Copy the key and value of the selected tag(s) to clipboard"));
        }

        @Override
        protected Collection<String> getString(OsmPrimitive p, String key) {
            String v = p.get(key);
            return v == null ? null : Collections.singleton(new Tag(key, v).toString());
        }
    }

    class CopyAllKeyValueAction extends AbstractCopyAction {

        CopyAllKeyValueAction() {
            putValue(NAME, tr("Copy all Keys/Values"));
            putValue(SHORT_DESCRIPTION, tr("Copy the key and value of all the tags to clipboard"));
            Shortcut sc = Shortcut.registerShortcut("system:copytags", tr("Edit: {0}", tr("Copy Tags")), KeyEvent.CHAR_UNDEFINED, Shortcut.NONE);
            Main.registerActionShortcut(this, sc);
            sc.setAccelerator(this);
        }

        @Override
        protected Collection<String> getString(OsmPrimitive p, String key) {
            List<String> r = new LinkedList<>();
            for (Entry<String, String> kv : p.getKeys().entrySet()) {
                r.add(new Tag(kv.getKey(), kv.getValue()).toString());
            }
            return r;
        }
    }

    class SearchAction extends AbstractAction {
        private final boolean sameType;

        SearchAction(boolean sameType) {
            this.sameType = sameType;
            if (sameType) {
                putValue(NAME, tr("Search Key/Value/Type"));
                putValue(SHORT_DESCRIPTION, tr("Search with the key and value of the selected tag, restrict to type (i.e., node/way/relation)"));
            } else {
                putValue(NAME, tr("Search Key/Value"));
                putValue(SHORT_DESCRIPTION, tr("Search with the key and value of the selected tag"));
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (tagTable.getSelectedRowCount() != 1)
                return;
            String key = editHelper.getDataKey(tagTable.getSelectedRow());
            Collection<OsmPrimitive> sel = Main.main.getInProgressSelection();
            if (sel.isEmpty())
                return;
            final SearchSetting ss = createSearchSetting(key, sel, sameType);
            org.openstreetmap.josm.actions.search.SearchAction.searchWithoutHistory(ss);
        }
    }

    static SearchSetting createSearchSetting(String key, Collection<OsmPrimitive> sel, boolean sameType) {
        String sep = "";
        StringBuilder s = new StringBuilder();
        Set<String> consideredTokens = new TreeSet<>();
        for (OsmPrimitive p : sel) {
            String val = p.get(key);
            if (val == null || (!sameType && consideredTokens.contains(val))) {
                continue;
            }
            String t = "";
            if (!sameType) {
                t = "";
            } else if (p instanceof Node) {
                t = "type:node ";
            } else if (p instanceof Way) {
                t = "type:way ";
            } else if (p instanceof Relation) {
                t = "type:relation ";
            }
            String token = new StringBuilder(t).append(val).toString();
            if (consideredTokens.add(token)) {
                s.append(sep).append('(').append(t).append(SearchCompiler.buildSearchStringForTag(key, val)).append(')');
                sep = " OR ";
            }
        }

        final SearchSetting ss = new SearchSetting();
        ss.text = s.toString();
        ss.caseSensitive = true;
        return ss;
    }

    @Override
    public void preferenceChanged(PreferenceChangeEvent e) {
        super.preferenceChanged(e);
        if ("display.discardable-keys".equals(e.getKey()) && Main.main.getCurrentDataSet() != null) {
            // Re-load data when display preference change
            updateSelection();
        }
    }

    /**
     * Clears the row selection when it is filtered away by the row sorter.
     */
    private class RemoveHiddenSelection implements ListSelectionListener, RowSorterListener {

        void removeHiddenSelection() {
            try {
                tagRowSorter.convertRowIndexToModel(tagTable.getSelectedRow());
            } catch (IndexOutOfBoundsException ignore) {
                Main.debug("Clearing tagTable selection, {0}", ignore.toString());
                tagTable.clearSelection();
            }
        }

        @Override
        public void valueChanged(ListSelectionEvent event) {
            removeHiddenSelection();
        }

        @Override
        public void sorterChanged(RowSorterEvent e) {
            removeHiddenSelection();
        }
    }
}
