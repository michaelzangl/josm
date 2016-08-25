// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.awt.event.HierarchyEvent;
import java.awt.event.HierarchyListener;
import java.beans.PropertyChangeListener;

import javax.swing.JMenu;
import javax.swing.JPopupMenu;
import javax.swing.JSeparator;

import org.openstreetmap.josm.actions.JosmAction;
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
    private final PropertyChangeListener enabledChangeListener = e -> updateVisibility();

    private final ContainerListener containerListener = new ContainerListener() {

        @Override
        public void componentAdded(ContainerEvent e) {
            e.getChild().addPropertyChangeListener("enabled", enabledChangeListener);

            if (e.getChild() instanceof Container) {
                ((Container) e.getChild()).addContainerListener(containerListener);
            }
        }

        @Override
        public void componentRemoved(ContainerEvent e) {
            e.getChild().removePropertyChangeListener("enabled", enabledChangeListener);

            if (e.getChild() instanceof Container) {
                ((Container) e.getChild()).removeContainerListener(containerListener);
            }
        }
    };

    /**
     * Create a new {@link JosmMenu}
     * @param menuId The internal identifier name of the menu
     * @param translatedName The human readable name
     */
    public JosmMenu(String menuId, String translatedName) {
        super(translatedName);
        this.menuId = menuId;
        getPopupMenu().setLayout(new MenuLayout());
        getPopupMenu().addHierarchyListener(new HierarchyListener() {
            @Override
            public void hierarchyChanged(HierarchyEvent e) {
                if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                    updateVisibility();
                }
            }
        });
        getPopupMenu().addContainerListener(containerListener);
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
            if (!(comp instanceof JSeparator) && comp.isVisible() && comp.isEnabled()) {
                visible = true;
            }
        }
        setEnabled(visible);
    }

    /**
     * Add the given action to the menu.
     * @param actionToBeInserted The action
     * @param insertionPosition The position to insert it at.
     */
    public void add(JosmAction actionToBeInserted, MenuInsertionFinder insertionPosition) {

    }

    @Override
    public void search(SearchReceiver sr) {
        sr.autoRecurse(getPopupMenu().getComponents());
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
            boolean wasSeparator = true; // <- hides separators in front
            for (int i = 0; i < nmembers; i++) {
                Component comp = target.getComponent(i);
                if (comp instanceof JSeparator) {
                    comp.setVisible(!wasSeparator);
                    wasSeparator = true;
                } else if (comp.isVisible()) {
                    wasSeparator = false;
                }
            }

            if (wasSeparator) {
                // separators at end of menu
                for (int i = nmembers - 1; i >= 0; i--) {
                    Component comp = target.getComponent(i);
                    if (comp instanceof JSeparator) {
                        comp.setVisible(false);
                    } else if (comp.isVisible()) {
                        break;
                    }
                }
            }
        }
    }
}
