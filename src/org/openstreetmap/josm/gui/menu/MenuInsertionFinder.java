// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.function.Predicate;

/**
 * This class finds the position to insert the menu at.
 * @author Michael Zangl
 * @since xxx
 */
public class MenuInsertionFinder {

    /**
     * Insert at the last position.
     */
    public static MenuInsertionFinder LAST = new MenuInsertionFinder() {
        @Override
        protected void add(InsertionPolicy p) {
            // ignored.
        }
    };

    /**
     * Insert at the first position.
     */
    public static MenuInsertionFinder FIRST = new MenuInsertionFinder() {
        @Override
        protected void add(InsertionPolicy p) {
            // ignored.
        }
    };

    private final ArrayList<MenuInsertionFinder.InsertionPolicy> policies = new ArrayList<>();

    /**
     * Note to insert the current component after a given menu item.
     * @param menuItem The menu item
     * @return this for easy chaining.
     */
    public MenuInsertionFinder after(Component menuItem) {
        add(new AfterInsertionPolicy(menuItem::equals));
        return this;
    }

    /**
     * Note to insert the current component before a given menu item.
     * @param menuItem The menu item
     * @return this for easy chaining.
     */
    public MenuInsertionFinder before(Component menuItem) {
        add(new BeforeInsertionPolicy(menuItem::equals));
        return this;
    }

    /**
     * Append as last item of the group.
     * @param group The group to search for.
     * @return this for easy chaining.
     */
    public MenuInsertionFinder inGroup(Enum<?> group) {
        add(new InGroupInsertionPolicy(group.ordinal()));
        return this;
    }

    /**
     * Append at the given position
     * @param position to insert at
     * @return this for easy chaining.
     */
    public MenuInsertionFinder at(int position) {
        add(menu -> menu.getComponentCount() >= position ? new MenuInsertionPoint(position, false) : null);
        return this;
    }

    protected void add(InsertionPolicy p) {
        policies.add(p);
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
        return new MenuInsertionPoint(menu.getComponentCount(), false);
    }

    @FunctionalInterface
    private interface InsertionPolicy {
        /**
         * Get the suggested insertion point
         * @param menu The menu to search in
         * @return The point. No point if no such point is found.
         */
        public MenuInsertionPoint getInsertionPoint(Container menu);
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

    private static class InGroupInsertionPolicy implements InsertionPolicy {

        private int groupIndex;

        public InGroupInsertionPolicy(int groupIndex) {
            this.groupIndex = groupIndex;
        }

        @Override
        public MenuInsertionPoint getInsertionPoint(Container menu) {
            int inGroup = 0;
            for (int i = 0; i < menu.getComponentCount(); i++) {
                if (menu.getComponent(i) == null) {
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