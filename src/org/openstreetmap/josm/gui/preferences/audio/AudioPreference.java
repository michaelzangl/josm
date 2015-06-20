// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.audio;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagLayout;

import javax.swing.Box;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.tools.GBC;

/**
 * Audio preferences.
 */
public final class AudioPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code AudioPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new AudioPreference();
        }
    }

    private AudioPreference() {
        super(/* ICON(preferences/) */ "audio", tr("Audio Settings"), tr("Settings for the audio player and audio markers."));
    }

    private JCheckBox audioMenuVisible = new JCheckBox(tr("Display the Audio menu."));
    private JCheckBox markerButtonLabels = new JCheckBox(tr("Label audio (and image and web) markers."));
    private JCheckBox markerAudioTraceVisible = new JCheckBox(tr("Display live audio trace."));

    // various methods of making markers on import audio
    private JCheckBox audioMarkersFromExplicitWaypoints = new JCheckBox(tr("Explicit waypoints with valid timestamps."));
    private JCheckBox audioMarkersFromUntimedWaypoints = new JCheckBox(tr("Explicit waypoints with time estimated from track position."));
    private JCheckBox audioMarkersFromNamedTrackpoints = new JCheckBox(tr("Named trackpoints."));
    private JCheckBox audioMarkersFromWavTimestamps = new JCheckBox(tr("Modified times (time stamps) of audio files."));
    private JCheckBox audioMarkersFromStart = new JCheckBox(tr("Start of track (will always do this if no other markers available)."));

    private JosmTextField audioLeadIn = new JosmTextField(8);
    private JosmTextField audioForwardBackAmount = new JosmTextField(8);
    private JosmTextField audioFastForwardMultiplier = new JosmTextField(8);
    private JosmTextField audioCalibration = new JosmTextField(8);

    @Override
    public void addGui(PreferenceTabbedPane gui) {
        JPanel audio = new JPanel(new GridBagLayout());

        // audioMenuVisible
        audioMenuVisible.setSelected(!Main.pref.getBoolean("audio.menuinvisible"));
        audioMenuVisible.setToolTipText(tr("Show or hide the audio menu entry on the main menu bar."));
        audio.add(audioMenuVisible, GBC.eol().insets(0,0,0,0));

        // audioTraceVisible
        markerAudioTraceVisible.setSelected(Main.pref.getBoolean("marker.traceaudio", true));
        markerAudioTraceVisible.setToolTipText(
                tr("Display a moving icon representing the point on the synchronized track where the audio currently playing was recorded."));
        audio.add(markerAudioTraceVisible, GBC.eol().insets(0,0,0,0));

        // buttonLabels
        markerButtonLabels.setSelected(Main.pref.getBoolean("marker.buttonlabels", true));
        markerButtonLabels.setToolTipText(tr("Put text labels against audio (and image and web) markers as well as their button icons."));
        audio.add(markerButtonLabels, GBC.eol().insets(0,0,0,0));

        audio.add(new JLabel(tr("When importing audio, make markers from...")), GBC.eol());

        // audioMarkersFromExplicitWaypoints
        audioMarkersFromExplicitWaypoints.setSelected(Main.pref.getBoolean("marker.audiofromexplicitwaypoints", true));
        audioMarkersFromExplicitWaypoints.setToolTipText(tr("When importing audio, apply it to any waypoints in the GPX layer."));
        audio.add(audioMarkersFromExplicitWaypoints, GBC.eol().insets(10,0,0,0));

        // audioMarkersFromUntimedWaypoints
        audioMarkersFromUntimedWaypoints.setSelected(Main.pref.getBoolean("marker.audiofromuntimedwaypoints", true));
        audioMarkersFromUntimedWaypoints.setToolTipText(tr("When importing audio, apply it to any waypoints in the GPX layer."));
        audio.add(audioMarkersFromUntimedWaypoints, GBC.eol().insets(10,0,0,0));

        // audioMarkersFromNamedTrackpoints
        audioMarkersFromNamedTrackpoints.setSelected(Main.pref.getBoolean("marker.audiofromnamedtrackpoints", false));
        audioMarkersFromNamedTrackpoints.setToolTipText(
                tr("Automatically create audio markers from trackpoints (rather than explicit waypoints) with names or descriptions."));
        audio.add(audioMarkersFromNamedTrackpoints, GBC.eol().insets(10,0,0,0));

        // audioMarkersFromWavTimestamps
        audioMarkersFromWavTimestamps.setSelected(Main.pref.getBoolean("marker.audiofromwavtimestamps", false));
        audioMarkersFromWavTimestamps.setToolTipText(
                tr("Create audio markers at the position on the track corresponding to the modified time of each audio WAV file imported."));
        audio.add(audioMarkersFromWavTimestamps, GBC.eol().insets(10,0,0,0));

        // audioMarkersFromStart
        audioMarkersFromStart.setSelected(Main.pref.getBoolean("marker.audiofromstart"));
        audioMarkersFromStart.setToolTipText(
                tr("Automatically create audio markers from trackpoints (rather than explicit waypoints) with names or descriptions."));
        audio.add(audioMarkersFromStart, GBC.eol().insets(10,0,0,0));

        audioForwardBackAmount.setText(Main.pref.get("audio.forwardbackamount", "10.0"));
        audioForwardBackAmount.setToolTipText(tr("The number of seconds to jump forward or back when the relevant button is pressed"));
        audio.add(new JLabel(tr("Forward/back time (seconds)")), GBC.std());
        audio.add(audioForwardBackAmount, GBC.eol().fill(GBC.HORIZONTAL).insets(5,0,0,5));

        audioFastForwardMultiplier.setText(Main.pref.get("audio.fastfwdmultiplier", "1.3"));
        audioFastForwardMultiplier.setToolTipText(tr("The amount by which the speed is multiplied for fast forwarding"));
        audio.add(new JLabel(tr("Fast forward multiplier")), GBC.std());
        audio.add(audioFastForwardMultiplier, GBC.eol().fill(GBC.HORIZONTAL).insets(5,0,0,5));

        audioLeadIn.setText(Main.pref.get("audio.leadin", "1.0"));
        audioLeadIn.setToolTipText(
                tr("Playback starts this number of seconds before (or after, if negative) the audio track position requested"));
        audio.add(new JLabel(tr("Lead-in time (seconds)")), GBC.std());
        audio.add(audioLeadIn, GBC.eol().fill(GBC.HORIZONTAL).insets(5,0,0,5));

        audioCalibration.setText(Main.pref.get("audio.calibration", "1.0"));
        audioCalibration.setToolTipText(tr("The ratio of voice recorder elapsed time to true elapsed time"));
        audio.add(new JLabel(tr("Voice recorder calibration")), GBC.std());
        audio.add(audioCalibration, GBC.eol().fill(GBC.HORIZONTAL).insets(5,0,0,5));

        audio.add(Box.createVerticalGlue(), GBC.eol().fill(GBC.VERTICAL));

        createPreferenceTabWithScrollPane(gui, audio);
    }

    @Override
    public boolean ok() {
        Main.pref.put("audio.menuinvisible", !audioMenuVisible.isSelected());
        Main.pref.put("marker.traceaudio", markerAudioTraceVisible.isSelected());
        Main.pref.put("marker.buttonlabels", markerButtonLabels.isSelected());
        Main.pref.put("marker.audiofromexplicitwaypoints", audioMarkersFromExplicitWaypoints.isSelected());
        Main.pref.put("marker.audiofromuntimedwaypoints", audioMarkersFromUntimedWaypoints.isSelected());
        Main.pref.put("marker.audiofromnamedtrackpoints", audioMarkersFromNamedTrackpoints.isSelected());
        Main.pref.put("marker.audiofromwavtimestamps", audioMarkersFromWavTimestamps.isSelected());
        Main.pref.put("marker.audiofromstart", audioMarkersFromStart.isSelected());
        Main.pref.put("audio.forwardbackamount", audioForwardBackAmount.getText());
        Main.pref.put("audio.fastfwdmultiplier", audioFastForwardMultiplier.getText());
        Main.pref.put("audio.leadin", audioLeadIn.getText());
        Main.pref.put("audio.calibration", audioCalibration.getText());
        return false;
    }
}
