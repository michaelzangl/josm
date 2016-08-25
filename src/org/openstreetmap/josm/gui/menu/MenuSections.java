// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.menu.JosmMenu.IMenu;
import org.openstreetmap.josm.gui.menu.JosmMenu.IMenuSection;

/**
 * This gets you the menu section.
 * @author Michael Zangl
 * @since xxx
 */
public final class MenuSections {
    public static final FileMenu FILE = new FileMenu(marktr("File"));
    public static final EditMenu EDIT = new EditMenu(marktr("Edit"));
    public static final ViewMenu VIEW = new ViewMenu(marktr("View"));
    public static final ToolsMenu TOOLS = new ToolsMenu(marktr("Tools"));
    public static final MoreToolsMenu MORE_TOOLS = new MoreToolsMenu(marktr("More Tools"));
    public static final DataMenu DATA = new DataMenu(marktr("Data"));

    private MenuSections() {
        // hide.
    }

    /**
     * Get a list of all menus.
     * @return The menus
     */
    public static List<IMenu> getMenus() {
        return Arrays.asList(FILE, EDIT, VIEW, TOOLS, MORE_TOOLS, DATA);
    }

    /**
     * The file menu. Use the {@link MenuSections#FILE} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class FileMenu extends Menu {
        FileMenu(String id) {
            super(id);
        }

        public final IMenuSection LAYER = new MenuSection(marktr("Layer"));
        public final IMenuSection SAVE = new MenuSection(marktr("Save"));
        public final IMenuSection DOWNLOAD = new MenuSection(marktr("Download"));
        public final IMenuSection UPLOAD = new MenuSection(marktr("Upload"));
        public final IMenuSection SYSTEM = new MenuSection(marktr("System"));
    }


    /**
     * The edit menu. Use the {@link MenuSections#EDIT} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class EditMenu extends Menu {
        EditMenu(String id) {
            super(id);
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    /**
     * The view menu. Use the {@link MenuSections#VIEW} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class ViewMenu extends Menu {
        ViewMenu(String id) {
            super(id);
        }

        public final IMenuSection STYLES = new MenuSection(marktr("Styles"));
        public final IMenuSection ZOOM = new MenuSection(marktr("Zoom"));
        public final IMenuSection ZOOM_TO = new MenuSection(marktr("Zoom to"));
        public final IMenuSection VIEWPORT = new MenuSection(marktr("Viewport"));
    }

    /**
     * The tools menu. Use the {@link MenuSections#TOOLS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class ToolsMenu extends Menu {
        ToolsMenu(String id) {
            super(id);
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    /**
     * The more tools menu. Use the {@link MenuSections#MORE_TOOLS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class MoreToolsMenu extends Menu {
        MoreToolsMenu(String id) {
            super(id);
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    /**
     * The data menu. Use the {@link MenuSections#DATA} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class DataMenu extends Menu {
        DataMenu(String id) {
            super(id);
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    protected static class Menu implements IMenu {

        private String id;

        Menu(String id) {
            this.id = id;
        }

        @Override
        public String getSectionId() {
            return id;
        }

        public List<IMenuSection> getSections() {
           return Stream.of(getClass().getFields()).map(this::getSectionField).filter(Objects::nonNull).collect(Collectors.toList());
        }

        private IMenuSection getSectionField(Field f) {
            try {
                Object obj = f.get(this);
                if (obj instanceof IMenuSection) {
                    return (IMenuSection) obj;
                }
            } catch (SecurityException | IllegalArgumentException | IllegalAccessException e) {
                Main.warn(e);
            }

            return null;
        }

        final class MenuSection implements IMenuSection {
            private String sectionId;

            MenuSection(String id) {
                this.sectionId = id;
            }

            @Override
            public String getSectionId() {
                return id + "." + sectionId;
            }

            @Override
            public String getSectionName() {
                return tr(sectionId);
            }
        }
    }
}
