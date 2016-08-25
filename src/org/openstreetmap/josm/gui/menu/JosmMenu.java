// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagLayout;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.HierarchyEvent;
import java.beans.PropertyChangeListener;
import java.io.Serializable;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JPopupMenu.Separator;
import javax.swing.JSeparator;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.gui.MenuScroller;
import org.openstreetmap.josm.gui.menu.MenuInsertionFinder.MenuInsertionPoint;
import org.openstreetmap.josm.gui.menu.search.SearchReceiver;
import org.openstreetmap.josm.gui.menu.search.Searchable;
import org.openstreetmap.josm.tools.GBC;

/**
 * This is an extension of a {@link JMenu} that allows the addition of {@link JosmAction}
 * @author Michael Zangl
 * @since xxx
 */
public class JosmMenu extends JMenu implements Searchable {

    private final String menuId;
    private final PropertyChangeListener enabledChangeListener = (PropertyChangeListener & Serializable) e -> updateVisibility();

    private final ContainerListener containerListener = new MenuContainerListener();

    /**
     * Create a new JosmMenu
     * @param blueprint The structure to populate the menu with.
     */
    public JosmMenu(IMenu blueprint) {
        this(blueprint.getSectionId(), blueprint.getSectionName());

        for (IMenuSection s : blueprint.getSections()) {
            addSection(s, MenuInsertionFinder.DEFAULT);
        }
    }

    /**
     * Create a new {@link JosmMenu}
     * @param menuId The internal identifier name of the menu
     * @param translatedName The human readable name
     */
    public JosmMenu(String menuId, String translatedName) {
        super(translatedName);
        this.menuId = menuId;
        getPopupMenu().setLayout(new MenuLayout());
        getPopupMenu().addHierarchyListener(e -> {
            if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                //TODO: This does not detect visibility events if the menu is hidden :-(.
                updateVisibility();
            }
        });
        getPopupMenu().addContainerListener(containerListener);
        setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        if (!GraphicsEnvironment.isHeadless()) {
            MenuScroller.setScrollerFor(this);
        }
        //TODO: getPopupMenu().setMinimumSize(new Dimension(200, 10));
    }

    @Override
    public void doLayout() {
        updateVisibility();
        super.doLayout();
    }

    private void updateVisibility() {
        boolean visible = false;
        JPopupMenu popupMenu = getPopupMenu();
        int nmembers = popupMenu.getComponentCount();
        for (int i = 0; i < nmembers && !visible; i++) {
            Component comp = popupMenu.getComponent(i);
            if (!isSection(comp) && comp.isVisible() && comp.isEnabled()) {
                visible = true;
            }
        }
        setEnabled(visible);
    }

    /**
     * Add the given action to the menu.
     * @param actionToBeInserted The action
     * @param insertionPosition The position to insert it at.
     * @return The new menu item.
     */
    public JosmMenuReference add(JosmAction actionToBeInserted, MenuInsertionFinder insertionPosition) {
        JosmMenuReference item = actionToBeInserted.createMenuItem();
        add(item.getMenuComponent(), insertionPosition);
        return item;
    }

    /**
     * Add the given sub menu to the menu.
     * @param menuToInsert The menu
     * @param insertionPosition The position to insert it at.
     */
    public void add(JosmMenu menuToInsert, MenuInsertionFinder insertionPosition) {
        add((Component) menuToInsert, insertionPosition);
    }

    /**
     * Add a new named menu separator.
     * @param sectionData The id and name of the section
     * @param insertionPosition The position to insert the section at.
     */
    public void addSection(IMenuSection sectionData, MenuInsertionFinder insertionPosition) {
        add(new JosmMenuSection(sectionData.getSectionId(), sectionData.getSectionName()), insertionPosition);
    }

    /**
     * Add a new named menu separator.
     * @param sectionId The universal id
     * @param sectionName The name of the section
     * @param insertionPosition The position to insert the section at.
     */
    public void addSection(String sectionId, String sectionName, MenuInsertionFinder insertionPosition) {
        add(new JosmMenuSection(sectionId, sectionName), insertionPosition);
    }

    protected void add(Component c, MenuInsertionFinder position) {
        MenuInsertionPoint pos = position.findInsertionPoint(getPopupMenu());
        int index = pos.getInsertPosition();
        if (pos.isAddSeparatorBefore()) {
            JSeparator sep = new JSeparator();
            add(sep, index);
            index++;
        }
        add(c, index);
        updateVisibility();
    }

    /**
     * Get the id of this menu.
     * @return The id of the menu.
     */
    public String getMenuId() {
        return menuId;
    }

    @Override
    public void search(SearchReceiver sr) {
        sr.autoRecurse(getPopupMenu().getComponents());
    }

    private final class MenuContainerListener implements ContainerListener, Serializable {
        @Override
        public void componentAdded(ContainerEvent e) {
            e.getChild().addPropertyChangeListener("enabled", enabledChangeListener);

            if (e.getChild() instanceof Container) {
                ((Container) e.getChild()).addContainerListener(containerListener);
            }
            updateVisibility();
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
            e.getChild().removePropertyChangeListener("enabled", enabledChangeListener);

            if (e.getChild() instanceof Container) {
                ((Container) e.getChild()).removeContainerListener(containerListener);
            }
            updateVisibility();
        }
    }

    /**
     * A layout manager that automatically handles the visibility of separators.
     * @author Michael Zangl
     * @since xxx
     */
    private class MenuLayout extends GridBagLayout {

        @Override
        public void addLayoutComponent(Component comp, Object constraints) {
            if (comp instanceof JSeparator) {
                comp.setMinimumSize(new Dimension(100, 100));
            }
            super.addLayoutComponent(comp, GBC.eol().fill(GBC.HORIZONTAL));
        }

        @Override
        public void layoutContainer(Container target) {
            updateVisibleState(target);
            super.layoutContainer(target);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            updateVisibleState(target);
            return super.minimumLayoutSize(target);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            updateVisibleState(target);
            return super.preferredLayoutSize(target);
        }

        private void updateVisibleState(Container target) {
            int nmembers = target.getComponentCount();
            Component prev = null;
            for (int i = 0; i < nmembers; i++) {
                Component comp = target.getComponent(i);
                boolean isSeparator = isSection(comp);
                if (prev != null) {
                    prev.setVisible(!isSeparator);
                }
                if (isSeparator) {
                    prev = comp;
                } else if (comp.isVisible()) {
                    prev = null;
                }
            }

            if (prev != null) {
                // separators at end of menu
                for (int i = nmembers - 1; i >= 0; i--) {
                    Component comp = target.getComponent(i);
                    if (isSection(comp)) {
                        comp.setVisible(false);
                    } else if (comp.isVisible()) {
                        break;
                    }
                }
            }
        }
    }

    private static boolean isSection(Component comp) {
        return comp instanceof Separator || comp instanceof JosmMenuSection;
    }

    /**
     * A helper interface for menu sections. May be implemented by e.g. enums.
     * @author Michael Zangl
     * @since xxx
     */
    @FunctionalInterface
    public interface IMenuSection {
        /**
         * Gets the id of the section. This should be the english name.
         * @return the id
         */
        String getSectionId();

        /**
         * Gets the localized name of the section
         * @return the label
         */
        default String getSectionName() {
            return tr(getSectionId());
        }

        default MenuInsertionFinder pos() {
            return MenuInsertionFinder.DEFAULT.in(this);
        }
    }

    /**
     * The definition of a menu and it's sub menu.
     * @author Michael Zangl
     * @since xxx
     */
    public interface IMenu extends IMenuSection {
        /**
         * Get all sub sections of this item.
         * @return The sub sections.
         */
        List<IMenuSection> getSections();
    }
}
