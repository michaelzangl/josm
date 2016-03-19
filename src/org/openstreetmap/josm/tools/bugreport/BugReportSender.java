// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.bugreport;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.gui.widgets.UrlLabel;
import org.openstreetmap.josm.tools.Base64;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.openstreetmap.josm.tools.Utils;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

/**
 * This class handles sending the bug report to JOSM website.
 * <p>
 * Currently, we try to open a browser window for the user that displays the bug report.
 *
 * @author Michael Zangl
 */
public class BugReportSender extends Thread {

    private final String statusText;

    /**
     * Creates a new sender.
     * @param statusText The status text to send.
     */
    public BugReportSender(String statusText) {
        super("Bug report sender");
        this.statusText = statusText;
    }

    @Override
    public void run() {
        try {
            // first, send the debug text using post.
            String debugTextPasteId = pasteDebugText();

            // then open a browser to display the pasted text.
            String openBrowserError = OpenBrowser.displayUrl(getJOSMTicketURL() + "?pdata_stored=" + debugTextPasteId);
            if (openBrowserError != null) {
                Main.warn(openBrowserError);
                failed(openBrowserError);
            }
        } catch (BugReportSenderException e) {
            Main.warn(e);
            failed(e.getMessage());
        }
    }

    /**
     * Sends the debug text to the server.
     * @return The token which was returned by the server. We need to pass this on to the ticket system.
     * @throws BugReportSenderException if sending the report failed.
     */
    private String pasteDebugText() throws BugReportSenderException {
        try {
            String text = Utils.strip(statusText);
            String postQuery = "pdata=" + Base64.encode(text, true);
            HttpClient client = HttpClient.create(new URL(getJOSMTicketURL()), "POST")
                    .setHeader("Content-Type", "application/x-www-form-urlencoded")
                    .setRequestBody(postQuery.getBytes(StandardCharsets.UTF_8));

            Response connection = client.connect();

            try (InputStream in = connection.getContent()) {
                DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                Document document = builder.parse(in);
                return retriveDebugToken(document);
            }
        } catch (IOException | SAXException | ParserConfigurationException | XPathExpressionException t) {
            throw new BugReportSenderException(t);
        }
    }

    private String getJOSMTicketURL() {
        return Main.getJOSMWebsite() + "/josmticket";
    }

    private String retriveDebugToken(Document document) throws XPathExpressionException, BugReportSenderException {
        XPathFactory factory = XPathFactory.newInstance();
        XPath xpath = factory.newXPath();
        String status = (String) xpath.compile("/josmticket/@status").evaluate(document, XPathConstants.STRING);
        if (!"ok".equals(status)) {
            String message = (String) xpath.compile("/josmticket/error/text()").evaluate(document,
                    XPathConstants.STRING);
            if (message.isEmpty()) {
                message = "Error in server response but server did not tell us what happened.";
            }
            throw new BugReportSenderException(message);
        }

        String token = (String) xpath.compile("/josmticket/preparedid/text()")
                .evaluate(document, XPathConstants.STRING);
        if (token.isEmpty()) {
            throw new BugReportSenderException("Server did not respond with a prepared id.");
        }
        return token;
    }

    private void failed(String string) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                JPanel errorPanel = new JPanel();
                errorPanel.setLayout(new GridBagLayout());
                errorPanel.add(new JMultilineLabel(
                        tr("Opening the bug report failed. Please report manually using this website:")),
                        GBC.eol().fill(GridBagConstraints.HORIZONTAL));
                errorPanel.add(new UrlLabel(Main.getJOSMWebsite() + "/newticket", 2), GBC.eop().insets(8, 0, 0, 0));
                errorPanel.add(new DebugTextDisplay(statusText));

                JOptionPane.showMessageDialog(Main.parent, errorPanel, tr("You have encountered a bug in JOSM"),
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }

    private static class BugReportSenderException extends Exception {
        BugReportSenderException(String message) {
            super(message);
        }

        BugReportSenderException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * Opens the bug report window on the JOSM server.
     * @param statusText The status text to send along to the server.
     */
    public static void reportBug(String statusText) {
        new BugReportSender(statusText).start();
    }
}
