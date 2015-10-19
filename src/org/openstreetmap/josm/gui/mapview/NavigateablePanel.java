// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.mapview;

import javax.swing.JPanel;

import org.openstreetmap.josm.gui.NavigatableComponent;

/**
 * This is a panel that displays something like a map: A graphic that is expressed in world coordinates.
 * <p>
 * This class is intended to replace the {@link NavigatableComponent}.
 * @author Michael Zangl
 *
 */
public class NavigateablePanel extends JPanel {
    private final NavigationModel navigationModel;

    public NavigateablePanel(NavigationModel navigationModel) {
        this.navigationModel = navigationModel;
        navigationModel.setTrackedComponent(this);
    }

    /**
     * You should use this to change the viewport.
     * @return The zoomer.
     */
    public MapDisplayZoomHelper getZoomer() {
        return navigationModel.getZoomHelper();
    }

    /**
     * Gets the backing navigation model.
     * <p>
     * You rarely need to use this directly. Use {@link #getZoomer()} to change the viewport or {@link #getState()} to do coordinate conversions.
     * @return The navigation model this component was constructed with.
     */
    public NavigationModel getNavigationModel() {
        return navigationModel;
    }

    /**
     * Gets the current state and conversion matrixes for this panel.
     * @return The state.
     */
    public MapDisplayState getState() {
        return navigationModel.getState();
    }
}
