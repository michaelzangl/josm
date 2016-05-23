// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import java.util.Arrays;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.openstreetmap.josm.JOSMFixture;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.layer.LayerManagerWithActive.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManagerWithActive.ActiveLayerChangeListener;
import org.openstreetmap.josm.tools.Predicates;

/**
 * Tests {@link LayerManagerWithActive}
 * @author Michael Zangl
 *
 */
public class LayerManagerWithActiveTest extends LayerManagerTest {

    private LayerManagerWithActive layerManagerWithActive;

    private final class CapturingActiveLayerChangeListener implements ActiveLayerChangeListener {
        private ActiveLayerChangeEvent lastEvent;

        @Override
        public void activeOrEditLayerChanged(ActiveLayerChangeEvent e) {
            assertSame(layerManager, e.getSource());
            lastEvent = e;
        }
    }

    protected class AbstractTestOsmLayer extends OsmDataLayer {
        public AbstractTestOsmLayer() {
            super(new DataSet(), "OSM layer", null);
        }

        @Override
        public LayerPositionStrategy getDefaultLayerPosition() {
            return LayerPositionStrategy.afterLast(Predicates.<Layer> alwaysTrue());
        }
    }

    @BeforeClass
    public static void setUpClass() {
        JOSMFixture.createUnitTestFixture().init();
    }

    @Override
    @Before
    public void setUp() {
        layerManager = layerManagerWithActive = new LayerManagerWithActive();
    }

    @Test
    public void testAddLayerSetsActiveLayer() {
        AbstractTestLayer layer1 = new AbstractTestLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        AbstractTestLayer layer3 = new AbstractTestLayer();
        assertNull(layerManagerWithActive.getActiveLayer());
        assertNull(layerManagerWithActive.getEditLayer());
        layerManagerWithActive.addLayer(layer1);
        assertSame(layer1, layerManagerWithActive.getActiveLayer());
        assertNull(layerManagerWithActive.getEditLayer());
        layerManagerWithActive.addLayer(layer2);
        assertSame(layer2, layerManagerWithActive.getActiveLayer());
        assertSame(layer2, layerManagerWithActive.getEditLayer());
        layerManagerWithActive.addLayer(layer3);
        assertSame(layer2, layerManagerWithActive.getActiveLayer());
        assertSame(layer2, layerManagerWithActive.getEditLayer());
    }

    @Test
    public void testRemoveLayerUnsetsActiveLayer() {
        AbstractTestLayer layer1 = new AbstractTestLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        AbstractTestLayer layer3 = new AbstractTestLayer();
        AbstractTestOsmLayer layer4 = new AbstractTestOsmLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);
        layerManagerWithActive.addLayer(layer3);
        layerManagerWithActive.addLayer(layer4);
        assertSame(layer4, layerManagerWithActive.getActiveLayer());
        assertSame(layer4, layerManagerWithActive.getEditLayer());
        layerManagerWithActive.removeLayer(layer4);
        //prefer osm layers
        assertSame(layer2, layerManagerWithActive.getActiveLayer());
        assertSame(layer2, layerManagerWithActive.getEditLayer());
        layerManagerWithActive.removeLayer(layer2);
        assertSame(layer1, layerManagerWithActive.getActiveLayer());
        assertNull(layerManagerWithActive.getEditLayer());

        layerManagerWithActive.removeLayer(layer1);
        layerManagerWithActive.removeLayer(layer3);
        assertNull(layerManagerWithActive.getActiveLayer());
        assertNull(layerManagerWithActive.getEditLayer());
    }

    @Test
    public void testAddActiveLayerChangeListener() {
        AbstractTestLayer layer1 = new AbstractTestLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);

        CapturingActiveLayerChangeListener listener = new CapturingActiveLayerChangeListener();
        layerManagerWithActive.addActiveLayerChangeListener(listener, false);
        assertNull(listener.lastEvent);

        CapturingActiveLayerChangeListener listener2 = new CapturingActiveLayerChangeListener();
        layerManagerWithActive.addActiveLayerChangeListener(listener2, true);
        assertSame(listener2.lastEvent.getPreviousActiveLayer(), null);
        assertSame(listener2.lastEvent.getPreviousEditLayer(), null);

        layerManagerWithActive.setActiveLayer(layer1);
        assertSame(listener2.lastEvent.getPreviousActiveLayer(), layer2);
        assertSame(listener2.lastEvent.getPreviousEditLayer(), layer2);

        layerManagerWithActive.setActiveLayer(layer2);
        assertSame(listener2.lastEvent.getPreviousActiveLayer(), layer1);
        assertSame(listener2.lastEvent.getPreviousEditLayer(), layer2);
    }

    /**
     * Test if {@link LayerManagerWithActive#addActiveLayerChangeListener(ActiveLayerChangeListener)} prevents listener from beeing added twice.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testAddActiveLayerChangeListenerTwice() {
        CapturingActiveLayerChangeListener listener = new CapturingActiveLayerChangeListener();
        layerManagerWithActive.addActiveLayerChangeListener(listener, false);
        layerManagerWithActive.addActiveLayerChangeListener(listener, false);
    }

    /**
     * Test if {@link LayerManagerWithActive#removeActiveLayerChangeListener(ActiveLayerChangeListener)} works.
     */
    @Test
    public void testRemoveActiveLayerChangeListener() {
        AbstractTestLayer layer1 = new AbstractTestLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);

        CapturingActiveLayerChangeListener listener = new CapturingActiveLayerChangeListener();
        layerManagerWithActive.addActiveLayerChangeListener(listener, false);
        layerManagerWithActive.removeActiveLayerChangeListener(listener);

        layerManagerWithActive.setActiveLayer(layer2);
        assertNull(listener.lastEvent);
    }

    /**
     * Test if {@link LayerManagerWithActive#removeActiveLayerChangeListener(ActiveLayerChangeListener)} checks if listener is in list.
     */
    @Test(expected = IllegalArgumentException.class)
    public void testRemoveActiveLayerChangeListenerNotInList() {
        layerManagerWithActive.removeActiveLayerChangeListener(new CapturingActiveLayerChangeListener());
    }

    /**
     * Tests {@link LayerManagerWithActive#setActiveLayer(Layer)} and {@link LayerManagerWithActive#getActiveLayer()}.
     * <p>
     * Edit and active layer getters are also tested in {@link #testAddLayerSetsActiveLayer()}
     */
    @Test
    public void testSetGetActiveLayer() {
        AbstractTestLayer layer1 = new AbstractTestLayer();
        AbstractTestLayer layer2 = new AbstractTestLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);

        layerManagerWithActive.setActiveLayer(layer1);
        assertSame(layer1, layerManagerWithActive.getActiveLayer());

        layerManagerWithActive.setActiveLayer(layer2);
        assertSame(layer2, layerManagerWithActive.getActiveLayer());
    }

    /**
     * Tests {@link LayerManagerWithActive#getEditDataSet()}
     */
    @Test
    public void testGetEditDataSet() {
        assertNull(layerManagerWithActive.getEditDataSet());
        AbstractTestLayer layer0 = new AbstractTestLayer();
        layerManagerWithActive.addLayer(layer0);
        assertNull(layerManagerWithActive.getEditDataSet());

        AbstractTestOsmLayer layer1 = new AbstractTestOsmLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);

        layerManagerWithActive.setActiveLayer(layer1);
        assertSame(layer1.data, layerManagerWithActive.getEditDataSet());

        layerManagerWithActive.setActiveLayer(layer2);
        assertSame(layer2.data, layerManagerWithActive.getEditDataSet());
    }

    /**
     * Tests {@link LayerManagerWithActive#getVisibleLayersInZOrder()}
     */
    @Test
    public void testGetVisibleLayersInZOrder() {
        AbstractTestOsmLayer layer1 = new AbstractTestOsmLayer();
        AbstractTestOsmLayer layer2 = new AbstractTestOsmLayer();
        AbstractTestLayer layer3 = new AbstractTestLayer();
        layer3.setVisible(false);
        AbstractTestOsmLayer layer4 = new AbstractTestOsmLayer();
        AbstractTestLayer layer5 = new AbstractTestLayer();
        AbstractTestOsmLayer layer6 = new AbstractTestOsmLayer();
        AbstractTestOsmLayer layer7 = new AbstractTestOsmLayer();
        layerManagerWithActive.addLayer(layer1);
        layerManagerWithActive.addLayer(layer2);
        layerManagerWithActive.addLayer(layer3);
        layerManagerWithActive.addLayer(layer4);
        layerManagerWithActive.addLayer(layer5);
        layerManagerWithActive.addLayer(layer6);
        layerManagerWithActive.addLayer(layer7);

        layerManagerWithActive.setActiveLayer(layer1);
        assertEquals(Arrays.asList(layer7, layer6, layer5, layer4, layer2, layer1),
                layerManagerWithActive.getVisibleLayersInZOrder());
        layerManagerWithActive.setActiveLayer(layer4);
        assertEquals(Arrays.asList(layer7, layer6, layer5, layer2, layer1, layer4),
                layerManagerWithActive.getVisibleLayersInZOrder());

        // should not be moved ouside edit layer block
        layerManagerWithActive.setActiveLayer(layer6);
        assertEquals(Arrays.asList(layer7, layer6, layer5, layer4, layer2, layer1),
                layerManagerWithActive.getVisibleLayersInZOrder());
        layerManagerWithActive.setActiveLayer(layer7);
        assertEquals(Arrays.asList(layer6, layer7, layer5, layer4, layer2, layer1),
                layerManagerWithActive.getVisibleLayersInZOrder());

        // ignored
        layerManagerWithActive.setActiveLayer(layer3);
        assertEquals(Arrays.asList(layer7, layer6, layer5, layer4, layer2, layer1),
                layerManagerWithActive.getVisibleLayersInZOrder());
        layerManagerWithActive.setActiveLayer(layer5);
        assertEquals(Arrays.asList(layer7, layer6, layer5, layer4, layer2, layer1),
                layerManagerWithActive.getVisibleLayersInZOrder());

    }

}
