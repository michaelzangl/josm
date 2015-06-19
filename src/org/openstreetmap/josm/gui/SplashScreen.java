// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JSeparator;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;
import javax.swing.border.EtchedBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Version;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.progress.ProgressTaskId;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Predicates;
import org.openstreetmap.josm.tools.Utils;
import org.openstreetmap.josm.tools.WindowGeometry;

/**
 * Show a splash screen so the user knows what is happening during startup.
 * @since 976
 */
public class SplashScreen extends JFrame implements ChangeListener {

    private final SplashProgressMonitor progressMonitor;
    private final SplashScreenProgressRenderer progressRenderer;

    /**
     * Constructs a new {@code SplashScreen}.
     */
    public SplashScreen() {
        setUndecorated(true);

        // Add a nice border to the main splash screen
        JPanel contentPane = (JPanel)this.getContentPane();
        Border margin = new EtchedBorder(1, Color.white, Color.gray);
        contentPane.setBorder(margin);

        // Add a margin from the border to the content
        JPanel innerContentPane = new JPanel();
        innerContentPane.setBorder(new EmptyBorder(10, 10, 2, 10));
        contentPane.add(innerContentPane);
        innerContentPane.setLayout(new GridBagLayout());

        // Add the logo
        JLabel logo = new JLabel(new ImageIcon(ImageProvider.get("logo.svg").getImage().getScaledInstance(128, 129, Image.SCALE_SMOOTH)));
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.gridheight = 2;
        gbc.insets = new Insets(0, 0, 0, 70);
        innerContentPane.add(logo, gbc);

        // Add the name of this application
        JLabel caption = new JLabel("JOSM – " + tr("Java OpenStreetMap Editor"));
        caption.setFont(GuiHelper.getTitleFont());
        gbc.gridheight = 1;
        gbc.gridx = 1;
        gbc.insets = new Insets(30, 0, 0, 0);
        innerContentPane.add(caption, gbc);

        // Add the version number
        JLabel version = new JLabel(tr("Version {0}", Version.getInstance().getVersionString()));
        gbc.gridy = 1;
        gbc.insets = new Insets(0, 0, 0, 0);
        innerContentPane.add(version, gbc);

        // Add a separator to the status text
        JSeparator separator = new JSeparator(JSeparator.HORIZONTAL);
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.gridwidth = 2;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(15, 0, 5, 0);
        innerContentPane.add(separator, gbc);

        // Add a status message
        progressRenderer = new SplashScreenProgressRenderer();
        gbc.gridy = 3;
        gbc.insets = new Insets(0, 0, 10, 0);
        innerContentPane.add(progressRenderer, gbc);
        progressMonitor = new SplashProgressMonitor(null, this);

        pack();

        WindowGeometry.centerOnScreen(this.getSize(), "gui.geometry").applySafe(this);

        // Add ability to hide splash screen by clicking it
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                setVisible(false);
            }
        });
    }

    @Override
    public void stateChanged(ChangeEvent ignore) {
        progressRenderer.setTasks(progressMonitor.toString());
        pack();
    }

    /**
     * A task (of a {@link ProgressMonitor}).
     */
    private static abstract class Task {

        /**
         * Returns a HTML representation for this task.
         */
        public abstract String toHtml();

        @Override
        public final String toString() {
            return toHtml();
        }
    }

    /**
     * A single task (of a {@link ProgressMonitor}) which keeps track of its execution duration
     * (requires a call to {@link #finish()}).
     */
    private static class MeasurableTask extends Task {
        private final String name;
        private final long start;
        private String duration = "";

        public MeasurableTask(String name) {
            this.name = name;
            this.start = System.currentTimeMillis();
        }

        public void finish() {
            if (!"".equals(duration)) {
                throw new IllegalStateException("This tasks has already been finished");
            }
            duration = tr(" ({0})", Utils.getDurationString(System.currentTimeMillis() - start));
        }

        @Override
        public String toHtml() {
            return name + "<i style='color: #666666;'>" + duration + "</i>";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MeasurableTask that = (MeasurableTask) o;
            return Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(name);
        }
    }

    /**
     * A {@link ProgressMonitor} which stores the (sub)tasks in a tree.
     */
    public static class SplashProgressMonitor extends Task implements ProgressMonitor {

        private final String name;
        private final ChangeListener listener;
        private final List<Task> tasks = Collections.synchronizedList(new ArrayList<Task>());
        private SplashProgressMonitor latestSubtask;

        public SplashProgressMonitor(String name, ChangeListener listener) {
            this.name = name;
            this.listener = listener;
        }

        @Override
        public String toHtml() {
            synchronized (tasks) {
                return Utils.firstNonNull(name, "") + (tasks.isEmpty() ? "" : Utils.joinAsHtmlUnorderedList(tasks));
            }
        }

        @Override
        public void beginTask(String title) {
            if (title != null) {
                final MeasurableTask task = new MeasurableTask(title);
                tasks.add(task);
                listener.stateChanged(null);
            }
        }

        @Override
        public void beginTask(String title, int ticks) {
            this.beginTask(title);
        }

        @Override
        public void setCustomText(String text) {
            this.beginTask(text);
        }

        @Override
        public void setExtraText(String text) {
            this.beginTask(text);
        }

        @Override
        public void indeterminateSubTask(String title) {
            this.subTask(title);
        }

        @Override
        public void subTask(String title) {
            latestSubtask = new SplashProgressMonitor(title, listener);
            tasks.add(latestSubtask);
            listener.stateChanged(null);
        }

        @Override
        public ProgressMonitor createSubTaskMonitor(int ticks, boolean internal) {
            return latestSubtask;
        }

        @Override
        @Deprecated
        public void finishTask() {
        }

        public void finishTask(String title) {
            final Task task = Utils.find(tasks, Predicates.<Task>equalTo(new MeasurableTask(title)));
            if (task != null && task instanceof MeasurableTask) {
                ((MeasurableTask) task).finish();
                Main.debug(tr("{0} completed in {1}", title, ((MeasurableTask) task).duration));
                listener.stateChanged(null);
            }
        }

        @Override
        public void invalidate() {
        }

        @Override
        public void setTicksCount(int ticks) {
        }

        @Override
        public int getTicksCount() {
            return 0;
        }

        @Override
        public void setTicks(int ticks) {
        }

        @Override
        public int getTicks() {
            return 0;
        }

        @Override
        public void worked(int ticks) {
        }

        @Override
        public boolean isCanceled() {
            return false;
        }

        @Override
        public void cancel() {
        }

        @Override
        public void addCancelListener(CancelListener listener) {
        }

        @Override
        public void removeCancelListener(CancelListener listener) {
        }

        @Override
        public void appendLogMessage(String message) {
        }

        @Override
        public void setProgressTaskId(ProgressTaskId taskId) {
        }

        @Override
        public ProgressTaskId getProgressTaskId() {
            return null;
        }

        @Override
        public Component getWindowParent() {
            return Main.parent;
        }
    }

    /**
     * Returns the progress monitor.
     * @return The progress monitor
     */
    public SplashProgressMonitor getProgressMonitor() {
        return progressMonitor;
    }

    private static class SplashScreenProgressRenderer extends JPanel {
        private JLabel lblTaskTitle;
        private JProgressBar progressBar;
        private static final String labelHtml = "<html>"
                + "<style>ul {margin-top: 0; margin-bottom: 0; padding: 0;} li {margin: 0; padding: 0;}</style>"
                + "<body height='320'>";

        protected void build() {
            setLayout(new GridBagLayout());
            GridBagConstraints gc = new GridBagConstraints();
            gc.gridx = 0;
            gc.gridy = 0;
            gc.fill = GridBagConstraints.HORIZONTAL;
            gc.weightx = 1.0;
            gc.weighty = 0.0;
            gc.insets = new Insets(5,0,0,0);
            add(lblTaskTitle = new JLabel(labelHtml), gc);

            gc.gridy = 1;
            gc.insets = new Insets(15,0,0,0);
            add(progressBar = new JProgressBar(JProgressBar.HORIZONTAL), gc);
            progressBar.setIndeterminate(true);
        }

        public SplashScreenProgressRenderer() {
            build();
        }

        @Override
        public void paint(Graphics g) {
            try {
                super.paint(g);
            } catch (NullPointerException ignore) {
                // NullPointerException at javax.swing.text.html.StyleSheet$ListPainter.paint
            }
        }

        public void setTasks(String tasks) {
            lblTaskTitle.setText(labelHtml + tasks);
            repaint();
        }
    }
}
