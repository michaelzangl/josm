// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.dialogs;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.AbstractAction;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultListSelectionModel;
import javax.swing.ImageIcon;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.UIManager;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.MergeLayerAction;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.SideButton;
import org.openstreetmap.josm.gui.dialogs.layer.ActivateLayerAction;
import org.openstreetmap.josm.gui.dialogs.layer.DeleteLayerAction;
import org.openstreetmap.josm.gui.dialogs.layer.DuplicateAction;
import org.openstreetmap.josm.gui.dialogs.layer.IEnabledStateUpdating;
import org.openstreetmap.josm.gui.dialogs.layer.LayerVisibilityAction;
import org.openstreetmap.josm.gui.dialogs.layer.MergeAction;
import org.openstreetmap.josm.gui.dialogs.layer.MoveDownAction;
import org.openstreetmap.josm.gui.dialogs.layer.MoveUpAction;
import org.openstreetmap.josm.gui.dialogs.layer.ShowHideLayerAction;
import org.openstreetmap.josm.gui.layer.JumpToMarkerActions;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.gui.layer.NativeScaleLayer;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.DisableShortcutsOnFocusGainedTextField;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.gui.widgets.PopupMenuLauncher;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.InputMapUtils;
import org.openstreetmap.josm.tools.MultikeyActionsHandler;
import org.openstreetmap.josm.tools.MultikeyShortcutAction.MultikeyInfo;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * This is a toggle dialog which displays the list of layers. Actions allow to
 * change the ordering of the layers, to hide/show layers, to activate layers,
 * and to delete layers.
 * <p>
 * Support for multiple {@link LayerListDialog} is currently not complete but intended for the future.
 * @since 17
 */
public class LayerListDialog extends ToggleDialog {
    /** the unique instance of the dialog */
    private static volatile LayerListDialog instance;

    /**
     * Creates the instance of the dialog. It's connected to the map frame <code>mapFrame</code>
     *
     * @param mapFrame the map frame
     */
    public static void createInstance(MapFrame mapFrame) {
        if (instance != null)
            throw new IllegalStateException("Dialog was already created");
        instance = new LayerListDialog(mapFrame);
    }

    /**
     * Replies the instance of the dialog
     *
     * @return the instance of the dialog
     * @throws IllegalStateException if the dialog is not created yet
     * @see #createInstance(MapFrame)
     */
    public static LayerListDialog getInstance() {
        if (instance == null)
            throw new IllegalStateException("Dialog not created yet. Invoke createInstance() first");
        return instance;
    }

    /** the model for the layer list */
    private final LayerListModel model;

    /** the list of layers (technically its a JTable, but appears like a list) */
    private final LayerList layerList;

    private final ActivateLayerAction activateLayerAction;
    private final ShowHideLayerAction showHideLayerAction;

    //TODO This duplicates ShowHide actions functionality
    /** stores which layer index to toggle and executes the ShowHide action if the layer is present */
    private final class ToggleLayerIndexVisibility extends AbstractAction {
        private final int layerIndex;

        ToggleLayerIndexVisibility(int layerIndex) {
            this.layerIndex = layerIndex;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            final Layer l = model.getLayer(model.getRowCount() - layerIndex - 1);
            if (l != null) {
                l.toggleVisible();
            }
        }
    }

    private final transient Shortcut[] visibilityToggleShortcuts = new Shortcut[10];
    private final ToggleLayerIndexVisibility[] visibilityToggleActions = new ToggleLayerIndexVisibility[10];

    /**
     * The {@link MainLayerManager} this list is for.
     * @since xxx
     */
    private final transient MainLayerManager layerManager;

    /**
     * registers (shortcut to toggle right hand side toggle dialogs)+(number keys) shortcuts
     * to toggle the visibility of the first ten layers.
     */
    private void createVisibilityToggleShortcuts() {
        for (int i = 0; i < 10; i++) {
            final int i1 = i + 1;
            /* POSSIBLE SHORTCUTS: 1,2,3,4,5,6,7,8,9,0=10 */
            visibilityToggleShortcuts[i] = Shortcut.registerShortcut("subwindow:layers:toggleLayer" + i1,
                    tr("Toggle visibility of layer: {0}", i1), KeyEvent.VK_0 + (i1 % 10), Shortcut.ALT);
            visibilityToggleActions[i] = new ToggleLayerIndexVisibility(i);
            Main.registerActionShortcut(visibilityToggleActions[i], visibilityToggleShortcuts[i]);
        }
    }

    /**
     * Creates a layer list and attach it to the given mapView.
     * @param mapFrame map frame
     */
    protected LayerListDialog(MapFrame mapFrame) {
        this(mapFrame.mapView.getLayerManager());
    }

    /**
     * Creates a layer list and attach it to the given mapView.
     * @param layerManager The layer manager this list is for
     * @since xxx
     */
    private LayerListDialog(MainLayerManager layerManager) {
        super(tr("Layers"), "layerlist", tr("Open a list of all loaded layers."),
                Shortcut.registerShortcut("subwindow:layers", tr("Toggle: {0}", tr("Layers")), KeyEvent.VK_L,
                        Shortcut.ALT_SHIFT), 100, true);
        this.layerManager = layerManager;

        // create the models
        //
        DefaultListSelectionModel selectionModel = new DefaultListSelectionModel();
        selectionModel.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        model = new LayerListModel(selectionModel);

        // create the list control
        //
        layerList = new LayerList(model, layerManager);
        layerList.setSelectionModel(selectionModel);
        layerList.addMouseListener(new PopupMenuHandler());
        layerList.setBackground(UIManager.getColor("Button.background"));
        layerList.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        layerList.putClientProperty("JTable.autoStartsEdit", Boolean.FALSE);
        layerList.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        layerList.setTableHeader(null);
        layerList.setShowGrid(false);
        layerList.setIntercellSpacing(new Dimension(0, 0));
        layerList.getColumnModel().getColumn(0).setCellRenderer(new ActiveLayerCellRenderer());
        layerList.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(new ActiveLayerCheckBox()));
        layerList.getColumnModel().getColumn(0).setMaxWidth(12);
        layerList.getColumnModel().getColumn(0).setPreferredWidth(12);
        layerList.getColumnModel().getColumn(0).setResizable(false);

        layerList.getColumnModel().getColumn(1).setCellRenderer(new NativeScaleLayerCellRenderer());
        layerList.getColumnModel().getColumn(1).setCellEditor(new DefaultCellEditor(new NativeScaleLayerCheckBox()));
        layerList.getColumnModel().getColumn(1).setMaxWidth(12);
        layerList.getColumnModel().getColumn(1).setPreferredWidth(12);
        layerList.getColumnModel().getColumn(1).setResizable(false);

        layerList.getColumnModel().getColumn(2).setCellRenderer(new LayerVisibleCellRenderer());
        layerList.getColumnModel().getColumn(2).setCellEditor(new LayerVisibleCellEditor(new LayerVisibleCheckBox()));
        layerList.getColumnModel().getColumn(2).setMaxWidth(16);
        layerList.getColumnModel().getColumn(2).setPreferredWidth(16);
        layerList.getColumnModel().getColumn(2).setResizable(false);

        layerList.getColumnModel().getColumn(3).setCellRenderer(new LayerNameCellRenderer());
        layerList.getColumnModel().getColumn(3).setCellEditor(new LayerNameCellEditor(new DisableShortcutsOnFocusGainedTextField()));
        // Disable some default JTable shortcuts to use JOSM ones (see #5678, #10458)
        for (KeyStroke ks : new KeyStroke[] {
                KeyStroke.getKeyStroke(KeyEvent.VK_C, GuiHelper.getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_V, GuiHelper.getMenuShortcutKeyMaskEx()),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_UP, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_LEFT, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_RIGHT, InputEvent.CTRL_DOWN_MASK),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_UP, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_PAGE_DOWN, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_TAB, 0),
                KeyStroke.getKeyStroke(KeyEvent.VK_F8, 0),
        }) {
            layerList.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(ks, new Object());
        }

        // init the model
        //
        model.populate();
        model.setSelectedLayer(layerManager.getActiveLayer());
        model.addLayerListModelListener(
                new LayerListModelListener() {
                    @Override
                    public void makeVisible(int row, Layer layer) {
                        layerList.scrollToVisible(row, 0);
                        layerList.repaint();
                    }

                    @Override
                    public void refresh() {
                        layerList.repaint();
                    }
                }
                );

        // -- move up action
        MoveUpAction moveUpAction = new MoveUpAction(model);
        adaptTo(moveUpAction, model);
        adaptTo(moveUpAction, selectionModel);

        // -- move down action
        MoveDownAction moveDownAction = new MoveDownAction(model);
        adaptTo(moveDownAction, model);
        adaptTo(moveDownAction, selectionModel);

        // -- activate action
        activateLayerAction = new ActivateLayerAction(model);
        activateLayerAction.updateEnabledState();
        MultikeyActionsHandler.getInstance().addAction(activateLayerAction);
        adaptTo(activateLayerAction, selectionModel);

        JumpToMarkerActions.initialize();

        // -- show hide action
        showHideLayerAction = new ShowHideLayerAction(model);
        MultikeyActionsHandler.getInstance().addAction(showHideLayerAction);
        adaptTo(showHideLayerAction, selectionModel);

        LayerVisibilityAction visibilityAction = new LayerVisibilityAction(model);
        adaptTo(visibilityAction, selectionModel);
        SideButton visibilityButton = new SideButton(visibilityAction, false);
        visibilityAction.setCorrespondingSideButton(visibilityButton);

        // -- delete layer action
        DeleteLayerAction deleteLayerAction = new DeleteLayerAction(model);
        layerList.getActionMap().put("deleteLayer", deleteLayerAction);
        adaptTo(deleteLayerAction, selectionModel);
        getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "delete"
                );
        getActionMap().put("delete", deleteLayerAction);

        // Activate layer on Enter key press
        InputMapUtils.addEnterAction(layerList, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                activateLayerAction.actionPerformed(null);
                layerList.requestFocus();
            }
        });

        // Show/Activate layer on Enter key press
        InputMapUtils.addSpacebarAction(layerList, showHideLayerAction);

        createLayout(layerList, true, Arrays.asList(
                new SideButton(moveUpAction, false),
                new SideButton(moveDownAction, false),
                new SideButton(activateLayerAction, false),
                visibilityButton,
                new SideButton(deleteLayerAction, false)
        ));

        createVisibilityToggleShortcuts();
    }

    /**
     * Gets the layer manager this dialog is for.
     * @return The layer manager.
     * @since xxx
     */
    public MainLayerManager getLayerManager() {
        return layerManager;
    }

    @Override
    public void showNotify() {
        MapView.addLayerChangeListener(activateLayerAction);
        layerManager.addLayerChangeListener(model);
        layerManager.addActiveLayerChangeListener(model, true);
        model.populate();
    }

    @Override
    public void hideNotify() {
        layerManager.removeLayerChangeListener(model);
        layerManager.removeActiveLayerChangeListener(model);
        MapView.removeLayerChangeListener(activateLayerAction);
    }

    /**
     * Returns the layer list model.
     * @return the layer list model
     */
    public LayerListModel getModel() {
        return model;
    }

    /**
     * Wires <code>listener</code> to <code>listSelectionModel</code> in such a way, that
     * <code>listener</code> receives a {@link IEnabledStateUpdating#updateEnabledState()}
     * on every {@link ListSelectionEvent}.
     *
     * @param listener  the listener
     * @param listSelectionModel  the source emitting {@link ListSelectionEvent}s
     */
    protected void adaptTo(final IEnabledStateUpdating listener, ListSelectionModel listSelectionModel) {
        listSelectionModel.addListSelectionListener(
                new ListSelectionListener() {
                    @Override
                    public void valueChanged(ListSelectionEvent e) {
                        listener.updateEnabledState();
                    }
                }
                );
    }

    /**
     * Wires <code>listener</code> to <code>listModel</code> in such a way, that
     * <code>listener</code> receives a {@link IEnabledStateUpdating#updateEnabledState()}
     * on every {@link ListDataEvent}.
     *
     * @param listener the listener
     * @param listModel the source emitting {@link ListDataEvent}s
     */
    protected void adaptTo(final IEnabledStateUpdating listener, LayerListModel listModel) {
        listModel.addTableModelListener(
                new TableModelListener() {
                    @Override
                    public void tableChanged(TableModelEvent e) {
                        listener.updateEnabledState();
                    }
                }
                );
    }

    @Override
    public void destroy() {
        for (int i = 0; i < 10; i++) {
            Main.unregisterActionShortcut(visibilityToggleActions[i], visibilityToggleShortcuts[i]);
        }
        MultikeyActionsHandler.getInstance().removeAction(activateLayerAction);
        MultikeyActionsHandler.getInstance().removeAction(showHideLayerAction);
        JumpToMarkerActions.unregisterActions();
        super.destroy();
        instance = null;
    }

    private static class ActiveLayerCheckBox extends JCheckBox {
        ActiveLayerCheckBox() {
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            ImageIcon blank = ImageProvider.get("dialogs/layerlist", "blank");
            ImageIcon active = ImageProvider.get("dialogs/layerlist", "active");
            setIcon(blank);
            setSelectedIcon(active);
            setRolloverIcon(blank);
            setRolloverSelectedIcon(active);
            setPressedIcon(ImageProvider.get("dialogs/layerlist", "active-pressed"));
        }
    }

    private static class LayerVisibleCheckBox extends JCheckBox {
        private final ImageIcon iconEye;
        private final ImageIcon iconEyeTranslucent;
        private boolean isTranslucent;

        /**
         * Constructs a new {@code LayerVisibleCheckBox}.
         */
        LayerVisibleCheckBox() {
            setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
            iconEye = ImageProvider.get("dialogs/layerlist", "eye");
            iconEyeTranslucent = ImageProvider.get("dialogs/layerlist", "eye-translucent");
            setIcon(ImageProvider.get("dialogs/layerlist", "eye-off"));
            setPressedIcon(ImageProvider.get("dialogs/layerlist", "eye-pressed"));
            setSelectedIcon(iconEye);
            isTranslucent = false;
        }

        public void setTranslucent(boolean isTranslucent) {
            if (this.isTranslucent == isTranslucent) return;
            if (isTranslucent) {
                setSelectedIcon(iconEyeTranslucent);
            } else {
                setSelectedIcon(iconEye);
            }
            this.isTranslucent = isTranslucent;
        }

        public void updateStatus(Layer layer) {
            boolean visible = layer.isVisible();
            setSelected(visible);
            setTranslucent(layer.getOpacity() < 1.0);
            setToolTipText(visible ?
                tr("layer is currently visible (click to hide layer)") :
                tr("layer is currently hidden (click to show layer)"));
        }
    }

    private static class NativeScaleLayerCheckBox extends JCheckBox {
        NativeScaleLayerCheckBox() {
            setHorizontalAlignment(javax.swing.SwingConstants.CENTER);
            ImageIcon blank = ImageProvider.get("dialogs/layerlist", "blank");
            ImageIcon active = ImageProvider.get("dialogs/layerlist", "scale");
            setIcon(blank);
            setSelectedIcon(active);
        }
    }

    private static class ActiveLayerCellRenderer implements TableCellRenderer {
        private final JCheckBox cb;

        /**
         * Constructs a new {@code ActiveLayerCellRenderer}.
         */
        ActiveLayerCellRenderer() {
            cb = new ActiveLayerCheckBox();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            boolean active = value != null && (Boolean) value;
            cb.setSelected(active);
            cb.setToolTipText(active ? tr("this layer is the active layer") : tr("this layer is not currently active (click to activate)"));
            return cb;
        }
    }

    private static class LayerVisibleCellRenderer implements TableCellRenderer {
        private final LayerVisibleCheckBox cb;

        /**
         * Constructs a new {@code LayerVisibleCellRenderer}.
         */
        LayerVisibleCellRenderer() {
            this.cb = new LayerVisibleCheckBox();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value != null) {
                cb.updateStatus((Layer) value);
            }
            return cb;
        }
    }

    private static class LayerVisibleCellEditor extends DefaultCellEditor {
        private final LayerVisibleCheckBox cb;

        LayerVisibleCellEditor(LayerVisibleCheckBox cb) {
            super(cb);
            this.cb = cb;
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            cb.updateStatus((Layer) value);
            return cb;
        }
    }

    private static class NativeScaleLayerCellRenderer implements TableCellRenderer {
        private final JCheckBox cb;

        /**
         * Constructs a new {@code ActiveLayerCellRenderer}.
         */
        NativeScaleLayerCellRenderer() {
            cb = new NativeScaleLayerCheckBox();
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            Layer layer = (Layer) value;
            if (layer instanceof NativeScaleLayer) {
                boolean active = ((NativeScaleLayer) layer) == Main.map.mapView.getNativeScaleLayer();
                cb.setSelected(active);
                cb.setToolTipText(active
                    ? tr("scale follows native resolution of this layer")
                    : tr("scale follows native resolution of another layer (click to set this layer)")
                );
            } else {
                cb.setSelected(false);
                cb.setToolTipText(tr("this layer has no native resolution"));
            }
            return cb;
        }
    }

    private class LayerNameCellRenderer extends DefaultTableCellRenderer {

        protected boolean isActiveLayer(Layer layer) {
            return getLayerManager().getActiveLayer() == layer;
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
            if (value == null)
                return this;
            Layer layer = (Layer) value;
            JLabel label = (JLabel) super.getTableCellRendererComponent(table,
                    layer.getName(), isSelected, hasFocus, row, column);
            if (isActiveLayer(layer)) {
                label.setFont(label.getFont().deriveFont(Font.BOLD));
            }
            if (Main.pref.getBoolean("dialog.layer.colorname", true)) {
                Color c = layer.getColor(false);
                if (c != null) {
                    Color oc = null;
                    for (Layer l : model.getLayers()) {
                        oc = l.getColor(false);
                        if (oc != null) {
                            if (oc.equals(c)) {
                                oc = null;
                            } else {
                                break;
                            }
                        }
                    }
                    /* not more than one color, don't use coloring */
                    if (oc == null) {
                        c = null;
                    }
                }
                if (c == null) {
                    c = UIManager.getColor(isSelected ? "Table.selectionForeground" : "Table.foreground");
                }
                label.setForeground(c);
            }
            label.setIcon(layer.getIcon());
            label.setToolTipText(layer.getToolTipText());
            return label;
        }
    }

    private static class LayerNameCellEditor extends DefaultCellEditor {
        LayerNameCellEditor(DisableShortcutsOnFocusGainedTextField tf) {
            super(tf);
        }

        @Override
        public Component getTableCellEditorComponent(JTable table, Object value, boolean isSelected, int row, int column) {
            JosmTextField tf = (JosmTextField) super.getTableCellEditorComponent(table, value, isSelected, row, column);
            tf.setText(value == null ? "" : ((Layer) value).getName());
            return tf;
        }
    }

    class PopupMenuHandler extends PopupMenuLauncher {
        @Override
        public void showMenu(MouseEvent evt) {
            menu = new LayerListPopup(getModel().getSelectedLayers());
            super.showMenu(evt);
        }
    }

    /**
     * Observer interface to be implemented by views using {@link LayerListModel}.
     */
    public interface LayerListModelListener {

        /**
         * Fired when a layer is made visible.
         * @param index the layer index
         * @param layer the layer
         */
        void makeVisible(int index, Layer layer);


        /**
         * Fired when something has changed in the layer list model.
         */
        void refresh();
    }

    /**
     * The layer list model. The model manages a list of layers and provides methods for
     * moving layers up and down, for toggling their visibility, and for activating a layer.
     *
     * The model is a {@link TableModel} and it provides a {@link ListSelectionModel}. It expects
     * to be configured with a {@link DefaultListSelectionModel}. The selection model is used
     * to update the selection state of views depending on messages sent to the model.
     *
     * The model manages a list of {@link LayerListModelListener} which are mainly notified if
     * the model requires views to make a specific list entry visible.
     *
     * It also listens to {@link PropertyChangeEvent}s of every {@link Layer} it manages, in particular to
     * the properties {@link Layer#VISIBLE_PROP} and {@link Layer#NAME_PROP}.
     */
    public static final class LayerListModel extends AbstractTableModel
            implements LayerChangeListener, ActiveLayerChangeListener, PropertyChangeListener {
        /** manages list selection state*/
        private final DefaultListSelectionModel selectionModel;
        private final CopyOnWriteArrayList<LayerListModelListener> listeners;
        private LayerList layerList;

        /**
         * constructor
         *
         * @param selectionModel the list selection model
         */
        LayerListModel(DefaultListSelectionModel selectionModel) {
            this.selectionModel = selectionModel;
            listeners = new CopyOnWriteArrayList<>();
        }

        void setLayerList(LayerList layerList) {
            this.layerList = layerList;
        }

        private MainLayerManager getLayerManager() {
            // layerList should never be null. But if it is, we should not crash.
            if (layerList == null) {
                return new MainLayerManager();
            } else {
                return layerList.getLayerManager();
            }
        }

        /**
         * Adds a listener to this model
         *
         * @param listener the listener
         */
        public void addLayerListModelListener(LayerListModelListener listener) {
            if (listener != null) {
                listeners.addIfAbsent(listener);
            }
        }

        /**
         * removes a listener from  this model
         * @param listener the listener
         */
        public void removeLayerListModelListener(LayerListModelListener listener) {
            listeners.remove(listener);
        }

        /**
         * Fires a make visible event to listeners
         *
         * @param index the index of the row to make visible
         * @param layer the layer at this index
         * @see LayerListModelListener#makeVisible(int, Layer)
         */
        protected void fireMakeVisible(int index, Layer layer) {
            for (LayerListModelListener listener : listeners) {
                listener.makeVisible(index, layer);
            }
        }

        /**
         * Fires a refresh event to listeners of this model
         *
         * @see LayerListModelListener#refresh()
         */
        protected void fireRefresh() {
            for (LayerListModelListener listener : listeners) {
                listener.refresh();
            }
        }

        /**
         * Populates the model with the current layers managed by {@link MapView}.
         */
        public void populate() {
            for (Layer layer: getLayers()) {
                // make sure the model is registered exactly once
                layer.removePropertyChangeListener(this);
                layer.addPropertyChangeListener(this);
            }
            fireTableDataChanged();
        }

        /**
         * Marks <code>layer</code> as selected layer. Ignored, if layer is null.
         *
         * @param layer the layer.
         */
        public void setSelectedLayer(Layer layer) {
            if (layer == null)
                return;
            int idx = getLayers().indexOf(layer);
            if (idx >= 0) {
                selectionModel.setSelectionInterval(idx, idx);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Replies the list of currently selected layers. Never null, but may be empty.
         *
         * @return the list of currently selected layers. Never null, but may be empty.
         */
        public List<Layer> getSelectedLayers() {
            List<Layer> selected = new ArrayList<>();
            List<Layer> layers = getLayers();
            for (int i = 0; i < layers.size(); i++) {
                if (selectionModel.isSelectedIndex(i)) {
                    selected.add(layers.get(i));
                }
            }
            return selected;
        }

        /**
         * Replies a the list of indices of the selected rows. Never null, but may be empty.
         *
         * @return  the list of indices of the selected rows. Never null, but may be empty.
         */
        public List<Integer> getSelectedRows() {
            List<Integer> selected = new ArrayList<>();
            for (int i = 0; i < getLayers().size(); i++) {
                if (selectionModel.isSelectedIndex(i)) {
                    selected.add(i);
                }
            }
            return selected;
        }

        /**
         * Invoked if a layer managed by {@link MapView} is removed
         *
         * @param layer the layer which is removed
         */
        private void onRemoveLayer(Layer layer) {
            if (layer == null)
                return;
            layer.removePropertyChangeListener(this);
            final int size = getRowCount();
            final List<Integer> rows = getSelectedRows();

            if (rows.isEmpty() && size > 0) {
                selectionModel.setSelectionInterval(size-1, size-1);
            }
            fireTableDataChanged();
            fireRefresh();
            ensureActiveSelected();
        }

        /**
         * Invoked when a layer managed by {@link MapView} is added
         *
         * @param layer the layer
         */
        private void onAddLayer(Layer layer) {
            if (layer == null)
                return;
            layer.addPropertyChangeListener(this);
            fireTableDataChanged();
            int idx = getLayers().indexOf(layer);
            if (layerList != null) {
                layerList.setRowHeight(idx, Math.max(16, layer.getIcon().getIconHeight()));
            }
            selectionModel.setSelectionInterval(idx, idx);
            ensureSelectedIsVisible();
        }

        /**
         * Replies the first layer. Null if no layers are present
         *
         * @return the first layer. Null if no layers are present
         */
        public Layer getFirstLayer() {
            if (getRowCount() == 0)
                return null;
            return getLayers().get(0);
        }

        /**
         * Replies the layer at position <code>index</code>
         *
         * @param index the index
         * @return the layer at position <code>index</code>. Null,
         * if index is out of range.
         */
        public Layer getLayer(int index) {
            if (index < 0 || index >= getRowCount())
                return null;
            return getLayers().get(index);
        }

        /**
         * Replies true if the currently selected layers can move up by one position
         *
         * @return true if the currently selected layers can move up by one position
         */
        public boolean canMoveUp() {
            List<Integer> sel = getSelectedRows();
            return !sel.isEmpty() && sel.get(0) > 0;
        }

        /**
         * Move up the currently selected layers by one position
         *
         */
        public void moveUp() {
            if (!canMoveUp())
                return;
            List<Integer> sel = getSelectedRows();
            List<Layer> layers = getLayers();
            for (int row : sel) {
                Layer l1 = layers.get(row);
                Layer l2 = layers.get(row-1);
                Main.map.mapView.moveLayer(l2, row);
                Main.map.mapView.moveLayer(l1, row-1);
            }
            fireTableDataChanged();
            selectionModel.clearSelection();
            for (int row : sel) {
                selectionModel.addSelectionInterval(row-1, row-1);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Replies true if the currently selected layers can move down by one position
         *
         * @return true if the currently selected layers can move down by one position
         */
        public boolean canMoveDown() {
            List<Integer> sel = getSelectedRows();
            return !sel.isEmpty() && sel.get(sel.size()-1) < getLayers().size()-1;
        }

        /**
         * Move down the currently selected layers by one position
         */
        public void moveDown() {
            if (!canMoveDown())
                return;
            List<Integer> sel = getSelectedRows();
            Collections.reverse(sel);
            List<Layer> layers = getLayers();
            for (int row : sel) {
                Layer l1 = layers.get(row);
                Layer l2 = layers.get(row+1);
                Main.map.mapView.moveLayer(l1, row+1);
                Main.map.mapView.moveLayer(l2, row);
            }
            fireTableDataChanged();
            selectionModel.clearSelection();
            for (int row : sel) {
                selectionModel.addSelectionInterval(row+1, row+1);
            }
            ensureSelectedIsVisible();
        }

        /**
         * Make sure the first of the selected layers is visible in the views of this model.
         */
        protected void ensureSelectedIsVisible() {
            int index = selectionModel.getMinSelectionIndex();
            if (index < 0)
                return;
            List<Layer> layers = getLayers();
            if (index >= layers.size())
                return;
            Layer layer = layers.get(index);
            fireMakeVisible(index, layer);
        }

        /**
         * Replies a list of layers which are possible merge targets for <code>source</code>
         *
         * @param source the source layer
         * @return a list of layers which are possible merge targets
         * for <code>source</code>. Never null, but can be empty.
         */
        public List<Layer> getPossibleMergeTargets(Layer source) {
            List<Layer> targets = new ArrayList<>();
            if (source == null) {
                return targets;
            }
            for (Layer target : getLayers()) {
                if (source == target) {
                    continue;
                }
                if (target.isMergable(source) && source.isMergable(target)) {
                    targets.add(target);
                }
            }
            return targets;
        }

        /**
         * Replies the list of layers currently managed by {@link MapView}.
         * Never null, but can be empty.
         *
         * @return the list of layers currently managed by {@link MapView}.
         * Never null, but can be empty.
         */
        public List<Layer> getLayers() {
            return getLayerManager().getLayers();
        }

        /**
         * Ensures that at least one layer is selected in the layer dialog
         *
         */
        protected void ensureActiveSelected() {
            List<Layer> layers = getLayers();
            if (layers.isEmpty())
                return;
            final Layer activeLayer = getActiveLayer();
            if (activeLayer != null) {
                // there's an active layer - select it and make it visible
                int idx = layers.indexOf(activeLayer);
                selectionModel.setSelectionInterval(idx, idx);
                ensureSelectedIsVisible();
            } else {
                // no active layer - select the first one and make it visible
                selectionModel.setSelectionInterval(0, 0);
                ensureSelectedIsVisible();
            }
        }

        /**
         * Replies the active layer. null, if no active layer is available
         *
         * @return the active layer. null, if no active layer is available
         */
        protected Layer getActiveLayer() {
            return getLayerManager().getActiveLayer();
        }

        /**
         * Replies the scale layer. null, if no active layer is available.
         *
         * @return the scale layer. null, if no active layer is available
         * @deprecated Deprecated since it is unused in JOSM and does not really belong here. Can be removed soon (August 2016).
         *             You can directly query MapView.
         */
        @Deprecated
        protected NativeScaleLayer getNativeScaleLayer() {
            return Main.isDisplayingMapView() ? Main.map.mapView.getNativeScaleLayer() : null;
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface TableModel                                                           */
        /* ------------------------------------------------------------------------------ */

        @Override
        public int getRowCount() {
            List<Layer> layers = getLayers();
            return layers == null ? 0 : layers.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public Object getValueAt(int row, int col) {
            List<Layer> layers = getLayers();
            if (row >= 0 && row < layers.size()) {
                switch (col) {
                case 0: return layers.get(row) == getActiveLayer();
                case 1:
                case 2:
                case 3: return layers.get(row);
                default: // Do nothing
                }
            }
            return null;
        }

        @Override
        public boolean isCellEditable(int row, int col) {
            if (col == 0 && getActiveLayer() == getLayers().get(row))
                return false;
            return true;
        }

        @Override
        public void setValueAt(Object value, int row, int col) {
            List<Layer> layers = getLayers();
            if (row < layers.size()) {
                Layer l = layers.get(row);
                switch (col) {
                case 0:
                    getLayerManager().setActiveLayer(l);
                    l.setVisible(true);
                    break;
                case 1:
                    NativeScaleLayer oldLayer = Main.map.mapView.getNativeScaleLayer();
                    if (oldLayer == l) {
                        Main.map.mapView.setNativeScaleLayer(null);
                    } else if (l instanceof NativeScaleLayer) {
                        Main.map.mapView.setNativeScaleLayer((NativeScaleLayer) l);
                        if (oldLayer != null) {
                            int idx = getLayers().indexOf(oldLayer);
                            if (idx >= 0) {
                                fireTableCellUpdated(idx, col);
                            }
                        }
                    }
                    break;
                case 2:
                    l.setVisible((Boolean) value);
                    break;
                case 3:
                    l.rename((String) value);
                    break;
                default: throw new RuntimeException();
                }
                fireTableCellUpdated(row, col);
            }
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface ActiveLayerChangeListener                                            */
        /* ------------------------------------------------------------------------------ */
        @Override
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
            Layer oldLayer = e.getPreviousActiveLayer();
            if (oldLayer != null) {
                int idx = getLayers().indexOf(oldLayer);
                if (idx >= 0) {
                    fireTableRowsUpdated(idx, idx);
                }
            }

            Layer newLayer = getActiveLayer();
            if (newLayer != null) {
                int idx = getLayers().indexOf(newLayer);
                if (idx >= 0) {
                    fireTableRowsUpdated(idx, idx);
                }
            }
            ensureActiveSelected();
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface LayerChangeListener                                                  */
        /* ------------------------------------------------------------------------------ */
        @Override
        public void layerAdded(LayerAddEvent e) {
            onAddLayer(e.getAddedLayer());
        }

        @Override
        public void layerRemoving(LayerRemoveEvent e) {
            onRemoveLayer(e.getRemovedLayer());
        }

        @Override
        public void layerOrderChanged(LayerOrderChangeEvent e) {
            // ignored for now, since only we change layer order.
        }

        /* ------------------------------------------------------------------------------ */
        /* Interface PropertyChangeListener                                               */
        /* ------------------------------------------------------------------------------ */
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getSource() instanceof Layer) {
                Layer layer = (Layer) evt.getSource();
                final int idx = getLayers().indexOf(layer);
                if (idx < 0)
                    return;
                fireRefresh();
            }
        }
    }

    /**
     * This component displays a list of layers and provides the methods needed by {@link LayerListModel}.
     */
    static class LayerList extends JTable {
        private final transient MainLayerManager layerManager;

        LayerList(LayerListModel dataModel, MainLayerManager layerManager) {
            super(dataModel);
            this.layerManager = layerManager;
            dataModel.setLayerList(this);
        }

        public void scrollToVisible(int row, int col) {
            if (!(getParent() instanceof JViewport))
                return;
            JViewport viewport = (JViewport) getParent();
            Rectangle rect = getCellRect(row, col, true);
            Point pt = viewport.getViewPosition();
            rect.setLocation(rect.x - pt.x, rect.y - pt.y);
            viewport.scrollRectToVisible(rect);
        }

        /**
         * Gets you the layer manager used for this list.
         * @return The layer manager.
         * @since xxx
         */
        public MainLayerManager getLayerManager() {
            return layerManager;
        }
    }

    /**
     * Creates a {@link ShowHideLayerAction} in the context of this {@link LayerListDialog}.
     *
     * @return the action
     */
    public ShowHideLayerAction createShowHideLayerAction() {
        return new ShowHideLayerAction(model);
    }

    /**
     * Creates a {@link DeleteLayerAction} in the context of this {@link LayerListDialog}.
     *
     * @return the action
     */
    public DeleteLayerAction createDeleteLayerAction() {
        return new DeleteLayerAction(model);
    }

    /**
     * Creates a {@link ActivateLayerAction} for <code>layer</code> in the context of this {@link LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public ActivateLayerAction createActivateLayerAction(Layer layer) {
        return new ActivateLayerAction(layer, model);
    }

    /**
     * Creates a {@link MergeLayerAction} for <code>layer</code> in the context of this {@link LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public MergeAction createMergeLayerAction(Layer layer) {
        return new MergeAction(layer, model);
    }

    /**
     * Creates a {@link DuplicateAction} for <code>layer</code> in the context of this {@link LayerListDialog}.
     *
     * @param layer the layer
     * @return the action
     */
    public DuplicateAction createDuplicateLayerAction(Layer layer) {
        return new DuplicateAction(layer, model);
    }

    /**
     * Returns the layer at given index, or {@code null}.
     * @param index the index
     * @return the layer at given index, or {@code null} if index out of range
     */
    public static Layer getLayerForIndex(int index) {
        List<Layer> layers = Main.getLayerManager().getLayers();

        if (index < layers.size() && index >= 0)
            return layers.get(index);
        else
            return null;
    }

    /**
     * Returns a list of info on all layers of a given class.
     * @param layerClass The layer class. This is not {@code Class<? extends Layer>} on purpose,
     *                   to allow asking for layers implementing some interface
     * @return list of info on all layers assignable from {@code layerClass}
     */
    public static List<MultikeyInfo> getLayerInfoByClass(Class<?> layerClass) {
        List<MultikeyInfo> result = new ArrayList<>();

        List<Layer> layers = Main.getLayerManager().getLayers();

        int index = 0;
        for (Layer l: layers) {
            if (layerClass.isAssignableFrom(l.getClass())) {
                result.add(new MultikeyInfo(index, l.getName()));
            }
            index++;
        }

        return result;
    }

    /**
     * Determines if a layer is valid (contained in global layer list).
     * @param l the layer
     * @return {@code true} if layer {@code l} is contained in current layer list
     */
    public static boolean isLayerValid(Layer l) {
        if (l == null)
            return false;

        return Main.getLayerManager().containsLayer(l);
    }

    /**
     * Returns info about layer.
     * @param l the layer
     * @return info about layer {@code l}
     */
    public static MultikeyInfo getLayerInfo(Layer l) {
        if (l == null)
            return null;

        int index = Main.getLayerManager().getLayers().indexOf(l);
        if (index < 0)
            return null;

        return new MultikeyInfo(index, l.getName());
    }
}
