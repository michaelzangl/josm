// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.mapmode.SelectAction;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.gui.mapview.MapDisplayZoomHelper.ScrollMode;
import org.openstreetmap.josm.gui.mapview.NavigationModel;
import org.openstreetmap.josm.gui.util.CursorManager;
import org.openstreetmap.josm.tools.Destroyable;
import org.openstreetmap.josm.tools.Shortcut;

/**
 * Enables moving of the map by holding down the right mouse button and drag
 * the mouse. Also, enables zooming by the mouse wheel.
 *
 * @author imi
 */
public class MapMover extends MouseAdapter implements MouseMotionListener, MouseWheelListener, Destroyable {

    private final class ZoomerAction extends AbstractAction {
        private final String action;

        public ZoomerAction(String action) {
            this.action = action;
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (".".equals(action) || ",".equals(action)) {
                Point2D mouse = lastMousePosition;
                if (mouse == null)
                    mouse = nm.getState().getCenter().getOnScreen();
                MouseWheelEvent we = new MouseWheelEvent((Component) e.getSource(), e.getID(), e.getWhen(), e.getModifiers(), (int) mouse.getX(), (int) mouse.getY(), 0, false,

                        MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, ",".equals(action) ? -1 : 1);
                mouseWheelMoved(we);
            } else {
                double relativeX = .5;
                double relativeY = .5;
                switch(action) {
                case "left":
                    relativeX -= .2;
                    break;
                case "right":
                    relativeX += .2;
                    break;
                case "up":
                    relativeY -= .2;
                    break;
                case "down":
                    relativeY += .2;
                    break;
                }
                EastNorth newcenter = nm.getState().getPoint(new Point2D.Double(relativeX, relativeY)).getEastNorth();
                nm.getZoomHelper().zoomTo(newcenter, ScrollMode.IMMEDIATE);
            }
        }
    }

    /**
     * The point in the map that was the under the mouse point
     * when moving around started.
     */
    private EastNorth mousePosMove;
    /**
     * The map to move around.
     */
    private final NavigationModel nm;
    private final JPanel contentPane;

    private boolean movementInPlace = false;
    private final CursorManager cursorManager;

    private Point lastMousePosition = null;

    /**
     * Constructs a new {@code MapMover}.
     * @param navigationModel the navigatable component
     * @param cursorManager A cursor manager to which we should send cursor changes.
     * @param contentPane the content pane
     */
    public MapMover(NavigationModel navigationModel, CursorManager cursorManager, JPanel contentPane) {
        this.nm = navigationModel;
        this.cursorManager = cursorManager;
        this.contentPane = contentPane;

        if (contentPane != null) {
            // CHECKSTYLE.OFF: LineLength
            contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                Shortcut.registerShortcut("system:movefocusright", tr("Map: {0}", tr("Move right")), KeyEvent.VK_RIGHT, Shortcut.CTRL).getKeyStroke(),
                "MapMover.Zoomer.right");
            contentPane.getActionMap().put("MapMover.Zoomer.right", new ZoomerAction("right"));

            contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                Shortcut.registerShortcut("system:movefocusleft", tr("Map: {0}", tr("Move left")), KeyEvent.VK_LEFT, Shortcut.CTRL).getKeyStroke(),
                "MapMover.Zoomer.left");
            contentPane.getActionMap().put("MapMover.Zoomer.left", new ZoomerAction("left"));

            contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                Shortcut.registerShortcut("system:movefocusup", tr("Map: {0}", tr("Move up")), KeyEvent.VK_UP, Shortcut.CTRL).getKeyStroke(),
                "MapMover.Zoomer.up");
            contentPane.getActionMap().put("MapMover.Zoomer.up", new ZoomerAction("up"));

            contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                Shortcut.registerShortcut("system:movefocusdown", tr("Map: {0}", tr("Move down")), KeyEvent.VK_DOWN, Shortcut.CTRL).getKeyStroke(),
                "MapMover.Zoomer.down");
            contentPane.getActionMap().put("MapMover.Zoomer.down", new ZoomerAction("down"));
            // CHECKSTYLE.ON: LineLength

            // see #10592 - Disable these alternate shortcuts on OS X because of conflict with system shortcut
            if (!Main.isPlatformOsx()) {
                contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    Shortcut.registerShortcut("view:zoominalternate",
                            tr("Map: {0}", tr("Zoom in")), KeyEvent.VK_COMMA, Shortcut.CTRL).getKeyStroke(),
                    "MapMover.Zoomer.in");
                contentPane.getActionMap().put("MapMover.Zoomer.in", new ZoomerAction(","));

                contentPane.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                    Shortcut.registerShortcut("view:zoomoutalternate",
                            tr("Map: {0}", tr("Zoom out")), KeyEvent.VK_PERIOD, Shortcut.CTRL).getKeyStroke(),
                    "MapMover.Zoomer.out");
                contentPane.getActionMap().put("MapMover.Zoomer.out", new ZoomerAction("."));
            }
        }
    }

    /**
     * Registers the mouse events of a component so that they move the map on the right actions.
     * @param c The component to register the event on.
     */
    public void registerMouseEvents(Component c) {
        c.addMouseListener(this);
        c.addMouseMotionListener(this);
        c.addMouseWheelListener(this);
    }

    /**
     * If the right (and only the right) mouse button is pressed, move the map.
     */
    @Override
    public void mouseDragged(MouseEvent e) {
        int offMask = MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK;
        int macMouseMask = MouseEvent.CTRL_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK;
        boolean stdMovement = (e.getModifiersEx() & (MouseEvent.BUTTON3_DOWN_MASK | offMask)) == MouseEvent.BUTTON3_DOWN_MASK;
        boolean macMovement = Main.isPlatformOsx() && e.getModifiersEx() == macMouseMask;
        boolean allowedMode = !Main.map.mapModeSelect.equals(Main.map.mapMode)
                          || SelectAction.Mode.SELECT.equals(Main.map.mapModeSelect.getMode());
        if (stdMovement || (macMovement && allowedMode)) {
            if (mousePosMove == null)
                startMovement(e);
            EastNorth center = nm.getState().getCenter().getEastNorth();
            EastNorth mouseCenter = nm.getState().getPoint(e.getPoint()).getEastNorth();

            nm.getZoomHelper().zoomTo(new EastNorth(
                    mousePosMove.east() + center.east() - mouseCenter.east(),
                    mousePosMove.north() + center.north() - mouseCenter.north()), ScrollMode.IMMEDIATE);
        } else {
            endMovement();
        }
        updateMousePosition(e);
    }

    /**
     * Start the movement, if it was the 3rd button (right button).
     */
    @Override
    public void mousePressed(MouseEvent e) {
        int offMask = MouseEvent.BUTTON1_DOWN_MASK | MouseEvent.BUTTON2_DOWN_MASK;
        int macMouseMask = MouseEvent.CTRL_DOWN_MASK | MouseEvent.BUTTON1_DOWN_MASK;
        if (e.getButton() == MouseEvent.BUTTON3 && (e.getModifiersEx() & offMask) == 0 ||
                Main.isPlatformOsx() && e.getModifiersEx() == macMouseMask) {
            startMovement(e);
        }
        updateMousePosition(e);
    }

    /**
     * Change the cursor back to it's pre-move cursor.
     */
    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON3 || Main.isPlatformOsx() && e.getButton() == MouseEvent.BUTTON1) {
            endMovement();
        }
        updateMousePosition(e);
    }

    @Override
    public void mouseExited(MouseEvent e) {
        lastMousePosition = null;
    }

    /**
     * Start movement by setting a new cursor and remember the current mouse
     * position.
     * @param e The mouse event that leat to the movement from.
     */
    private void startMovement(MouseEvent e) {
        if (movementInPlace)
            return;
        movementInPlace = true;
        mousePosMove = nm.getState().getPoint(e.getPoint()).getEastNorth();
        cursorManager.setNewCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR), this);
    }

    /**
     * End the movement. Setting back the cursor and clear the movement variables
     */
    private void endMovement() {
        if (!movementInPlace)
            return;
        movementInPlace = false;
        cursorManager.resetCursor(this);
        mousePosMove = null;
    }

    /**
     * Zoom the map by 1/5th of current zoom per wheel-delta.
     * @param e The wheel event.
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
        nm.getZoomHelper().zoomToFactorAround(e.getPoint(), Math.pow(Math.sqrt(2), e.getWheelRotation()));
    }

    /**
     * Emulates dragging on Mac OSX.
     */
    @Override
    public void mouseMoved(MouseEvent e) {
        if (!movementInPlace)
            return;
        // Mac OSX simulates with  ctrl + mouse 1  the second mouse button hence no dragging events get fired.
        // Is only the selected mouse button pressed?
        if (Main.isPlatformOsx()) {
            if (e.getModifiersEx() == MouseEvent.CTRL_DOWN_MASK) {
                if (mousePosMove == null) {
                    startMovement(e);
                }
                EastNorth center = nm.getState().getCenter().getEastNorth();
                EastNorth mouseCenter = nm.getState().getPoint(e.getPoint()).getEastNorth();
                nm.getZoomHelper().zoomTo(new EastNorth(mousePosMove.east() + center.east() - mouseCenter.east(), mousePosMove.north()
                        + center.north() - mouseCenter.north()), ScrollMode.IMMEDIATE);
            } else {
                endMovement();
            }
        }
        updateMousePosition(e);
    }

    @Override
    public void destroy() {
        if (this.contentPane != null) {
            InputMap inputMap = contentPane.getInputMap();
            KeyStroke[] inputKeys = inputMap.keys();
            if (inputKeys != null) {
                for (KeyStroke key : inputKeys) {
                    Object binding = inputMap.get(key);
                    if (binding instanceof String && ((String) binding).startsWith("MapMover.")) {
                        inputMap.remove(key);
                    }
                }
            }
            ActionMap actionMap = contentPane.getActionMap();
            Object[] actionsKeys = actionMap.keys();
            if (actionsKeys != null) {
                for (Object key : actionsKeys) {
                    if (key instanceof String && ((String) key).startsWith("MapMover.")) {
                        actionMap.remove(key);
                    }
                }
            }
        }
    }

    private void updateMousePosition(MouseEvent e) {
        lastMousePosition = e.getPoint();
    }
}
