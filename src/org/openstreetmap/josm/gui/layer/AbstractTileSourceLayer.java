// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Component;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.ActionEvent;
import java.awt.image.ImageObserver;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;

import org.openstreetmap.gui.jmapviewer.OsmTileLoader;
import org.openstreetmap.gui.jmapviewer.Tile;
import org.openstreetmap.gui.jmapviewer.TileXY;
import org.openstreetmap.gui.jmapviewer.interfaces.TemplatedTileSource;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoader;
import org.openstreetmap.gui.jmapviewer.interfaces.TileLoaderListener;
import org.openstreetmap.gui.jmapviewer.interfaces.TileSource;
import org.openstreetmap.gui.jmapviewer.tilesources.AbstractTMSTileSource;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ImageryAdjustAction;
import org.openstreetmap.josm.actions.RenameLayerAction;
import org.openstreetmap.josm.actions.SaveActionBase;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.imagery.TMSCachedTileLoader;
import org.openstreetmap.josm.data.imagery.TileLoaderFactory;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.preferences.IntegerProperty;
import org.openstreetmap.josm.gui.MapFrame;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.NavigatableComponent.ZoomChangeListener;
import org.openstreetmap.josm.gui.dialogs.LayerListDialog;
import org.openstreetmap.josm.gui.dialogs.LayerListPopup;
import org.openstreetmap.josm.gui.layer.imagery.ImageryFilterSettings.FilterChangeListener;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings.DisplaySettingsChangeEvent;
import org.openstreetmap.josm.gui.layer.imagery.TileSourceDisplaySettings.DisplaySettingsChangeListener;
import org.openstreetmap.josm.gui.layer.imagery.TileSourcePainter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.WMSLayerImporter;
import org.openstreetmap.josm.tools.GBC;

/**
 * Base abstract class that supports displaying images provided by TileSource. It might be TMS source, WMS or WMTS
 *
 * It implements all standard functions of tilesource based layers: autozoom, tile reloads, layer saving, loading,etc.
 *
 * @author Upliner
 * @author Wiktor Niesiobędzki
 * @param <T> Tile Source class used for this layer
 * @since 3715
 * @since 8526 (copied from TMSLayer)
 */
public abstract class AbstractTileSourceLayer<T extends AbstractTMSTileSource> extends ImageryLayer implements
        ImageObserver, TileLoaderListener, ZoomChangeListener, FilterChangeListener, DisplaySettingsChangeListener {
    private static final String PREFERENCE_PREFIX = "imagery.generic";

    /**
     * Registers all setting properties
     */
    static {
        new TileSourceDisplaySettings();
    }

    /** maximum zoom level supported */
    public static final int MAX_ZOOM = 30;
    /** minium zoom level supported */
    public static final int MIN_ZOOM = 2;

    /** minimum zoom level to show to user */
    public static final IntegerProperty PROP_MIN_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".min_zoom_lvl", 2);
    /** maximum zoom level to show to user */
    public static final IntegerProperty PROP_MAX_ZOOM_LVL = new IntegerProperty(PREFERENCE_PREFIX + ".max_zoom_lvl",
            20);

    //public static final BooleanProperty PROP_DRAW_DEBUG = new BooleanProperty(PREFERENCE_PREFIX + ".draw_debug", false);

    /**
     * Offset between calculated zoom level and zoom level used to download and show tiles. Negative values will result in
     * lower resolution of imagery useful in "retina" displays, positive values will result in higher resolution
     */
    public static final IntegerProperty ZOOM_OFFSET = new IntegerProperty(PREFERENCE_PREFIX + ".zoom_offset", 0);

    /**
     * A timer that is used to delay invalidation events if required.
     */
    private final Timer invalidateLaterTimer = new Timer(100, e -> this.invalidate());

    private final TileSourceDisplaySettings displaySettings = createDisplaySettings();

    private final ImageryAdjustAction adjustAction = new ImageryAdjustAction(this);

    private HashMap<MapView, TileSourcePainter<T>> painters = new HashMap<>();

    /**
     * Creates Tile Source based Imagery Layer based on Imagery Info
     * @param info imagery info
     */
    public AbstractTileSourceLayer(ImageryInfo info) {
        super(info);
        setBackgroundLayer(true);
        this.setVisible(true);
        getFilterSettings().addFilterChangeListener(this);
        getDisplaySettings().addSettingsChangeListener(this);
    }

    /**
     * This method creates the {@link TileSourceDisplaySettings} object. Subclasses may implement it to e.g. change the prefix.
     * @return The object.
     * @since 10568
     */
    protected TileSourceDisplaySettings createDisplaySettings() {
        return new TileSourceDisplaySettings();
    }

    /**
     * Gets the {@link TileSourceDisplaySettings} instance associated with this tile source.
     * @return The tile source display settings
     * @since 10568
     */
    public TileSourceDisplaySettings getDisplaySettings() {
        return displaySettings;
    }

    @Override
    public void filterChanged() {
        invalidate();
    }

    /**
     * Generate the tile loader
     * @param tileSource A tile source that is already generated.
     * @return The tile loader.
     */
    public TileLoader generateTileLoader(T tileSource) {
        Map<String, String> headers = getHeaders(tileSource);

        TileLoader loader = getTileLoaderFactory().makeTileLoader(this, headers);
        if (loader != null) {
            return loader;
        }

        try {
            if ("file".equalsIgnoreCase(new URL(tileSource.getBaseUrl()).getProtocol())) {
                return new OsmTileLoader(this);
            }
        } catch (MalformedURLException e) {
            // ignore, assume that this is not a file
            if (Main.isDebugEnabled()) {
                Main.debug(e.getMessage());
            }
        }

        return new OsmTileLoader(this, headers);
    }

    /**
     * Generates the tile source for this layer.
     * @return The tile source
     */
    public T getTileSource() {
        return getTileSource(getInfo());
    }

    protected abstract TileLoaderFactory getTileLoaderFactory();

    /**
     * Used by the default {@link TileSourcePainter} to create the tile source.
     * @param info imagery info
     * @return TileSource for specified ImageryInfo
     * @throws IllegalArgumentException when Imagery is not supported by layer
     */
    protected T getTileSource(ImageryInfo info) {
        throw new UnsupportedOperationException();
    }

    protected Map<String, String> getHeaders(T tileSource) {
        if (tileSource instanceof TemplatedTileSource) {
            return ((TemplatedTileSource) tileSource).getHeaders();
        }
        return null;
    }

    @Override
    public synchronized void tileLoadingFinished(Tile tile, boolean success) {
        if (tile.hasError()) {
            success = false;
            tile.setImage(null);
        }
        tile.setLoaded(success);
        invalidateLater();
        if (Main.isDebugEnabled()) {
            Main.debug("tileLoadingFinished() tile: " + tile + " success: " + success);
        }
    }

    /**
     * Initiates a repaint of Main.map
     *
     * @see Main#map
     * @see MapFrame#repaint()
     * @see #invalidate() To trigger a repaint of all places where the layer is displayed.
     */
    protected void redraw() {
        invalidate();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings#getDx()}
     */
    @Override
    @Deprecated
    public double getDx() {
        return getDisplaySettings().getDx();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings#getDy()}
     */
    @Override
    @Deprecated
    public double getDy() {
        return getDisplaySettings().getDy();
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings}
     */
    @Override
    @Deprecated
    public void displace(double dx, double dy) {
        getDisplaySettings().addDisplacement(new EastNorth(dx, dy));
    }

    /**
     * {@inheritDoc}
     * @deprecated Use {@link TileSourceDisplaySettings}
     */
    @Override
    @Deprecated
    public void setOffset(double dx, double dy) {
        getDisplaySettings().setDisplacement(new EastNorth(dx, dy));
    }

    @Override
    public Object getInfoComponent() {
        JPanel panel = (JPanel) super.getInfoComponent();
        EastNorth offset = getDisplaySettings().getDisplacement();
        if (offset.distanceSq(0, 0) > 1e-10) {
            panel.add(new JLabel(tr("Offset: ") + offset.east() + ';' + offset.north()), GBC.eol().insets(0, 5, 10, 0));
        }
        return panel;
    }

    @Override
    protected Action getAdjustAction() {
        return adjustAction;
    }

    private static boolean actionSupportLayers(List<Layer> layers) {
        return layers.size() == 1 && layers.get(0) instanceof TMSLayer;
    }

    private class AutoZoomAction extends AbstractAction implements LayerAction {
        AutoZoomAction() {
            super(tr("Auto zoom"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setAutoZoom(!getDisplaySettings().isAutoZoom());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isAutoZoom());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    private class AutoLoadTilesAction extends AbstractAction implements LayerAction {
        AutoLoadTilesAction() {
            super(tr("Auto load tiles"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setAutoLoad(!getDisplaySettings().isAutoLoad());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isAutoLoad());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    private class ShowErrorsAction extends AbstractAction implements LayerAction {
        ShowErrorsAction() {
            super(tr("Show errors"));
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            getDisplaySettings().setShowErrors(!getDisplaySettings().isShowErrors());
        }

        @Override
        public Component createMenuComponent() {
            JCheckBoxMenuItem item = new JCheckBoxMenuItem(this);
            item.setSelected(getDisplaySettings().isShowErrors());
            return item;
        }

        @Override
        public boolean supportLayers(List<Layer> layers) {
            return actionSupportLayers(layers);
        }
    }

    @Override
    public void hookUpMapView() {
    }

    @Override
    public LayerPainter attachToMapView(MapViewEvent event) {
        GuiHelper.assertCallFromEdt();
        MapView.addZoomChangeListener(this);

        if (this instanceof NativeScaleLayer) {
            event.getMapView().setNativeScaleLayer((NativeScaleLayer) this);
        }

        TileSourcePainter<T> painter = createMapViewPainter(event);
        painters.put(event.getMapView(), painter);
        return painter;
    }

    @Override
    protected TileSourcePainter<T> createMapViewPainter(MapViewEvent event) {
        return new TileSourcePainter<>(this, event.getMapView());
    }

    /**
     * Tile source layer popup menu.
     */
    public class TileSourceLayerPopup extends JPopupMenu {
        /**
         * Constructs a new {@code TileSourceLayerPopup}.
         * @param mv
         */
        public TileSourceLayerPopup(MapView mv) {
            for (Action a : getCommonEntries()) {
                addAction(a);
            }
            for (Action a : getMapViewEntries(mv)) {
                addAction(a);
            }
        }

        private void addAction(Action a) {
            if (a instanceof LayerAction) {
                add(((LayerAction) a).createMenuComponent());
            } else {
                add(new JMenuItem(a));
            }
        }
    }

    @Override
    public void displaySettingsChanged(DisplaySettingsChangeEvent e) {
        switch (e.getChangedSetting()) {
        case TileSourceDisplaySettings.AUTO_LOAD:
            if (getDisplaySettings().isAutoLoad()) {
                invalidate();
            }
            break;
        default:
            // trigger a redraw just to be sure.
            invalidate();
        }
    }

    /**
     * Checks zoom level against settings
     * @param maxZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return maximum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMaxZoomLvl(int maxZoomLvl, TileSource ts) {
        if (maxZoomLvl > MAX_ZOOM) {
            maxZoomLvl = MAX_ZOOM;
        }
        if (maxZoomLvl < PROP_MIN_ZOOM_LVL.get()) {
            maxZoomLvl = PROP_MIN_ZOOM_LVL.get();
        }
        if (ts != null && ts.getMaxZoom() != 0 && ts.getMaxZoom() < maxZoomLvl) {
            maxZoomLvl = ts.getMaxZoom();
        }
        return maxZoomLvl;
    }

    /**
     * Checks zoom level against settings
     * @param minZoomLvl zoom level to check
     * @param ts tile source to crosscheck with
     * @return minimum zoom level, not higher than supported by tilesource nor set by the user
     */
    public static int checkMinZoomLvl(int minZoomLvl, TileSource ts) {
        if (minZoomLvl < MIN_ZOOM) {
            minZoomLvl = MIN_ZOOM;
        }
        if (minZoomLvl > PROP_MAX_ZOOM_LVL.get()) {
            minZoomLvl = getMaxZoomLvl(ts);
        }
        if (ts != null && ts.getMinZoom() > minZoomLvl) {
            minZoomLvl = ts.getMinZoom();
        }
        return minZoomLvl;
    }

    /**
     * @param ts TileSource for which we want to know maximum zoom level
     * @return maximum max zoom level, that will be shown on layer
     */
    public static int getMaxZoomLvl(TileSource ts) {
        return checkMaxZoomLvl(PROP_MAX_ZOOM_LVL.get(), ts);
    }

    /**
     * @param ts TileSource for which we want to know minimum zoom level
     * @return minimum zoom level, that will be shown on layer
     */
    public static int getMinZoomLvl(TileSource ts) {
        return checkMinZoomLvl(PROP_MIN_ZOOM_LVL.get(), ts);
    }

    /**
     * Sets maximum zoom level, that layer will attempt show
     * @param maxZoomLvl maximum zoom level
     */
    public static void setMaxZoomLvl(int maxZoomLvl) {
        PROP_MAX_ZOOM_LVL.put(checkMaxZoomLvl(maxZoomLvl, null));
    }

    /**
     * Sets minimum zoom level, that layer will attempt show
     * @param minZoomLvl minimum zoom level
     */
    public static void setMinZoomLvl(int minZoomLvl) {
        PROP_MIN_ZOOM_LVL.put(checkMinZoomLvl(minZoomLvl, null));
    }

    /**
     * This fires every time the user changes the zoom, but also (due to ZoomChangeListener) - on all
     * changes to visible map (panning/zooming)
     */
    @Override
    public void zoomChanged() {
        invalidate();
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        boolean done = (infoflags & (ERROR | FRAMEBITS | ALLBITS)) != 0;
        if (Main.isDebugEnabled()) {
            Main.debug("imageUpdate() done: " + done + " calling repaint");
        }

        if (done) {
            invalidate();
        } else {
            invalidateLater();
        }
        return !done;
    }

    /**
     * Invalidate the layer at a time in the future so taht the user still sees the interface responsive.
     */
    private void invalidateLater() {
        GuiHelper.runInEDT(() -> {
            if (!invalidateLaterTimer.isRunning()) {
                invalidateLaterTimer.setRepeats(false);
                invalidateLaterTimer.start();
            }
        });
    }

    @Override
    public Action[] getMenuEntries() {
        ArrayList<Action> actions = new ArrayList<>();
        actions.addAll(Arrays.asList(getLayerListEntries()));
        actions.addAll(Arrays.asList(getCommonEntries()));
        actions.add(SeparatorLayerAction.INSTANCE);
        actions.add(new LayerListPopup.InfoAction(this));
        return actions.toArray(new Action[actions.size()]);
    }

    public Action[] getLayerListEntries() {
        return new Action[] { LayerListDialog.getInstance().createActivateLayerAction(this),
                LayerListDialog.getInstance().createShowHideLayerAction(),
                LayerListDialog.getInstance().createDeleteLayerAction(), SeparatorLayerAction.INSTANCE,
                // color,
                new OffsetAction(), new RenameLayerAction(this.getAssociatedFile(), this),
                SeparatorLayerAction.INSTANCE };
    }

    /**
     * Returns the common menu entries.
     * @return the common menu entries
     */
    public Action[] getCommonEntries() {
        return new Action[] {
                new AutoLoadTilesAction(),
                new AutoZoomAction(),
                new ShowErrorsAction() };
    }

    private List<Action> getMapViewEntries(MapView mv) {
        TileSourcePainter<T> painter = painters.get(mv);
        return painter.getMenuEntries();
    }

    @Override
    public String getToolTipText() {
        String currentZoomLevel = painters.values().stream().findAny().map(TileSourcePainter::getZoomString).orElse("?");
        if (getDisplaySettings().isAutoLoad()) {
            return tr("{0} ({1}), automatically downloading in zoom {2}", this.getClass().getSimpleName(), getName(),
                    currentZoomLevel);
        } else {
            return tr("{0} ({1}), downloading in zoom {2}", this.getClass().getSimpleName(), getName(),
                    currentZoomLevel);
        }
    }

    @Override
    public void visitBoundingBox(BoundingXYVisitor v) {
    }

    @Override
    public boolean isChanged() {
        // we use #invalidate()
        return false;
    }

    @Override
    public void paint(Graphics2D g, MapView mv, Bounds bbox) {
        // never called, we use a custom painter
        throw new UnsupportedOperationException();
    }

    /**
     * Task responsible for precaching imagery along the gpx track
     *
     */
    public class PrecacheTask implements TileLoaderListener {
        private final ProgressMonitor progressMonitor;
        private int totalCount;
        private final AtomicInteger processedCount = new AtomicInteger(0);
        private final TileLoader tileLoader;

        /**
         * @param progressMonitor that will be notified about progess of the task
         */
        public PrecacheTask(ProgressMonitor progressMonitor) {
            this.progressMonitor = progressMonitor;
            // TODO
            T tileSource = null;
            this.tileLoader = getTileLoaderFactory().makeTileLoader(this, getHeaders(tileSource));
            if (this.tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) this.tileLoader)
                        .setDownloadExecutor(TMSCachedTileLoader.getNewThreadPoolExecutor("Precache downloader"));
            }
        }

        /**
         * @return true, if all is done
         */
        public boolean isFinished() {
            return processedCount.get() >= totalCount;
        }

        /**
         * @return total number of tiles to download
         */
        public int getTotalCount() {
            return totalCount;
        }

        /**
         * cancel the task
         */
        public void cancel() {
            if (tileLoader instanceof TMSCachedTileLoader) {
                ((TMSCachedTileLoader) tileLoader).cancelOutstandingTasks();
            }
        }

        @Override
        public void tileLoadingFinished(Tile tile, boolean success) {
            int processed = this.processedCount.incrementAndGet();
            if (success) {
                this.progressMonitor.worked(1);
                this.progressMonitor.setCustomText(tr("Downloaded {0}/{1} tiles", processed, totalCount));
            } else {
                Main.warn("Tile loading failure: " + tile + " - " + tile.getErrorMessage());
            }
        }

        /**
         * @return tile loader that is used to load the tiles
         */
        public TileLoader getTileLoader() {
            return tileLoader;
        }
    }

    /**
     * Calculates tiles, that needs to be downloaded to cache, gets a current tile loader and creates a task to download
     * all of the tiles. Buffer contains at least one tile.
     *
     * To prevent accidental clear of the queue, new download executor is created with separate queue
     *
     * @param progressMonitor progress monitor for download task
     * @param points lat/lon coordinates to download
     * @param bufferX how many units in current Coordinate Reference System to cover in X axis in both sides
     * @param bufferY how many units in current Coordinate Reference System to cover in Y axis in both sides
     * @return precache task representing download task
     */
    public AbstractTileSourceLayer<T>.PrecacheTask downloadAreaToCache(final ProgressMonitor progressMonitor,
            List<LatLon> points, double bufferX, double bufferY) {
        PrecacheTask precacheTask = new PrecacheTask(progressMonitor);
        final Set<Tile> requestedTiles = new ConcurrentSkipListSet<>(
                (o1, o2) -> String.CASE_INSENSITIVE_ORDER.compare(o1.getKey(), o2.getKey()));
        for (LatLon point : points) {
            //TODO
            TileSource tileSource = null;
            int currentZoomLevel = 0;
            TileXY minTile = tileSource.latLonToTileXY(point.lat() - bufferY, point.lon() - bufferX, currentZoomLevel);
            TileXY curTile = tileSource.latLonToTileXY(point.toCoordinate(), currentZoomLevel);
            TileXY maxTile = tileSource.latLonToTileXY(point.lat() + bufferY, point.lon() + bufferX, currentZoomLevel);

            // take at least one tile of buffer
            int minY = Math.min(curTile.getYIndex() - 1, minTile.getYIndex());
            int maxY = Math.max(curTile.getYIndex() + 1, maxTile.getYIndex());
            int minX = Math.min(curTile.getXIndex() - 1, minTile.getXIndex());
            int maxX = Math.max(curTile.getXIndex() + 1, maxTile.getXIndex());

            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    requestedTiles.add(new Tile(tileSource, x, y, currentZoomLevel));
                }
            }
        }

        precacheTask.totalCount = requestedTiles.size();
        precacheTask.progressMonitor.setTicksCount(requestedTiles.size());

        TileLoader loader = precacheTask.getTileLoader();
        for (Tile t : requestedTiles) {
            loader.createTileLoaderJob(t).submit();
        }
        return precacheTask;
    }

    @Override
    public boolean isSavable() {
        return true; // With WMSLayerExporter
    }

    @Override
    public File createAndOpenSaveFileChooser() {
        return SaveActionBase.createAndOpenSaveFileChooser(tr("Save WMS file"), WMSLayerImporter.FILE_FILTER);
    }

    @Override
    public void destroy() {
        super.destroy();
        adjustAction.destroy();
    }

    /**
     * A {@link TileSourcePainter} notifies us of a dispatch
     * @param tileSourcePainter The painter.
     */
    public void detach(TileSourcePainter<T> tileSourcePainter) {
        GuiHelper.assertCallFromEdt();
        painters.entrySet().removeIf(e -> e.getValue().equals(tileSourcePainter));
    }
}
