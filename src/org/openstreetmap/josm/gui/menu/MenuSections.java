// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.menu;
import static org.openstreetmap.josm.gui.help.HelpUtil.ht;
import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.marktrc;
import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trc;

import java.awt.event.KeyEvent;
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
    public static final FileMenu FILE = new FileMenu();
    public static final EditMenu EDIT = new EditMenu();
    public static final ViewMenu VIEW = new ViewMenu();
    public static final ToolsMenu TOOLS = new ToolsMenu();
    public static final MoreToolsMenu MORE_TOOLS = new MoreToolsMenu();
    public static final DataMenu DATA = new DataMenu();
    public static final SelectionMenu SELECTION = new SelectionMenu();
    public static final PresetsMenu PRESETS = new PresetsMenu();
    public static final ImageryMenu IMAGERY = new ImageryMenu();
    public static final GpsMenu GPS = new GpsMenu();
    public static final WindowMenu WINDOWS = new WindowMenu();
    public static final HelpMenu HELP = new HelpMenu();

    private MenuSections() {
        // hide.
    }

    /**
     * Get a list of all menus.
     * @return The menus
     */
    public static List<IMenu> getMenus() {
        return Arrays.asList(FILE, EDIT, VIEW, TOOLS, MORE_TOOLS, DATA, SELECTION, PRESETS, IMAGERY, GPS, WINDOWS, HELP);
    }

    /**
     * The file menu. Use the {@link MenuSections#FILE} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class FileMenu extends Menu {
        FileMenu() {
            super(/* I18N: mnemonic: F */marktrc("menu", "File"), KeyEvent.VK_F, 0, ht("/Menu/File"));
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
        EditMenu() {
            super(/* I18N: mnemonic: E */marktrc("menu", "Edit"), KeyEvent.VK_E, 1, ht("/Menu/Edit"));
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
        ViewMenu() {
            super(/* I18N: mnemonic: V */ marktrc("menu", "View"), KeyEvent.VK_V, 2, ht("/Menu/View"));
        }

        public final IMenuSection STYLES = new MenuSection(marktr("Styles"));
        public final IMenuSection ZOOM = new MenuSection(marktr("Zoom"));
        public final IMenuSection ZOOM_TO = new MenuSection(marktr("Zoom to"));
        public final IMenuSection VIEWPORT = new MenuSection(marktr("Viewport"));
        public final IMenuSection INFO = new MenuSection(marktr("Info"));
        public final IMenuSection TOOLBARS = new MenuSection(marktr("Toolbars"));
    }

    /**
     * The tools menu. Use the {@link MenuSections#TOOLS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class ToolsMenu extends Menu {
        ToolsMenu() {
            super(/* I18N: mnemonic: T */ marktrc("menu", "Tools"), KeyEvent.VK_T, 3, ht("/Menu/Tools"));
        }

        public final IMenuSection SEC_1 = new MenuSection(marktr("1"));
        public final IMenuSection SEC_2 = new MenuSection(marktr("2"));
        public final IMenuSection SEC_3 = new MenuSection(marktr("3"));
        public final IMenuSection SEC_4 = new MenuSection(marktr("4"));
        public final IMenuSection SEC_5 = new MenuSection(marktr("5"));
        public final IMenuSection SEC_6 = new MenuSection(marktr("6"));
    }

    /**
     * The more tools menu. Use the {@link MenuSections#MORE_TOOLS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class MoreToolsMenu extends Menu {
        MoreToolsMenu() {
            super(/* I18N: mnemonic: M */ marktrc("menu", "More tools"), KeyEvent.VK_M, 4, ht("/Menu/MoreTools"));
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
        DataMenu() {
            super(/* I18N: mnemonic: D */ marktrc("menu", "Data"), KeyEvent.VK_D, 5, ht("/Menu/Data"));
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    /**
     * The selection menu. Use the {@link MenuSections#SELECTION} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class SelectionMenu extends Menu {
        SelectionMenu() {
            super(/* I18N: mnemonic: N */ marktrc("menu", "Selection"), KeyEvent.VK_N, 6, ht("/Menu/Selection"));
        }

        public final IMenuSection SELECT = new MenuSection(marktr("Select"));
        public final IMenuSection FILTER = new MenuSection(marktr("Filter"));
    }

    /**
     * The presets menu. Use the {@link MenuSections#PRESETS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class PresetsMenu extends Menu {
        PresetsMenu() {
            super(/* I18N: mnemonic: P */ marktrc("menu", "Presets"), KeyEvent.VK_P, 7, ht("/Menu/Presets"));
        }

        public final IMenuSection OPTIONS = new MenuSection(marktr("Options"));
        public final IMenuSection PRESETS = new MenuSection(marktr("Presets"));
    }

    /**
     * The imagery menu. Use the {@link MenuSections#IMAGERY} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class ImageryMenu extends Menu {
        ImageryMenu() {
            super(/* I18N: mnemonic: I */ marktrc("menu", "Imagery"), KeyEvent.VK_P, 7, ht("/Menu/Imagery"));
        }

        public final IMenuSection OPTIONS = new MenuSection(marktr("Options"));
    }

    /**
     * The gps menu. Use the {@link MenuSections#GPS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class GpsMenu extends Menu {
        GpsMenu() {
            super(/* I18N: mnemonic: G */ marktrc("menu", "GPS"), KeyEvent.VK_G, 9, ht("/Menu/GPS"));
        }

        public final IMenuSection UNDO_REDO = new MenuSection(marktr("Commands"));
        public final IMenuSection CLIPBOARD = new MenuSection(marktr("Clipboard"));
        public final IMenuSection SEARCH = new MenuSection(marktr("Search"));
        public final IMenuSection PREFERENCES = new MenuSection(marktr("Preferences"));
    }

    /**
     * The window menu. Use the {@link MenuSections#WINDOWS} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class WindowMenu extends Menu {
        WindowMenu() {
            super(/* I18N: mnemonic: W */ marktrc("menu", "Windows"), KeyEvent.VK_W, 10, ht("/Menu/Windows"));
        }

        public final IMenuSection ALWAYS = new MenuSection(marktr("Window"));
        public final IMenuSection TOGGLE_DIALOG = new MenuSection(marktr("Dialogs"));
        public final IMenuSection VOLATILE = new MenuSection(marktr("More"));
    }

    /**
     * The help menu. Use the {@link MenuSections#HELP} constant to access
     * @author Michael Zangl
     * @since xxx
     */
    public static final class HelpMenu extends Menu {
        HelpMenu() {
            super(/* I18N: mnemonic: H */ trc("menu", "Help"), KeyEvent.VK_H, 11, ht("/Menu/Help"));
        }

        public final IMenuSection MENU = new MenuSection(marktr("Menu"));
        public final IMenuSection BUG = new MenuSection(marktr("Report Bugs"));
        public final IMenuSection HELP = new MenuSection(marktr("Help"));
    }

    protected static class Menu implements IMenu {

        private String id;

        Menu(String id, int vkF, int i, String string) {
            this.id = id;
        }

        @Override
        public String getSectionName() {
            return trc("menu", id);
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
