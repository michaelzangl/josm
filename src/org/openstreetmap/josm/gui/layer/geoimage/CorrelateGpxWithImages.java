// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.geoimage;

import static org.openstreetmap.josm.tools.I18n.tr;
import static org.openstreetmap.josm.tools.I18n.trn;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimeZone;
import java.util.zip.GZIPInputStream;

import javax.swing.AbstractAction;
import javax.swing.AbstractListModel;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSlider;
import javax.swing.ListSelectionModel;
import javax.swing.MutableComboBoxModel;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileFilter;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.data.gpx.GpxConstants;
import org.openstreetmap.josm.data.gpx.GpxData;
import org.openstreetmap.josm.data.gpx.GpxTrack;
import org.openstreetmap.josm.data.gpx.GpxTrackSegment;
import org.openstreetmap.josm.data.gpx.WayPoint;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.ExtendedDialog;
import org.openstreetmap.josm.gui.layer.GpxLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.widgets.AbstractFileChooser;
import org.openstreetmap.josm.gui.widgets.JosmComboBox;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.io.GpxReader;
import org.openstreetmap.josm.io.JpgImporter;
import org.openstreetmap.josm.tools.ExifReader;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Pair;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.date.DateUtils;
import org.xml.sax.SAXException;

/**
 * This class displays the window to select the GPX file and the offset (timezone + delta).
 * Then it correlates the images of the layer with that GPX file.
 */
public class CorrelateGpxWithImages extends AbstractAction {

    private static List<GpxData> loadedGpxData = new ArrayList<>();

    private final transient GeoImageLayer yLayer;
    private transient Timezone timezone;
    private transient Offset delta;

    /**
     * Constructs a new {@code CorrelateGpxWithImages} action.
     * @param layer The image layer
     */
    public CorrelateGpxWithImages(GeoImageLayer layer) {
        super(tr("Correlate to GPX"), ImageProvider.get("dialogs/geoimage/gpx2img"));
        this.yLayer = layer;
    }

    private final class SyncDialogWindowListener extends WindowAdapter {
        private static final int CANCEL = -1;
        private static final int DONE = 0;
        private static final int AGAIN = 1;
        private static final int NOTHING = 2;

        private int checkAndSave() {
            if (syncDialog.isVisible())
                // nothing happened: JOSM was minimized or similar
                return NOTHING;
            int answer = syncDialog.getValue();
            if (answer != 1)
                return CANCEL;

            // Parse values again, to display an error if the format is not recognized
            try {
                timezone = Timezone.parseTimezone(tfTimezone.getText().trim());
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(Main.parent, e.getMessage(),
                        tr("Invalid timezone"), JOptionPane.ERROR_MESSAGE);
                return AGAIN;
            }

            try {
                delta = Offset.parseOffset(tfOffset.getText().trim());
            } catch (ParseException e) {
                JOptionPane.showMessageDialog(Main.parent, e.getMessage(),
                        tr("Invalid offset"), JOptionPane.ERROR_MESSAGE);
                return AGAIN;
            }

            if (lastNumMatched == 0 && new ExtendedDialog(
                        Main.parent,
                        tr("Correlate images with GPX track"),
                        new String[] {tr("OK"), tr("Try Again")}).
                        setContent(tr("No images could be matched!")).
                        setButtonIcons(new String[] {"ok", "dialogs/refresh"}).
                        showDialog().getValue() == 2)
                return AGAIN;
            return DONE;
        }

        @Override
        public void windowDeactivated(WindowEvent e) {
            int result = checkAndSave();
            switch (result) {
            case NOTHING:
                break;
            case CANCEL:
                if (yLayer != null) {
                    if (yLayer.data != null) {
                        for (ImageEntry ie : yLayer.data) {
                            ie.discardTmp();
                        }
                    }
                    yLayer.updateBufferAndRepaint();
                }
                break;
            case AGAIN:
                actionPerformed(null);
                break;
            case DONE:
                Main.pref.put("geoimage.timezone", timezone.formatTimezone());
                Main.pref.put("geoimage.delta", delta.formatOffset());
                Main.pref.put("geoimage.showThumbs", yLayer.useThumbs);

                yLayer.useThumbs = cbShowThumbs.isSelected();
                yLayer.startLoadThumbs();

                // Search whether an other layer has yet defined some bounding box.
                // If none, we'll zoom to the bounding box of the layer with the photos.
                boolean boundingBoxedLayerFound = false;
                for (Layer l: Main.getLayerManager().getLayers()) {
                    if (l != yLayer) {
                        BoundingXYVisitor bbox = new BoundingXYVisitor();
                        l.visitBoundingBox(bbox);
                        if (bbox.getBounds() != null) {
                            boundingBoxedLayerFound = true;
                            break;
                        }
                    }
                }
                if (!boundingBoxedLayerFound) {
                    BoundingXYVisitor bbox = new BoundingXYVisitor();
                    yLayer.visitBoundingBox(bbox);
                    Main.map.mapView.zoomTo(bbox);
                }

                if (yLayer.data != null) {
                    for (ImageEntry ie : yLayer.data) {
                        ie.applyTmp();
                    }
                }

                yLayer.updateBufferAndRepaint();

                break;
            default:
                throw new IllegalStateException();
            }
        }
    }

    private static class GpxDataWrapper {
        private final String name;
        private final GpxData data;
        private final File file;

        GpxDataWrapper(String name, GpxData data, File file) {
            this.name = name;
            this.data = data;
            this.file = file;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private ExtendedDialog syncDialog;
    private final transient List<GpxDataWrapper> gpxLst = new ArrayList<>();
    private JPanel outerPanel;
    private JosmComboBox<GpxDataWrapper> cbGpx;
    private JosmTextField tfTimezone;
    private JosmTextField tfOffset;
    private JCheckBox cbExifImg;
    private JCheckBox cbTaggedImg;
    private JCheckBox cbShowThumbs;
    private JLabel statusBarText;

    // remember the last number of matched photos
    private int lastNumMatched;

    /** This class is called when the user doesn't find the GPX file he needs in the files that have
     * been loaded yet. It displays a FileChooser dialog to select the GPX file to be loaded.
     */
    private class LoadGpxDataActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent arg0) {
            FileFilter filter = new FileFilter() {
                @Override
                public boolean accept(File f) {
                    return f.isDirectory() || Utils.hasExtension(f, "gpx", "gpx.gz");
                }

                @Override
                public String getDescription() {
                    return tr("GPX Files (*.gpx *.gpx.gz)");
                }
            };
            AbstractFileChooser fc = DiskAccessAction.createAndOpenFileChooser(true, false, null, filter, JFileChooser.FILES_ONLY, null);
            if (fc == null)
                return;
            File sel = fc.getSelectedFile();

            try {
                outerPanel.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

                for (int i = gpxLst.size() - 1; i >= 0; i--) {
                    GpxDataWrapper wrapper = gpxLst.get(i);
                    if (wrapper.file != null && sel.equals(wrapper.file)) {
                        cbGpx.setSelectedIndex(i);
                        if (!sel.getName().equals(wrapper.name)) {
                            JOptionPane.showMessageDialog(
                                    Main.parent,
                                    tr("File {0} is loaded yet under the name \"{1}\"", sel.getName(), wrapper.name),
                                    tr("Error"),
                                    JOptionPane.ERROR_MESSAGE
                            );
                        }
                        return;
                    }
                }
                GpxData data = null;
                try (InputStream iStream = createInputStream(sel)) {
                    GpxReader reader = new GpxReader(iStream);
                    reader.parse(false);
                    data = reader.getGpxData();
                    data.storageFile = sel;

                } catch (SAXException x) {
                    Main.error(x);
                    JOptionPane.showMessageDialog(
                            Main.parent,
                            tr("Error while parsing {0}", sel.getName())+": "+x.getMessage(),
                            tr("Error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                } catch (IOException x) {
                    Main.error(x);
                    JOptionPane.showMessageDialog(
                            Main.parent,
                            tr("Could not read \"{0}\"", sel.getName())+'\n'+x.getMessage(),
                            tr("Error"),
                            JOptionPane.ERROR_MESSAGE
                    );
                    return;
                }

                MutableComboBoxModel<GpxDataWrapper> model = (MutableComboBoxModel<GpxDataWrapper>) cbGpx.getModel();
                loadedGpxData.add(data);
                if (gpxLst.get(0).file == null) {
                    gpxLst.remove(0);
                    model.removeElementAt(0);
                }
                GpxDataWrapper elem = new GpxDataWrapper(sel.getName(), data, sel);
                gpxLst.add(elem);
                model.addElement(elem);
                cbGpx.setSelectedIndex(cbGpx.getItemCount() - 1);
            } finally {
                outerPanel.setCursor(Cursor.getDefaultCursor());
            }
        }

        private InputStream createInputStream(File sel) throws IOException {
            if (Utils.hasExtension(sel, "gpx.gz")) {
                return new GZIPInputStream(new FileInputStream(sel));
            } else {
                return new FileInputStream(sel);
            }
        }
    }

    /**
     * This action listener is called when the user has a photo of the time of his GPS receiver. It
     * displays the list of photos of the layer, and upon selection displays the selected photo.
     * From that photo, the user can key in the time of the GPS.
     * Then values of timezone and delta are set.
     * @author chris
     *
     */
    private class SetOffsetActionListener implements ActionListener {
        private JPanel panel;
        private JLabel lbExifTime;
        private JosmTextField tfGpsTime;
        private JosmComboBox<String> cbTimezones;
        private ImageDisplay imgDisp;
        private JList<String> imgList;

        @Override
        public void actionPerformed(ActionEvent arg0) {
            SimpleDateFormat dateFormat = (SimpleDateFormat) DateUtils.getDateTimeFormat(DateFormat.SHORT, DateFormat.MEDIUM);

            panel = new JPanel(new BorderLayout());
            panel.add(new JLabel(tr("<html>Take a photo of your GPS receiver while it displays the time.<br>"
                    + "Display that photo here.<br>"
                    + "And then, simply capture the time you read on the photo and select a timezone<hr></html>")),
                    BorderLayout.NORTH);

            imgDisp = new ImageDisplay();
            imgDisp.setPreferredSize(new Dimension(300, 225));
            panel.add(imgDisp, BorderLayout.CENTER);

            JPanel panelTf = new JPanel(new GridBagLayout());

            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = gc.gridy = 0;
            gc.gridwidth = gc.gridheight = 1;
            gc.weightx = gc.weighty = 0.0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            panelTf.add(new JLabel(tr("Photo time (from exif):")), gc);

            lbExifTime = new JLabel();
            gc.gridx = 1;
            gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.gridwidth = 2;
            panelTf.add(lbExifTime, gc);

            gc.gridx = 0;
            gc.gridy = 1;
            gc.gridwidth = gc.gridheight = 1;
            gc.weightx = gc.weighty = 0.0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            panelTf.add(new JLabel(tr("Gps time (read from the above photo): ")), gc);

            tfGpsTime = new JosmTextField(12);
            tfGpsTime.setEnabled(false);
            tfGpsTime.setMinimumSize(new Dimension(155, tfGpsTime.getMinimumSize().height));
            gc.gridx = 1;
            gc.weightx = 1.0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            panelTf.add(tfGpsTime, gc);

            gc.gridx = 2;
            gc.weightx = 0.2;
            panelTf.add(new JLabel(" ["+dateFormat.toLocalizedPattern()+']'), gc);

            gc.gridx = 0;
            gc.gridy = 2;
            gc.gridwidth = gc.gridheight = 1;
            gc.weightx = gc.weighty = 0.0;
            gc.fill = GridBagConstraints.NONE;
            gc.anchor = GridBagConstraints.WEST;
            panelTf.add(new JLabel(tr("I am in the timezone of: ")), gc);

            String[] tmp = TimeZone.getAvailableIDs();
            List<String> vtTimezones = new ArrayList<>(tmp.length);

            for (String tzStr : tmp) {
                TimeZone tz = TimeZone.getTimeZone(tzStr);

                String tzDesc = new StringBuilder(tzStr).append(" (")
                .append(new Timezone(tz.getRawOffset() / 3600000.0).formatTimezone())
                .append(')').toString();
                vtTimezones.add(tzDesc);
            }

            Collections.sort(vtTimezones);

            cbTimezones = new JosmComboBox<>(vtTimezones.toArray(new String[0]));

            String tzId = Main.pref.get("geoimage.timezoneid", "");
            TimeZone defaultTz;
            if (tzId.isEmpty()) {
                defaultTz = TimeZone.getDefault();
            } else {
                defaultTz = TimeZone.getTimeZone(tzId);
            }

            cbTimezones.setSelectedItem(new StringBuilder(defaultTz.getID()).append(" (")
                    .append(new Timezone(defaultTz.getRawOffset() / 3600000.0).formatTimezone())
                    .append(')').toString());

            gc.gridx = 1;
            gc.weightx = 1.0;
            gc.gridwidth = 2;
            gc.fill = GridBagConstraints.HORIZONTAL;
            panelTf.add(cbTimezones, gc);

            panel.add(panelTf, BorderLayout.SOUTH);

            JPanel panelLst = new JPanel(new BorderLayout());

            imgList = new JList<>(new AbstractListModel<String>() {
                @Override
                public String getElementAt(int i) {
                    return yLayer.data.get(i).getFile().getName();
                }

                @Override
                public int getSize() {
                    return yLayer.data != null ? yLayer.data.size() : 0;
                }
            });
            imgList.getSelectionModel().setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            imgList.getSelectionModel().addListSelectionListener(new ListSelectionListener() {

                @Override
                public void valueChanged(ListSelectionEvent arg0) {
                    int index = imgList.getSelectedIndex();
                    Integer orientation = ExifReader.readOrientation(yLayer.data.get(index).getFile());
                    imgDisp.setImage(yLayer.data.get(index).getFile(), orientation);
                    Date date = yLayer.data.get(index).getExifTime();
                    if (date != null) {
                        DateFormat df = DateUtils.getDateTimeFormat(DateFormat.SHORT, DateFormat.MEDIUM);
                        lbExifTime.setText(df.format(date));
                        tfGpsTime.setText(df.format(date));
                        tfGpsTime.setCaretPosition(tfGpsTime.getText().length());
                        tfGpsTime.setEnabled(true);
                        tfGpsTime.requestFocus();
                    } else {
                        lbExifTime.setText(tr("No date"));
                        tfGpsTime.setText("");
                        tfGpsTime.setEnabled(false);
                    }
                }
            });
            panelLst.add(new JScrollPane(imgList), BorderLayout.CENTER);

            JButton openButton = new JButton(tr("Open another photo"));
            openButton.addActionListener(new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent ae) {
                    AbstractFileChooser fc = DiskAccessAction.createAndOpenFileChooser(true, false, null,
                            JpgImporter.FILE_FILTER_WITH_FOLDERS, JFileChooser.FILES_ONLY, "geoimage.lastdirectory");
                    if (fc == null)
                        return;
                    File sel = fc.getSelectedFile();

                    Integer orientation = ExifReader.readOrientation(sel);
                    imgDisp.setImage(sel, orientation);

                    Date date = ExifReader.readTime(sel);
                    if (date != null) {
                        lbExifTime.setText(DateUtils.getDateTimeFormat(DateFormat.SHORT, DateFormat.MEDIUM).format(date));
                        tfGpsTime.setText(DateUtils.getDateFormat(DateFormat.SHORT).format(date)+' ');
                        tfGpsTime.setEnabled(true);
                    } else {
                        lbExifTime.setText(tr("No date"));
                        tfGpsTime.setText("");
                        tfGpsTime.setEnabled(false);
                    }
                }
            });
            panelLst.add(openButton, BorderLayout.PAGE_END);

            panel.add(panelLst, BorderLayout.LINE_START);

            boolean isOk = false;
            while (!isOk) {
                int answer = JOptionPane.showConfirmDialog(
                        Main.parent, panel,
                        tr("Synchronize time from a photo of the GPS receiver"),
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.QUESTION_MESSAGE
                );
                if (answer == JOptionPane.CANCEL_OPTION)
                    return;

                long delta;

                try {
                    delta = dateFormat.parse(lbExifTime.getText()).getTime()
                    - dateFormat.parse(tfGpsTime.getText()).getTime();
                } catch (ParseException e) {
                    JOptionPane.showMessageDialog(Main.parent, tr("Error while parsing the date.\n"
                            + "Please use the requested format"),
                            tr("Invalid date"), JOptionPane.ERROR_MESSAGE);
                    continue;
                }

                String selectedTz = (String) cbTimezones.getSelectedItem();
                int pos = selectedTz.lastIndexOf('(');
                tzId = selectedTz.substring(0, pos - 1);
                String tzValue = selectedTz.substring(pos + 1, selectedTz.length() - 1);

                Main.pref.put("geoimage.timezoneid", tzId);
                tfOffset.setText(Offset.milliseconds(delta).formatOffset());
                tfTimezone.setText(tzValue);

                isOk = true;

            }
            statusBarUpdater.updateStatusBar();
            yLayer.updateBufferAndRepaint();
        }
    }

    @Override
    public void actionPerformed(ActionEvent arg0) {
        // Construct the list of loaded GPX tracks
        Collection<Layer> layerLst = Main.getLayerManager().getLayers();
        GpxDataWrapper defaultItem = null;
        for (Layer cur : layerLst) {
            if (cur instanceof GpxLayer) {
                GpxLayer curGpx = (GpxLayer) cur;
                GpxDataWrapper gdw = new GpxDataWrapper(curGpx.getName(), curGpx.data, curGpx.data.storageFile);
                gpxLst.add(gdw);
                if (cur == yLayer.gpxLayer) {
                    defaultItem = gdw;
                }
            }
        }
        for (GpxData data : loadedGpxData) {
            gpxLst.add(new GpxDataWrapper(data.storageFile.getName(),
                    data,
                    data.storageFile));
        }

        if (gpxLst.isEmpty()) {
            gpxLst.add(new GpxDataWrapper(tr("<No GPX track loaded yet>"), null, null));
        }

        JPanel panelCb = new JPanel();

        panelCb.add(new JLabel(tr("GPX track: ")));

        cbGpx = new JosmComboBox<>(gpxLst.toArray(new GpxDataWrapper[0]));
        if (defaultItem != null) {
            cbGpx.setSelectedItem(defaultItem);
        }
        cbGpx.addActionListener(statusBarUpdaterWithRepaint);
        panelCb.add(cbGpx);

        JButton buttonOpen = new JButton(tr("Open another GPX trace"));
        buttonOpen.addActionListener(new LoadGpxDataActionListener());
        panelCb.add(buttonOpen);

        JPanel panelTf = new JPanel(new GridBagLayout());

        String prefTimezone = Main.pref.get("geoimage.timezone", "0:00");
        if (prefTimezone == null) {
            prefTimezone = "0:00";
        }
        try {
            timezone = Timezone.parseTimezone(prefTimezone);
        } catch (ParseException e) {
            timezone = Timezone.ZERO;
        }

        tfTimezone = new JosmTextField(10);
        tfTimezone.setText(timezone.formatTimezone());

        try {
            delta = Offset.parseOffset(Main.pref.get("geoimage.delta", "0"));
        } catch (ParseException e) {
            delta = Offset.ZERO;
        }

        tfOffset = new JosmTextField(10);
        tfOffset.setText(delta.formatOffset());

        JButton buttonViewGpsPhoto = new JButton(tr("<html>Use photo of an accurate clock,<br>"
                + "e.g. GPS receiver display</html>"));
        buttonViewGpsPhoto.setIcon(ImageProvider.get("clock"));
        buttonViewGpsPhoto.addActionListener(new SetOffsetActionListener());

        JButton buttonAutoGuess = new JButton(tr("Auto-Guess"));
        buttonAutoGuess.setToolTipText(tr("Matches first photo with first gpx point"));
        buttonAutoGuess.addActionListener(new AutoGuessActionListener());

        JButton buttonAdjust = new JButton(tr("Manual adjust"));
        buttonAdjust.addActionListener(new AdjustActionListener());

        JLabel labelPosition = new JLabel(tr("Override position for: "));

        int numAll = getSortedImgList(true, true).size();
        int numExif = numAll - getSortedImgList(false, true).size();
        int numTagged = numAll - getSortedImgList(true, false).size();

        cbExifImg = new JCheckBox(tr("Images with geo location in exif data ({0}/{1})", numExif, numAll));
        cbExifImg.setEnabled(numExif != 0);

        cbTaggedImg = new JCheckBox(tr("Images that are already tagged ({0}/{1})", numTagged, numAll), true);
        cbTaggedImg.setEnabled(numTagged != 0);

        labelPosition.setEnabled(cbExifImg.isEnabled() || cbTaggedImg.isEnabled());

        boolean ticked = yLayer.thumbsLoaded || Main.pref.getBoolean("geoimage.showThumbs", false);
        cbShowThumbs = new JCheckBox(tr("Show Thumbnail images on the map"), ticked);
        cbShowThumbs.setEnabled(!yLayer.thumbsLoaded);

        int y = 0;
        GBC gbc = GBC.eol();
        gbc.gridx = 0;
        gbc.gridy = y++;
        panelTf.add(panelCb, gbc);

        gbc = GBC.eol().fill(GBC.HORIZONTAL).insets(0, 0, 0, 12);
        gbc.gridx = 0;
        gbc.gridy = y++;
        panelTf.add(new JSeparator(SwingConstants.HORIZONTAL), gbc);

        gbc = GBC.std();
        gbc.gridx = 0;
        gbc.gridy = y;
        panelTf.add(new JLabel(tr("Timezone: ")), gbc);

        gbc = GBC.std().fill(GBC.HORIZONTAL);
        gbc.gridx = 1;
        gbc.gridy = y++;
        gbc.weightx = 1.;
        panelTf.add(tfTimezone, gbc);

        gbc = GBC.std();
        gbc.gridx = 0;
        gbc.gridy = y;
        panelTf.add(new JLabel(tr("Offset:")), gbc);

        gbc = GBC.std().fill(GBC.HORIZONTAL);
        gbc.gridx = 1;
        gbc.gridy = y++;
        gbc.weightx = 1.;
        panelTf.add(tfOffset, gbc);

        gbc = GBC.std().insets(5, 5, 5, 5);
        gbc.gridx = 2;
        gbc.gridy = y-2;
        gbc.gridheight = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.BOTH;
        gbc.weightx = 0.5;
        panelTf.add(buttonViewGpsPhoto, gbc);

        gbc = GBC.std().fill(GBC.BOTH).insets(5, 5, 5, 5);
        gbc.gridx = 2;
        gbc.gridy = y++;
        gbc.weightx = 0.5;
        panelTf.add(buttonAutoGuess, gbc);

        gbc.gridx = 3;
        panelTf.add(buttonAdjust, gbc);

        gbc = GBC.eol().fill(GBC.HORIZONTAL).insets(0, 12, 0, 0);
        gbc.gridx = 0;
        gbc.gridy = y++;
        panelTf.add(new JSeparator(SwingConstants.HORIZONTAL), gbc);

        gbc = GBC.eol();
        gbc.gridx = 0;
        gbc.gridy = y++;
        panelTf.add(labelPosition, gbc);

        gbc = GBC.eol();
        gbc.gridx = 1;
        gbc.gridy = y++;
        panelTf.add(cbExifImg, gbc);

        gbc = GBC.eol();
        gbc.gridx = 1;
        gbc.gridy = y++;
        panelTf.add(cbTaggedImg, gbc);

        gbc = GBC.eol();
        gbc.gridx = 0;
        gbc.gridy = y;
        panelTf.add(cbShowThumbs, gbc);

        final JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        statusBar.setBorder(BorderFactory.createLoweredBevelBorder());
        statusBarText = new JLabel(" ");
        statusBarText.setFont(statusBarText.getFont().deriveFont(8));
        statusBar.add(statusBarText);

        tfTimezone.addFocusListener(repaintTheMap);
        tfOffset.addFocusListener(repaintTheMap);

        tfTimezone.getDocument().addDocumentListener(statusBarUpdater);
        tfOffset.getDocument().addDocumentListener(statusBarUpdater);
        cbExifImg.addItemListener(statusBarUpdaterWithRepaint);
        cbTaggedImg.addItemListener(statusBarUpdaterWithRepaint);

        statusBarUpdater.updateStatusBar();

        outerPanel = new JPanel(new BorderLayout());
        outerPanel.add(statusBar, BorderLayout.PAGE_END);

        if (!GraphicsEnvironment.isHeadless()) {
            syncDialog = new ExtendedDialog(
                    Main.parent,
                    tr("Correlate images with GPX track"),
                    new String[] {tr("Correlate"), tr("Cancel")},
                    false
            );
            syncDialog.setContent(panelTf, false);
            syncDialog.setButtonIcons(new String[] {"ok", "cancel"});
            syncDialog.setupDialog();
            outerPanel.add(syncDialog.getContentPane(), BorderLayout.PAGE_START);
            syncDialog.setContentPane(outerPanel);
            syncDialog.pack();
            syncDialog.addWindowListener(new SyncDialogWindowListener());
            syncDialog.showDialog();
        }
    }

    private final transient StatusBarUpdater statusBarUpdater = new StatusBarUpdater(false);
    private final transient StatusBarUpdater statusBarUpdaterWithRepaint = new StatusBarUpdater(true);

    private class StatusBarUpdater implements  DocumentListener, ItemListener, ActionListener {
        private final boolean doRepaint;

        StatusBarUpdater(boolean doRepaint) {
            this.doRepaint = doRepaint;
        }

        @Override
        public void insertUpdate(DocumentEvent ev) {
            updateStatusBar();
        }

        @Override
        public void removeUpdate(DocumentEvent ev) {
            updateStatusBar();
        }

        @Override
        public void changedUpdate(DocumentEvent ev) {
            // Do nothing
        }

        @Override
        public void itemStateChanged(ItemEvent e) {
            updateStatusBar();
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            updateStatusBar();
        }

        public void updateStatusBar() {
            statusBarText.setText(statusText());
            if (doRepaint) {
                yLayer.updateBufferAndRepaint();
            }
        }

        private String statusText() {
            try {
                timezone = Timezone.parseTimezone(tfTimezone.getText().trim());
                delta = Offset.parseOffset(tfOffset.getText().trim());
            } catch (ParseException e) {
                return e.getMessage();
            }

            // The selection of images we are about to correlate may have changed.
            // So reset all images.
            if (yLayer.data != null) {
                for (ImageEntry ie: yLayer.data) {
                    ie.discardTmp();
                }
            }

            // Construct a list of images that have a date, and sort them on the date.
            List<ImageEntry> dateImgLst = getSortedImgList();
            // Create a temporary copy for each image
            for (ImageEntry ie : dateImgLst) {
                ie.createTmp();
                ie.tmp.setPos(null);
            }

            GpxDataWrapper selGpx = selectedGPX(false);
            if (selGpx == null)
                return tr("No gpx selected");

            final long offsetMs = ((long) (timezone.getHours() * 3600 * 1000)) + delta.getMilliseconds(); // in milliseconds
            lastNumMatched = matchGpxTrack(dateImgLst, selGpx.data, offsetMs);

            return trn("<html>Matched <b>{0}</b> of <b>{1}</b> photo to GPX track.</html>",
                    "<html>Matched <b>{0}</b> of <b>{1}</b> photos to GPX track.</html>",
                    dateImgLst.size(), lastNumMatched, dateImgLst.size());
        }
    }

    private final transient RepaintTheMapListener repaintTheMap = new RepaintTheMapListener();

    private class RepaintTheMapListener implements FocusListener {
        @Override
        public void focusGained(FocusEvent e) { // do nothing
        }

        @Override
        public void focusLost(FocusEvent e) {
            yLayer.updateBufferAndRepaint();
        }
    }

    /**
     * Presents dialog with sliders for manual adjust.
     */
    private class AdjustActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent arg0) {

            final Offset offset = Offset.milliseconds(
                    delta.getMilliseconds() + Math.round(timezone.getHours() * 60 * 60 * 1000));
            final int dayOffset = offset.getDayOffset();
            final Pair<Timezone, Offset> timezoneOffsetPair = offset.withoutDayOffset().splitOutTimezone();

            // Info Labels
            final JLabel lblMatches = new JLabel();

            // Timezone Slider
            // The slider allows to switch timezon from -12:00 to 12:00 in 30 minutes steps. Therefore the range is -24 to 24.
            final JLabel lblTimezone = new JLabel();
            final JSlider sldTimezone = new JSlider(-24, 24, 0);
            sldTimezone.setPaintLabels(true);
            Dictionary<Integer, JLabel> labelTable = new Hashtable<>();
            // CHECKSTYLE.OFF: ParenPad
            for (int i = -12; i <= 12; i += 6) {
                labelTable.put(i * 2, new JLabel(new Timezone(i).formatTimezone()));
            }
            // CHECKSTYLE.ON: ParenPad
            sldTimezone.setLabelTable(labelTable);

            // Minutes Slider
            final JLabel lblMinutes = new JLabel();
            final JSlider sldMinutes = new JSlider(-15, 15, 0);
            sldMinutes.setPaintLabels(true);
            sldMinutes.setMajorTickSpacing(5);

            // Seconds slider
            final JLabel lblSeconds = new JLabel();
            final JSlider sldSeconds = new JSlider(-600, 600, 0);
            sldSeconds.setPaintLabels(true);
            labelTable = new Hashtable<>();
            // CHECKSTYLE.OFF: ParenPad
            for (int i = -60; i <= 60; i += 30) {
                labelTable.put(i * 10, new JLabel(Offset.seconds(i).formatOffset()));
            }
            // CHECKSTYLE.ON: ParenPad
            sldSeconds.setLabelTable(labelTable);
            sldSeconds.setMajorTickSpacing(300);

            // This is called whenever one of the sliders is moved.
            // It updates the labels and also calls the "match photos" code
            class SliderListener implements ChangeListener {
                @Override
                public void stateChanged(ChangeEvent e) {
                    timezone = new Timezone(sldTimezone.getValue() / 2.);

                    lblTimezone.setText(tr("Timezone: {0}", timezone.formatTimezone()));
                    lblMinutes.setText(tr("Minutes: {0}", sldMinutes.getValue()));
                    lblSeconds.setText(tr("Seconds: {0}", Offset.milliseconds(100L * sldSeconds.getValue()).formatOffset()));

                    delta = Offset.milliseconds(100L * sldSeconds.getValue()
                            + 1000L * 60 * sldMinutes.getValue()
                            + 1000L * 60 * 60 * 24 * dayOffset);

                    tfTimezone.getDocument().removeDocumentListener(statusBarUpdater);
                    tfOffset.getDocument().removeDocumentListener(statusBarUpdater);

                    tfTimezone.setText(timezone.formatTimezone());
                    tfOffset.setText(delta.formatOffset());

                    tfTimezone.getDocument().addDocumentListener(statusBarUpdater);
                    tfOffset.getDocument().addDocumentListener(statusBarUpdater);

                    lblMatches.setText(statusBarText.getText() + "<br>" + trn("(Time difference of {0} day)",
                            "Time difference of {0} days", Math.abs(dayOffset), Math.abs(dayOffset)));

                    statusBarUpdater.updateStatusBar();
                    yLayer.updateBufferAndRepaint();
                }
            }

            // Put everything together
            JPanel p = new JPanel(new GridBagLayout());
            p.setPreferredSize(new Dimension(400, 230));
            p.add(lblMatches, GBC.eol().fill());
            p.add(lblTimezone, GBC.eol().fill());
            p.add(sldTimezone, GBC.eol().fill().insets(0, 0, 0, 10));
            p.add(lblMinutes, GBC.eol().fill());
            p.add(sldMinutes, GBC.eol().fill().insets(0, 0, 0, 10));
            p.add(lblSeconds, GBC.eol().fill());
            p.add(sldSeconds, GBC.eol().fill());

            // If there's an error in the calculation the found values
            // will be off range for the sliders. Catch this error
            // and inform the user about it.
            try {
                sldTimezone.setValue((int) (timezoneOffsetPair.a.getHours() * 2));
                sldMinutes.setValue((int) (timezoneOffsetPair.b.getSeconds() / 60));
                final long deciSeconds = timezoneOffsetPair.b.getMilliseconds() / 100;
                sldSeconds.setValue((int) (deciSeconds % 60));
            } catch (RuntimeException e) {
                JOptionPane.showMessageDialog(Main.parent,
                        tr("An error occurred while trying to match the photos to the GPX track."
                                +" You can adjust the sliders to manually match the photos."),
                                tr("Matching photos to track failed"),
                                JOptionPane.WARNING_MESSAGE);
            }

            // Call the sliderListener once manually so labels get adjusted
            new SliderListener().stateChanged(null);
            // Listeners added here, otherwise it tries to match three times
            // (when setting the default values)
            sldTimezone.addChangeListener(new SliderListener());
            sldMinutes.addChangeListener(new SliderListener());
            sldSeconds.addChangeListener(new SliderListener());

            // There is no way to cancel this dialog, all changes get applied
            // immediately. Therefore "Close" is marked with an "OK" icon.
            // Settings are only saved temporarily to the layer.
            new ExtendedDialog(Main.parent,
                    tr("Adjust timezone and offset"),
                    new String[] {tr("Close")}).
                    setContent(p).setButtonIcons(new String[] {"ok"}).showDialog();
        }
    }

    static class NoGpxTimestamps extends Exception {
    }

    /**
     * Tries to auto-guess the timezone and offset.
     *
     * @param imgs the images to correlate
     * @param gpx the gpx track to correlate to
     * @return a pair of timezone and offset
     * @throws IndexOutOfBoundsException when there are no images
     * @throws NoGpxTimestamps when the gpx track does not contain a timestamp
     */
    static Pair<Timezone, Offset> autoGuess(List<ImageEntry> imgs, GpxData gpx) throws IndexOutOfBoundsException, NoGpxTimestamps {

        // Init variables
        long firstExifDate = imgs.get(0).getExifTime().getTime();

        long firstGPXDate = -1;
        // Finds first GPX point
        outer: for (GpxTrack trk : gpx.tracks) {
            for (GpxTrackSegment segment : trk.getSegments()) {
                for (WayPoint curWp : segment.getWayPoints()) {
                    final Date parsedTime = curWp.setTimeFromAttribute();
                    if (parsedTime != null) {
                        firstGPXDate = parsedTime.getTime();
                        break outer;
                    }
                }
            }
        }

        if (firstGPXDate < 0) {
            throw new NoGpxTimestamps();
        }

        return Offset.milliseconds(firstExifDate - firstGPXDate).splitOutTimezone();
    }

    private class AutoGuessActionListener implements ActionListener {

        @Override
        public void actionPerformed(ActionEvent arg0) {
            GpxDataWrapper gpxW = selectedGPX(true);
            if (gpxW == null)
                return;
            GpxData gpx = gpxW.data;

            List<ImageEntry> imgs = getSortedImgList();

            try {
                final Pair<Timezone, Offset> r = autoGuess(imgs, gpx);
                timezone = r.a;
                delta = r.b;
            } catch (IndexOutOfBoundsException ex) {
                JOptionPane.showMessageDialog(Main.parent,
                        tr("The selected photos do not contain time information."),
                        tr("Photos do not contain time information"), JOptionPane.WARNING_MESSAGE);
                return;
            } catch (NoGpxTimestamps ex) {
                JOptionPane.showMessageDialog(Main.parent,
                        tr("The selected GPX track does not contain timestamps. Please select another one."),
                        tr("GPX Track has no time information"), JOptionPane.WARNING_MESSAGE);
                return;
            }

            tfTimezone.getDocument().removeDocumentListener(statusBarUpdater);
            tfOffset.getDocument().removeDocumentListener(statusBarUpdater);

            tfTimezone.setText(timezone.formatTimezone());
            tfOffset.setText(delta.formatOffset());
            tfOffset.requestFocus();

            tfTimezone.getDocument().addDocumentListener(statusBarUpdater);
            tfOffset.getDocument().addDocumentListener(statusBarUpdater);

            statusBarUpdater.updateStatusBar();
            yLayer.updateBufferAndRepaint();
        }
    }

    private List<ImageEntry> getSortedImgList() {
        return getSortedImgList(cbExifImg.isSelected(), cbTaggedImg.isSelected());
    }

    /**
     * Returns a list of images that fulfill the given criteria.
     * Default setting is to return untagged images, but may be overwritten.
     * @param exif also returns images with exif-gps info
     * @param tagged also returns tagged images
     * @return matching images
     */
    private List<ImageEntry> getSortedImgList(boolean exif, boolean tagged) {
        if (yLayer.data == null) {
            return Collections.emptyList();
        }
        List<ImageEntry> dateImgLst = new ArrayList<>(yLayer.data.size());
        for (ImageEntry e : yLayer.data) {
            if (!e.hasExifTime()) {
                continue;
            }

            if (e.getExifCoor() != null && !exif) {
                continue;
            }

            if (e.isTagged() && e.getExifCoor() == null && !tagged) {
                continue;
            }

            dateImgLst.add(e);
        }

        Collections.sort(dateImgLst, new Comparator<ImageEntry>() {
            @Override
            public int compare(ImageEntry arg0, ImageEntry arg1) {
                return arg0.getExifTime().compareTo(arg1.getExifTime());
            }
        });

        return dateImgLst;
    }

    private GpxDataWrapper selectedGPX(boolean complain) {
        Object item = cbGpx.getSelectedItem();

        if (item == null || ((GpxDataWrapper) item).file == null) {
            if (complain) {
                JOptionPane.showMessageDialog(Main.parent, tr("You should select a GPX track"),
                        tr("No selected GPX track"), JOptionPane.ERROR_MESSAGE);
            }
            return null;
        }
        return (GpxDataWrapper) item;
    }

    /**
     * Match a list of photos to a gpx track with a given offset.
     * All images need a exifTime attribute and the List must be sorted according to these times.
     * @param images images to match
     * @param selectedGpx selected GPX data
     * @param offset offset
     * @return number of matched points
     */
    static int matchGpxTrack(List<ImageEntry> images, GpxData selectedGpx, long offset) {
        int ret = 0;

        for (GpxTrack trk : selectedGpx.tracks) {
            for (GpxTrackSegment segment : trk.getSegments()) {

                long prevWpTime = 0;
                WayPoint prevWp = null;

                for (WayPoint curWp : segment.getWayPoints()) {
                    final Date parsedTime = curWp.setTimeFromAttribute();
                    if (parsedTime != null) {
                        final long curWpTime = parsedTime.getTime() + offset;
                        ret += matchPoints(images, prevWp, prevWpTime, curWp, curWpTime, offset);

                        prevWp = curWp;
                        prevWpTime = curWpTime;
                        continue;
                    }
                    prevWp = null;
                    prevWpTime = 0;
                }
            }
        }
        return ret;
    }

    private static Double getElevation(WayPoint wp) {
        String value = wp.getString(GpxConstants.PT_ELE);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.valueOf(value);
            } catch (NumberFormatException e) {
                Main.warn(e);
            }
        }
        return null;
    }

    static int matchPoints(List<ImageEntry> images, WayPoint prevWp, long prevWpTime,
            WayPoint curWp, long curWpTime, long offset) {
        // Time between the track point and the previous one, 5 sec if first point, i.e. photos take
        // 5 sec before the first track point can be assumed to be take at the starting position
        long interval = prevWpTime > 0 ? Math.abs(curWpTime - prevWpTime) : 5*1000;
        int ret = 0;

        // i is the index of the timewise last photo that has the same or earlier EXIF time
        int i = getLastIndexOfListBefore(images, curWpTime);

        // no photos match
        if (i < 0)
            return 0;

        Double speed = null;
        Double prevElevation = null;

        if (prevWp != null) {
            double distance = prevWp.getCoor().greatCircleDistance(curWp.getCoor());
            // This is in km/h, 3.6 * m/s
            if (curWpTime > prevWpTime) {
                speed = 3600 * distance / (curWpTime - prevWpTime);
            }
            prevElevation = getElevation(prevWp);
        }

        Double curElevation = getElevation(curWp);

        // First trackpoint, then interval is set to five seconds, i.e. photos up to five seconds
        // before the first point will be geotagged with the starting point
        if (prevWpTime == 0 || curWpTime <= prevWpTime) {
            while (i >= 0) {
                final ImageEntry curImg = images.get(i);
                long time = curImg.getExifTime().getTime();
                if (time > curWpTime || time < curWpTime - interval) {
                    break;
                }
                if (curImg.tmp.getPos() == null) {
                    curImg.tmp.setPos(curWp.getCoor());
                    curImg.tmp.setSpeed(speed);
                    curImg.tmp.setElevation(curElevation);
                    curImg.tmp.setGpsTime(new Date(curImg.getExifTime().getTime() - offset));
                    curImg.flagNewGpsData();
                    ret++;
                }
                i--;
            }
            return ret;
        }

        // This code gives a simple linear interpolation of the coordinates between current and
        // previous track point assuming a constant speed in between
        while (i >= 0) {
            ImageEntry curImg = images.get(i);
            long imgTime = curImg.getExifTime().getTime();
            if (imgTime < prevWpTime) {
                break;
            }

            if (curImg.tmp.getPos() == null && prevWp != null) {
                // The values of timeDiff are between 0 and 1, it is not seconds but a dimensionless variable
                double timeDiff = (double) (imgTime - prevWpTime) / interval;
                curImg.tmp.setPos(prevWp.getCoor().interpolate(curWp.getCoor(), timeDiff));
                curImg.tmp.setSpeed(speed);
                if (curElevation != null && prevElevation != null) {
                    curImg.tmp.setElevation(prevElevation + (curElevation - prevElevation) * timeDiff);
                }
                curImg.tmp.setGpsTime(new Date(curImg.getExifTime().getTime() - offset));
                curImg.flagNewGpsData();

                ret++;
            }
            i--;
        }
        return ret;
    }

    private static int getLastIndexOfListBefore(List<ImageEntry> images, long searchedTime) {
        int lstSize = images.size();

        // No photos or the first photo taken is later than the search period
        if (lstSize == 0 || searchedTime < images.get(0).getExifTime().getTime())
            return -1;

        // The search period is later than the last photo
        if (searchedTime > images.get(lstSize - 1).getExifTime().getTime())
            return lstSize-1;

        // The searched index is somewhere in the middle, do a binary search from the beginning
        int curIndex;
        int startIndex = 0;
        int endIndex = lstSize-1;
        while (endIndex - startIndex > 1) {
            curIndex = (endIndex + startIndex) / 2;
            if (searchedTime > images.get(curIndex).getExifTime().getTime()) {
                startIndex = curIndex;
            } else {
                endIndex = curIndex;
            }
        }
        if (searchedTime < images.get(endIndex).getExifTime().getTime())
            return startIndex;

        // This final loop is to check if photos with the exact same EXIF time follows
        while ((endIndex < (lstSize-1)) && (images.get(endIndex).getExifTime().getTime()
                == images.get(endIndex + 1).getExifTime().getTime())) {
            endIndex++;
        }
        return endIndex;
    }

    static final class Timezone {

        static final Timezone ZERO = new Timezone(0.0);
        private final double timezone;

        Timezone(double hours) {
            this.timezone = hours;
        }

        public double getHours() {
            return timezone;
        }

        String formatTimezone() {
            StringBuilder ret = new StringBuilder();

            double timezone = this.timezone;
            if (timezone < 0) {
                ret.append('-');
                timezone = -timezone;
            } else {
                ret.append('+');
            }
            ret.append((long) timezone).append(':');
            int minutes = (int) ((timezone % 1) * 60);
            if (minutes < 10) {
                ret.append('0');
            }
            ret.append(minutes);

            return ret.toString();
        }

        static Timezone parseTimezone(String timezone) throws ParseException {

            if (timezone.isEmpty())
                return ZERO;

            String error = tr("Error while parsing timezone.\nExpected format: {0}", "+H:MM");

            char sgnTimezone = '+';
            StringBuilder hTimezone = new StringBuilder();
            StringBuilder mTimezone = new StringBuilder();
            int state = 1; // 1=start/sign, 2=hours, 3=minutes.
            for (int i = 0; i < timezone.length(); i++) {
                char c = timezone.charAt(i);
                switch (c) {
                    case ' ':
                        if (state != 2 || hTimezone.length() != 0)
                            throw new ParseException(error, i);
                        break;
                    case '+':
                    case '-':
                        if (state == 1) {
                            sgnTimezone = c;
                            state = 2;
                        } else
                            throw new ParseException(error, i);
                        break;
                    case ':':
                    case '.':
                        if (state == 2) {
                            state = 3;
                        } else
                            throw new ParseException(error, i);
                        break;
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        switch (state) {
                            case 1:
                            case 2:
                                state = 2;
                                hTimezone.append(c);
                                break;
                            case 3:
                                mTimezone.append(c);
                                break;
                            default:
                                throw new ParseException(error, i);
                        }
                        break;
                    default:
                        throw new ParseException(error, i);
                }
            }

            int h = 0;
            int m = 0;
            try {
                h = Integer.parseInt(hTimezone.toString());
                if (mTimezone.length() > 0) {
                    m = Integer.parseInt(mTimezone.toString());
                }
            } catch (NumberFormatException nfe) {
                // Invalid timezone
                throw (ParseException) new ParseException(error, 0).initCause(nfe);
            }

            if (h > 12 || m > 59)
                throw new ParseException(error, 0);
            else
                return new Timezone((h + m / 60.0) * (sgnTimezone == '-' ? -1 : 1));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Timezone)) return false;
            Timezone timezone1 = (Timezone) o;
            return Double.compare(timezone1.timezone, timezone) == 0;
        }

        @Override
        public int hashCode() {
            return Objects.hash(timezone);
        }
    }

    static final class Offset {

        static final Offset ZERO = new Offset(0);
        private final long milliseconds;

        private Offset(long milliseconds) {
            this.milliseconds = milliseconds;
        }

        static Offset milliseconds(long milliseconds) {
            return new Offset(milliseconds);
        }

        static Offset seconds(long seconds) {
            return new Offset(1000 * seconds);
        }

        long getMilliseconds() {
            return milliseconds;
        }

        long getSeconds() {
            return milliseconds / 1000;
        }

        String formatOffset() {
            if (milliseconds % 1000 == 0) {
                return Long.toString(milliseconds / 1000);
            } else if (milliseconds % 100 == 0) {
                return String.format(Locale.ENGLISH, "%.1f", milliseconds / 1000.);
            } else {
                return String.format(Locale.ENGLISH, "%.3f", milliseconds / 1000.);
            }
        }

        static Offset parseOffset(String offset) throws ParseException {
            String error = tr("Error while parsing offset.\nExpected format: {0}", "number");

            if (!offset.isEmpty()) {
                try {
                    if (offset.startsWith("+")) {
                        offset = offset.substring(1);
                    }
                    return Offset.milliseconds(Math.round(Double.parseDouble(offset) * 1000));
                } catch (NumberFormatException nfe) {
                    throw (ParseException) new ParseException(error, 0).initCause(nfe);
                }
            } else {
                return Offset.ZERO;
            }
        }

        int getDayOffset() {
            final double diffInH = getMilliseconds() / 1000. / 60 / 60; // hours

            // Find day difference
            return (int) Math.round(diffInH / 24);
        }

        Offset withoutDayOffset() {
            return milliseconds(getMilliseconds() - getDayOffset() * 24L * 60L * 60L * 1000L);
        }

        Pair<Timezone, Offset> splitOutTimezone() {
            // In hours
            double tz = withoutDayOffset().getSeconds() / 3600.0;

            // Due to imprecise clocks we might get a "+3:28" timezone, which should obviously be 3:30 with
            // -2 minutes offset. This determines the real timezone and finds offset.
            final double timezone = (double) Math.round(tz * 2) / 2; // hours, rounded to one decimal place
            final long delta = Math.round(getMilliseconds() - timezone * 60 * 60 * 1000); // milliseconds
            return Pair.create(new Timezone(timezone), Offset.milliseconds(delta));
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Offset)) return false;
            Offset offset = (Offset) o;
            return milliseconds == offset.milliseconds;
        }

        @Override
        public int hashCode() {
            return Objects.hash(milliseconds);
        }
    }
}
