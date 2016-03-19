// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.tools.crashreport;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.openstreetmap.josm.actions.JosmAction;
import org.openstreetmap.josm.actions.ReportBugAction;
import org.openstreetmap.josm.gui.widgets.JMultilineLabel;
import org.openstreetmap.josm.tools.Base64;
import org.openstreetmap.josm.tools.GBC;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.HttpClient.Response;
import org.openstreetmap.josm.tools.OpenBrowser;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.sun.org.apache.xml.internal.serialize.OutputFormat;
import com.sun.org.apache.xml.internal.serialize.XMLSerializer;

/**
 * This panel displays allows the user to send in a crash report.
 * <p>
 * It displays some help on how to send in the crash report and a Button to open the web browser.
 * <p>
 * For experts, there is an expand button that allows you to see the stack trace.
 *
 * @author Michael Zangl
 */
public class SuggestCrashReportPanel extends JPanel {
    private final CrashReportData data;

    public SuggestCrashReportPanel(CrashReportData data) {
        this.data = data;
        setBorder(BorderFactory.createTitledBorder("Send crash report."));
        setLayout(new GridBagLayout());
        JMultilineLabel comp = new JMultilineLabel(
                "You can help us fix the issue by reporting the error. We need the following information and a step by step list of how you produced the error for this.");
        add(comp, GBC.eop().fill(GBC.HORIZONTAL));
        add(Box.createRigidArea(new Dimension(0, 10)));

        // Debug report
        JScrollPane debugText = new JScrollPane(new JTextArea(getDebugText()));
        debugText.setPreferredSize(new Dimension(500, 100));
        add(debugText, GBC.eop().fill(GBC.BOTH));
        add(Box.createRigidArea(new Dimension(0, 10)));

        // Buttons
        JButton sendButton = new JButton();
        sendButton.setAction(new JosmAction(tr("Report bug"), "bug", tr("Report a ticket to JOSM bugtracker"), null,
                false) {
            @Override
            public void actionPerformed(ActionEvent e) {
                startSending();
            }
        });

        add(sendButton, GBC.std().anchor(GBC.LINE_END));
    }

    private String getDebugText() {
        // Do not translate. We send this to Trac.
        StringWriter debugString = new StringWriter();
        PrintWriter out = new PrintWriter(debugString);
        data.writeTo(out);

        return debugString.getBuffer().toString();
    }

    protected void startSending() {
        new BugReportSender().start();
    }

    private static class BugReportSenderException extends Exception {

        public BugReportSenderException(String message, Throwable cause) {
            super(message, cause);
        }

        public BugReportSenderException(String message) {
            super(message);
        }

        public BugReportSenderException(Throwable cause) {
            super(cause);
        }
    }

    private class BugReportSender extends Thread {

        public BugReportSender() {
            super("Bug report sender");
        }

        @Override
        public void run() {
            try {
                // first, send the debug text using post.
                String debugTextPaste = pasteDebugText();

                // Now direct the user to the ticket site.
                ReportBugAction.reportBug(debugTextPaste);

                System.out.println("http://localhost:8000/trac/josmticket?pdata_stored=" + debugTextPaste);
                OpenBrowser.displayUrl("http://localhost:8000/trac/josmticket?pdata_stored=" + debugTextPaste);
            } catch (BugReportSenderException e) {
                e.printStackTrace();
                failed(e.getMessage());
            }
        }

        /**
         * Sends the debug text to the server.
         * @return The token which was returned by the server. We need to pass this on to the ticket system.
         * @throws BugReportSenderException
         */
        private String pasteDebugText() throws BugReportSenderException {
            try {
                String text = getDebugText();
                String postQuery = "pdata=" + Base64.encode(text, true);
                HttpClient client = HttpClient.create(new URL("http://localhost:8000/trac/josmticket"), "POST")
                        .setHeader("Content-Type", "application/x-www-form-urlencoded")
                        .setRequestBody(postQuery.getBytes(StandardCharsets.UTF_8));

                Response connection = client.connect();

                try (InputStream in = connection.getContent()) {
                    Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(in);
                    OutputFormat format = new OutputFormat(document);
                    format.setIndenting(true);
                    XMLSerializer serializer = new XMLSerializer(System.out, format);
                    serializer.serialize(document);
                    return retriveDebugToken(document);
                }
            } catch (IOException | SAXException | ParserConfigurationException | XPathExpressionException t) {
                throw new BugReportSenderException(t);
            }
        }

        private String retriveDebugToken(Document document) throws XPathExpressionException, BugReportSenderException {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xpath = factory.newXPath();
            String status = (String) xpath.compile("/josmticket/@status").evaluate(document, XPathConstants.STRING);
            if (!"ok".equals(status)) {
                String message = (String) xpath.compile("/josmticket/error/text()").evaluate(document, XPathConstants.STRING);
                if (message.isEmpty()) {
                    message = "Error in server response but server did not tell us what happened.";
                }
                throw new BugReportSenderException(message);
            }

            String token = (String) xpath.compile("/josmticket/preparedid/text()").evaluate(document, XPathConstants.STRING);
            if (token.isEmpty()) {
                throw new BugReportSenderException("Server did not respond with a prepared id.");
            }
            return token;
        }

        private void failed(String string) {
            // TODO Notify user that automated reporting failed.
        }
    }
}
