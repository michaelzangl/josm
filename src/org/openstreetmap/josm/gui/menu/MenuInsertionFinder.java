// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import javax.swing.JMenu;

import org.openstreetmap.josm.gui.menu.JosmMenu.IMenuSection;
import org.openstreetmap.josm.tools.Pair;

/**
 * This class finds the position to insert the menu at.
 * @author Michael Zangl
 * @since xxx
 */
public class MenuInsertionFinder {
    /**
     * Adds the menu item nowhere.
     */
    public static final MenuInsertionFinder NONE = new MenuInsertionFinder();

    /**d
     * Insert at the last position.
     */
    public static final MenuInsertionFinder LAST = NONE.atLast();

    /**
     * This menu insertion finder won't return any insertion position.
     * <p>
     * It will fall back to a guessed position (usually the last) when no other rules are added.
     */
    public static final MenuInsertionFinder DEFAULT = LAST; // TODO: Section tools

    /**
     * Insert at the first position.
     */
    public static final MenuInsertionFinder FIRST = NONE.at(0);

    private final ArrayList<MenuInsertionFinder.InsertionPolicy> policies = new ArrayList<>();

    private MenuInsertionFinder() {
    }

    private MenuInsertionFinder(MenuInsertionFinder base, InsertionPolicy p) {
        policies.add(p);
        policies.addAll(base.policies);
    }

    /**
     * Note to insert the current component after a given menu item.
     * @param menuItem The menu item
     * @return The new insertion finder with the rule added.
     */
    public MenuInsertionFinder after(Component menuItem) {
        return add(new AfterInsertionPolicy(menuItem::equals));
    }

    /**
     * Note to insert the current component before a given menu item.
     * @param menuItem The menu item
     * @return The new insertion finder with the rule added.
     */
    public MenuInsertionFinder before(Component menuItem) {
        return add(new BeforeInsertionPolicy(menuItem::equals));
    }

    /**
     * Append as last item of the group.
     * @param group The group to search for.
     * @return The new insertion finder with the rule added.
     */
    public MenuInsertionFinder inGroup(Enum<?> group) {
        return add(new InGroupInsertionPolicy(group.ordinal()));
    }

    /**
     * Append at the given position
     * @param position to insert at
     * @return The new insertion finder with the rule added.
     */
    public MenuInsertionFinder at(int position) {
        return add(menu -> menu.getComponentCount() >= position ? new MenuInsertionPoint(position, false) : null);
    }

    protected MenuInsertionFinder add(InsertionPolicy p) {
        return new MenuInsertionFinder(this, p);
    }

    /**
     * Add to the end of the section.
     * @param section The section to search for.
     * @return The section to add this to.
     */
    public MenuInsertionFinder in(IMenuSection section) {
        return add(new InSectionInsertionPolicy(section.getSectionId()));
    }

    /**
     * Find the suggested insertion point inside the menu.
     * @param menu The menu to search in
     * @return The point
     */
    public MenuInsertionPoint findInsertionPoint(Container menu) {
        for (MenuInsertionFinder.InsertionPolicy p : policies) {
            MenuInsertionPoint i = p.getInsertionPoint(menu);
            if (i != null) {
                return i;
            }
        }
        return null;
    }

    /**
     * Find the suggested insertion point inside the menu.
     * @param <T> The menu type.
     * @param menus The menus to search in
     * @return The point
     */
    public <T extends JMenu> Pair<T, MenuInsertionPoint> findInsertionPoint(List<T> menus) {
        for (MenuInsertionFinder.InsertionPolicy p : policies) {
            for (T m : menus) {
                MenuInsertionPoint i = p.getInsertionPoint(m.getPopupMenu());
                if (i != null) {
                    return new Pair<>(m, i);
                }
            }
        }
        return null;
    }

    private MenuInsertionFinder atLast() {
        return add(menu -> new MenuInsertionPoint(menu.getComponentCount(), false));
    }

    @FunctionalInterface
    private interface InsertionPolicy {
        /**
         * Get the suggested insertion point
         * @param menu The menu to search in
         * @return The point. No point if no such point is found.
         */
        public MenuInsertionPoint getInsertionPoint(Container menu);

//        default public MenuInsertionPoint getInsertionPointRecursive(Container menu) {
//            MenuInsertionPoint p = getInsertionPoint(menu);
//            for (int i = 0; i < menu.getComponentCount() && p == null; i++) {
//                 Component component = menu.getComponent(i);
//                 if (component instanceof Container) {
//                     p = getInsertionPointRecursive((Container) component);
//                 }
//            }
//            return p;
//        }
    }

    private static class BeforeInsertionPolicy implements InsertionPolicy {
        private final Predicate<Component> beforeWhat;

        public BeforeInsertionPolicy(Predicate<Component> beforeWhat) {
            super();
            this.beforeWhat = beforeWhat;
        }

        @Override
        public MenuInsertionPoint getInsertionPoint(Container menu) {
            for (int i = 0; i < menu.getComponentCount(); i++) {
                if (beforeWhat.test(menu.getComponent(i))) {
                    return new MenuInsertionPoint(getOffset(i), false);
                }
            }
            return null;
        }

        protected int getOffset(int i) {
            return i;
        }
    }

    private static class AfterInsertionPolicy extends BeforeInsertionPolicy {
        public AfterInsertionPolicy(Predicate<Component> afterWhat) {
            super(afterWhat);
        }

        @Override
        protected int getOffset(int i) {
            return i + 1;
        }
    }

    private static class InSectionInsertionPolicy implements InsertionPolicy {

        private String sectionId;

        public InSectionInsertionPolicy(String sectionId) {
            this.sectionId = sectionId;
        }

        @Override
        public MenuInsertionPoint getInsertionPoint(Container menu) {
            boolean found = false;
            for (int i = 0; i < menu.getComponentCount(); i++) {
                Component component = menu.getComponent(i);
                if (component instanceof JosmMenuSection) {
                    if (found ) {
                        // we are in next section, return last.
                        return new MenuInsertionPoint(i, false);
                    } else {
                        found = sectionId.equals(((JosmMenuSection) component).getSectionId());
                    }
                }
            }

            if (found || (menu instanceof JosmMenu && sectionId.equals(((JosmMenu) menu).getMenuId()))) {
                return new MenuInsertionPoint(menu.getComponentCount(), false);
            } else {
                return null;
            }
        }

    }

    private static class InGroupInsertionPolicy implements InsertionPolicy {

        private int groupIndex;

        public InGroupInsertionPolicy(int groupIndex) {
            this.groupIndex = groupIndex;
        }

        @Override
        public MenuInsertionPoint getInsertionPoint(Container menu) {
            int inGroup = 0;
            for (int i = 0; i < menu.getComponentCount(); i++) {
                if (menu.getComponent(i) == null || menu.getComponent(i) instanceof JosmMenuSection) {
                    if  (inGroup >= groupIndex) {
                        return new MenuInsertionPoint(i, false);
                    }
                    inGroup++;
                }
            }

            return new MenuInsertionPoint(menu.getComponentCount(), inGroup < groupIndex);
        }

    }

    /**
     * Defines a menu insertion point
     * @author Michael Zangl
     * @since xxx
     */
    public static class MenuInsertionPoint {
        private final int insertPosition;
        private final boolean addSeparatorBefore;
        MenuInsertionPoint(int insertPosition, boolean addSeparatorBefore) {
            this.insertPosition = insertPosition;
            this.addSeparatorBefore = addSeparatorBefore;
        }

        /**
         * @return the insertPosition
         */
        public int getInsertPosition() {
            return insertPosition;
        }


        /**
         * @return if a separator should be added before the menu item.
         */
        public boolean isAddSeparatorBefore() {
            return addSeparatorBefore;
        }


        @Override
        public String toString() {
            return "MenuInsertionPoint [insertPosition=" + insertPosition + ", addSeparatorBefore=" + addSeparatorBefore
                    + "]";
        }

    }
}