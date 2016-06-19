// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.io;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.notes.Note;
import org.openstreetmap.josm.gui.layer.NoteLayer;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.xml.sax.SAXException;

/**
 * File importer that reads note dump files (*.osn, .osn.gz and .osn.bz2)
 * @since 7538
 */
public class NoteImporter extends FileImporter {

    private static final ExtensionFileFilter FILE_FILTER = ExtensionFileFilter.newFilterWithArchiveExtensions(
            "osn", "osn", tr("Note Files"), true);

    /** Create an importer for note dump files */
    public NoteImporter() {
        super(FILE_FILTER);
    }

    @Override
    public void importData(final File file, ProgressMonitor progressMonitor) throws IOException {
        if (Main.isDebugEnabled()) {
            Main.debug("importing notes file " + file.getAbsolutePath());
        }
        try (InputStream is = Compression.getUncompressedFileInputStream(file)) {
            final NoteLayer layer = loadLayer(is, file, file.getName(), progressMonitor);
            if (!Main.getLayerManager().containsLayer(layer)) {
                Main.getLayerManager().addLayer(layer);
            }
        } catch (SAXException e) {
            Main.error("error opening up notes file");
            Main.error(e, true);
            throw new IOException(e.getMessage(), e);
        }
    }

    /**
     * Load note layer from InputStream.
     * @param in input stream
     * @param associatedFile filename of data (can be <code>null</code> if the stream does not come from a file)
     * @param layerName name of generated layer
     * @param progressMonitor handler for progress monitoring and canceling
     * @return note layer
     * @throws IOException if any I/O error occurs
     * @throws SAXException if any SAX error occurs
     * @since 9746
     */
    public NoteLayer loadLayer(InputStream in, final File associatedFile, final String layerName, ProgressMonitor progressMonitor)
            throws SAXException, IOException {
        final List<Note> fileNotes = new NoteReader(in).parse();
        List<NoteLayer> noteLayers = null;
        if (Main.map != null) {
            noteLayers = Main.getLayerManager().getLayersOfType(NoteLayer.class);
        }
        final NoteLayer layer;
        if (noteLayers != null && !noteLayers.isEmpty()) {
            layer = noteLayers.get(0);
            layer.getNoteData().addNotes(fileNotes);
        } else {
            layer = new NoteLayer(fileNotes, associatedFile != null ? associatedFile.getName() : tr("Notes"));
        }
        return layer;
    }
}
