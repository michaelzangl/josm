// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import java.awt.GridBagLayout;
import java.io.IOException;

import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.gui.widgets.UrlLabel;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.crashreport.JosmVersionTester.JosmVersionTesterListener;
import org.openstreetmap.josm.tools.crashreport.JosmVersionTester.UpToDateState;

/**
 * This panel checks the current version of JOSM. If the user is not running the current stable/tested version, the user is prompted to update JOSM to the current version.
 *
 * @author Michael Zangl
 */
public class SuggestJosmUpdatePanel extends JPanel implements JosmVersionTesterListener {

    private JMultilineLabel updateLabel = new JMultilineLabel("");

    public SuggestJosmUpdatePanel() {
        setBorder(BorderFactory.createTitledBorder("Update JOSM"));
        setLayout(new GridBagLayout());
        add(updateLabel, GBC.eol().fill(GBC.HORIZONTAL));
        add(new UrlLabel(Main.getJOSMWebsite()), GBC.eop().insets(8, 0, 0, 0));
        updateText();
    }

    private void updateText() {
        UpToDateState state = JosmVersionTester.getState();
        String labelText;
        switch (state.isUpToDate()) {
        case UP_TO_DATE:
            labelText = "Your josm version is up to date.";
            break;
        case HAVE_UPDATE_LATEST:
        case HAVE_UPDATE_TESTED:
            labelText = "You should update.";
            break;
        case UNKNOWN:
            labelText = "Unknown result.";
            break;
        case PENDING:
            labelText = "Please wait ...";
            break;
        case ERROR:
        default:
            labelText = "An error occured while checking your JOSM version.";
            break;
        }

        updateLabel.setMultilineText(labelText);
    }

    private void showJosmUpdateWebsite() {
        try {
            Main.platform.openUrl(Main.getJOSMWebsite());
        } catch (IOException e) {
            Main.warn("Unable to access JOSM website: " + e.getMessage());
        }
    }

    @Override
    public void josmVersionChanged() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                updateText();
            }
        });
    }

    @Override
    public void addNotify() {
        JosmVersionTester.addListener(this);
        super.addNotify();
    }

    @Override
    public void removeNotify() {
        JosmVersionTester.removeListener(this);
        super.removeNotify();
    }
}
