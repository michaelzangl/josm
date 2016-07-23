// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io.imagery;

import java.awt.HeadlessException;
import java.io.IOException;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.imageio.ImageIO;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.ParserConfigurationException;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.imagery.ImageryInfo;
import org.openstreetmap.josm.data.projection.Projections;
import org.openstreetmap.josm.tools.HttpClient;
import org.openstreetmap.josm.tools.Utils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.EntityResolver;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * This class represents the capabilites of a WMS imagery server.
 */
public class WMSImagery {

    private static final class ChildIterator implements Iterator<Element> {
        private Element child;

        ChildIterator(Element parent) {
            child = advanceToElement(parent.getFirstChild());
        }

        private static Element advanceToElement(Node firstChild) {
            Node node = firstChild;
            while (node != null && !(node instanceof Element)) {
                node = node.getNextSibling();
            }
            return (Element) node;
        }

        @Override
        public boolean hasNext() {
            return child != null;
        }

        @Override
        public Element next() {
            if (!hasNext()) {
                throw new NoSuchElementException("No next sibling.");
            }
            Element next = child;
            child = advanceToElement(child.getNextSibling());
            return next;
        }
    }

    /**
     * An exception that is thrown if there was an error while getting the capabilities of the WMS server.
     */
    public static class WMSGetCapabilitiesException extends Exception {
        private final String incomingData;

        /**
         * Constructs a new {@code WMSGetCapabilitiesException}
         * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method)
         * @param incomingData the answer from WMS server
         */
        public WMSGetCapabilitiesException(Throwable cause, String incomingData) {
            super(cause);
            this.incomingData = incomingData;
        }

        /**
         * Constructs a new {@code WMSGetCapabilitiesException}
         * @param message   the detail message. The detail message is saved for later retrieval by the {@link #getMessage()} method
         * @param incomingData the answer from the server
         * @since 10520
         */
        public WMSGetCapabilitiesException(String message, String incomingData) {
            super(message);
            this.incomingData = incomingData;
        }

        /**
         * The data that caused this exception.
         * @return The server response to the capabilites request.
         */
        public String getIncomingData() {
            return incomingData;
        }
    }

    private List<LayerDetails> layers;
    private URL serviceUrl;
    private List<String> formats;

    /**
     * Returns the list of layers.
     * @return the list of layers
     */
    public List<LayerDetails> getLayers() {
        return Collections.unmodifiableList(layers);
    }

    /**
     * Returns the service URL.
     * @return the service URL
     */
    public URL getServiceUrl() {
        return serviceUrl;
    }

    /**
     * Returns the list of supported formats.
     * @return the list of supported formats
     */
    public List<String> getFormats() {
        return Collections.unmodifiableList(formats);
    }

    /**
     * Gets the preffered format for this imagery layer.
     * @return The preffered format as mime type.
     */
    public String getPreferredFormats() {
        if (formats.contains("image/jpeg")) {
            return "image/jpeg";
        } else if (formats.contains("image/png")) {
            return "image/png";
        } else if (formats.isEmpty()) {
            return null;
        } else {
            return formats.get(0);
        }
    }

    String buildRootUrl() {
        if (serviceUrl == null) {
            return null;
        }
        StringBuilder a = new StringBuilder(serviceUrl.getProtocol());
        a.append("://").append(serviceUrl.getHost());
        if (serviceUrl.getPort() != -1) {
            a.append(':').append(serviceUrl.getPort());
        }
        a.append(serviceUrl.getPath()).append('?');
        if (serviceUrl.getQuery() != null) {
            a.append(serviceUrl.getQuery());
            if (!serviceUrl.getQuery().isEmpty() && !serviceUrl.getQuery().endsWith("&")) {
                a.append('&');
            }
        }
        return a.toString();
    }

    public String buildGetMapUrl(Collection<LayerDetails> selectedLayers) {
        return buildGetMapUrl(selectedLayers, "image/jpeg");
    }

    public String buildGetMapUrl(Collection<LayerDetails> selectedLayers, String format) {
        return buildRootUrl() + "FORMAT=" + format + (imageFormatHasTransparency(format) ? "&TRANSPARENT=TRUE" : "")
                + "&VERSION=1.1.1&SERVICE=WMS&REQUEST=GetMap&LAYERS="
                + Utils.join(",", Utils.transform(selectedLayers, x -> x.ident))
                + "&STYLES=&SRS={proj}&WIDTH={width}&HEIGHT={height}&BBOX={bbox}";
    }

    public void attemptGetCapabilities(String serviceUrlStr) throws IOException, WMSGetCapabilitiesException {
        URL getCapabilitiesUrl = null;
        try {
            if (!Pattern.compile(".*GetCapabilities.*", Pattern.CASE_INSENSITIVE).matcher(serviceUrlStr).matches()) {
                // If the url doesn't already have GetCapabilities, add it in
                getCapabilitiesUrl = new URL(serviceUrlStr);
                final String getCapabilitiesQuery = "VERSION=1.1.1&SERVICE=WMS&REQUEST=GetCapabilities";
                if (getCapabilitiesUrl.getQuery() == null) {
                    getCapabilitiesUrl = new URL(serviceUrlStr + '?' + getCapabilitiesQuery);
                } else if (!getCapabilitiesUrl.getQuery().isEmpty() && !getCapabilitiesUrl.getQuery().endsWith("&")) {
                    getCapabilitiesUrl = new URL(serviceUrlStr + '&' + getCapabilitiesQuery);
                } else {
                    getCapabilitiesUrl = new URL(serviceUrlStr + getCapabilitiesQuery);
                }
            } else {
                // Otherwise assume it's a good URL and let the subsequent error
                // handling systems deal with problems
                getCapabilitiesUrl = new URL(serviceUrlStr);
            }
            serviceUrl = new URL(serviceUrlStr);
        } catch (HeadlessException e) {
            Main.warn(e);
            return;
        }

        Main.info("GET " + getCapabilitiesUrl);
        final String incomingData = HttpClient.create(getCapabilitiesUrl).connect().fetchContent();
        Main.debug("Server response to Capabilities request:");
        Main.debug(incomingData);

        try {
            DocumentBuilder builder = Utils.newSafeDOMBuilder();
            builder.setEntityResolver(new EntityResolver() {
                @Override
                public InputSource resolveEntity(String publicId, String systemId) throws SAXException, IOException {
                    Main.info("Ignoring DTD " + publicId + ", " + systemId);
                    return new InputSource(new StringReader(""));
                }
            });
            Document document = builder.parse(new InputSource(new StringReader(incomingData)));
            Element root = document.getDocumentElement();

            // Check if the request resulted in ServiceException
            if ("ServiceException".equals(root.getTagName())) {
                throw new WMSGetCapabilitiesException(root.getTextContent(), incomingData);
            }

            // Some WMS service URLs specify a different base URL for their GetMap service
            Element child = getChild(root, "Capability");
            child = getChild(child, "Request");
            child = getChild(child, "GetMap");

            formats = getChildrenStream(child, "Format")
                    .map(x -> x.getTextContent())
                    .filter(WMSImagery::isImageFormatSupportedWarn)
                    .collect(Collectors.toList());

            child = getChild(child, "DCPType");
            child = getChild(child, "HTTP");
            child = getChild(child, "Get");
            child = getChild(child, "OnlineResource");
            if (child != null) {
                String baseURL = child.getAttribute("xlink:href");
                if (baseURL != null && !baseURL.equals(serviceUrlStr)) {
                    Main.info("GetCapabilities specifies a different service URL: " + baseURL);
                    serviceUrl = new URL(baseURL);
                }
            }

            Element capabilityElem = getChild(root, "Capability");
            List<Element> children = getChildren(capabilityElem, "Layer");
            layers = parseLayers(children, new HashSet<String>());
        } catch (MalformedURLException | ParserConfigurationException | SAXException e) {
            throw new WMSGetCapabilitiesException(e, incomingData);
        }
    }

    private static boolean isImageFormatSupportedWarn(String format) {
        boolean isFormatSupported = isImageFormatSupported(format);
        if (!isFormatSupported) {
            Main.info("Skipping unsupported image format {0}", format);
        }
        return isFormatSupported;
    }

    static boolean isImageFormatSupported(final String format) {
        return ImageIO.getImageReadersByMIMEType(format).hasNext()
                // handles image/tiff image/tiff8 image/geotiff image/geotiff8
                || (format.startsWith("image/tiff") || format.startsWith("image/geotiff"))
                        && ImageIO.getImageReadersBySuffix("tiff").hasNext()
                || format.startsWith("image/png") && ImageIO.getImageReadersBySuffix("png").hasNext()
                || format.startsWith("image/svg") && ImageIO.getImageReadersBySuffix("svg").hasNext()
                || format.startsWith("image/bmp") && ImageIO.getImageReadersBySuffix("bmp").hasNext();
    }

    static boolean imageFormatHasTransparency(final String format) {
        return format != null && (format.startsWith("image/png") || format.startsWith("image/gif")
                || format.startsWith("image/svg") || format.startsWith("image/tiff"));
    }

    public ImageryInfo toImageryInfo(String name, Collection<LayerDetails> selectedLayers) {
        ImageryInfo i = new ImageryInfo(name, buildGetMapUrl(selectedLayers));
        if (selectedLayers != null) {
            Set<String> proj = new HashSet<>();
            for (WMSImagery.LayerDetails l : selectedLayers) {
                proj.addAll(l.getProjections());
            }
            i.setServerProjections(proj);
        }
        return i;
    }

    private List<LayerDetails> parseLayers(List<Element> children, Set<String> parentCrs) {
        List<LayerDetails> details = new ArrayList<>(children.size());
        for (Element element : children) {
            details.add(parseLayer(element, parentCrs));
        }
        return details;
    }

    private LayerDetails parseLayer(Element element, Set<String> parentCrs) {
        String name = getChildContent(element, "Title", null, null);
        String ident = getChildContent(element, "Name", null, null);

        // The set of supported CRS/SRS for this layer
        Set<String> crsList = new HashSet<>();
        // ...including this layer's already-parsed parent projections
        crsList.addAll(parentCrs);

        // Parse the CRS/SRS pulled out of this layer's XML element
        // I think CRS and SRS are the same at this point
        getChildrenStream(element)
            .filter(child -> "CRS".equals(child.getNodeName()) || "SRS".equals(child.getNodeName()))
            .map(child -> (String) getContent(child))
            .filter(crs -> !crs.isEmpty())
            .map(crs -> crs.trim().toUpperCase(Locale.ENGLISH))
            .forEach(crsList::add);

        // Check to see if any of the specified projections are supported by JOSM
        boolean josmSupportsThisLayer = false;
        for (String crs : crsList) {
            josmSupportsThisLayer |= isProjSupported(crs);
        }

        Bounds bounds = null;
        Element bboxElem = getChild(element, "EX_GeographicBoundingBox");
        if (bboxElem != null) {
            // Attempt to use EX_GeographicBoundingBox for bounding box
            double left = Double.parseDouble(getChildContent(bboxElem, "westBoundLongitude", null, null));
            double top = Double.parseDouble(getChildContent(bboxElem, "northBoundLatitude", null, null));
            double right = Double.parseDouble(getChildContent(bboxElem, "eastBoundLongitude", null, null));
            double bot = Double.parseDouble(getChildContent(bboxElem, "southBoundLatitude", null, null));
            bounds = new Bounds(bot, left, top, right);
        } else {
            // If that's not available, try LatLonBoundingBox
            bboxElem = getChild(element, "LatLonBoundingBox");
            if (bboxElem != null) {
                double left = Double.parseDouble(bboxElem.getAttribute("minx"));
                double top = Double.parseDouble(bboxElem.getAttribute("maxy"));
                double right = Double.parseDouble(bboxElem.getAttribute("maxx"));
                double bot = Double.parseDouble(bboxElem.getAttribute("miny"));
                bounds = new Bounds(bot, left, top, right);
            }
        }

        List<Element> layerChildren = getChildren(element, "Layer");
        List<LayerDetails> childLayers = parseLayers(layerChildren, crsList);

        return new LayerDetails(name, ident, crsList, josmSupportsThisLayer, bounds, childLayers);
    }

    private static boolean isProjSupported(String crs) {
        return Projections.getProjectionByCode(crs) != null;
    }

    private static String getChildContent(Element parent, String name, String missing, String empty) {
        Element child = getChild(parent, name);
        if (child == null)
            return missing;
        else {
            String content = (String) getContent(child);
            return (!content.isEmpty()) ? content : empty;
        }
    }

    private static Object getContent(Element element) {
        NodeList nl = element.getChildNodes();
        StringBuilder content = new StringBuilder();
        for (int i = 0; i < nl.getLength(); i++) {
            Node node = nl.item(i);
            switch (node.getNodeType()) {
                case Node.ELEMENT_NODE:
                    return node;
                case Node.CDATA_SECTION_NODE:
                case Node.TEXT_NODE:
                    content.append(node.getNodeValue());
                    break;
                default: // Do nothing
            }
        }
        return content.toString().trim();
    }

    private static Stream<Element> getChildrenStream(Element parent) {
        if (parent == null) {
            // ignore missing elements
            return Stream.empty();
        } else {
            Iterable<Element> it = () -> new ChildIterator(parent);
            return StreamSupport.stream(it.spliterator(), false);
        }
    }

    private static Stream<Element> getChildrenStream(Element parent, String name) {
        return getChildrenStream(parent).filter(child -> name.equals(child.getNodeName()));
    }

    private static List<Element> getChildren(Element parent, String name) {
        return getChildrenStream(parent, name).collect(Collectors.toList());
    }

    private static Element getChild(Element parent, String name) {
        return getChildrenStream(parent, name).findFirst().orElse(null);
    }

    /**
     * The details of a layer of this wms server.
     */
    public static class LayerDetails {

        /**
         * The layer name
         */
        public final String name;
        public final String ident;
        /**
         * The child layers of this layer
         */
        public final List<LayerDetails> children;
        /**
         * The bounds this layer can be used for
         */
        public final Bounds bounds;
        public final Set<String> crsList;
        public final boolean supported;

        public LayerDetails(String name, String ident, Set<String> crsList, boolean supportedLayer, Bounds bounds,
                List<LayerDetails> childLayers) {
            this.name = name;
            this.ident = ident;
            this.supported = supportedLayer;
            this.children = childLayers;
            this.bounds = bounds;
            this.crsList = crsList;
        }

        public boolean isSupported() {
            return this.supported;
        }

        public Set<String> getProjections() {
            return crsList;
        }

        @Override
        public String toString() {
            if (this.name == null || this.name.isEmpty())
                return this.ident;
            else
                return this.name;
        }
    }
}
