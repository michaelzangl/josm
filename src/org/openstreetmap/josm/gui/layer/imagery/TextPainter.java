// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;

/**
 * This class handles text painting on the tile source layer.
 * @author Michael Zangl
 * @since xxx
 */
public class TextPainter {
    private Graphics2D g;
    private int debugY;
    private int overlayY;

    /**
     * Reset internal state and start.
     * @param g The graphics to paint on
     */
    public void start(Graphics2D g) {
        this.g = g;
        debugY = 160;
        overlayY = 100;
    }

    /**
     * Add a debug string
     * @param debugLine The debug line
     */
    public void addDebug(String debugLine) {
        debugY += drawString(debugLine, 50, debugY, 500);
    }

    /**
     * Draw a string onto a given tile.
     * @param text The text to draw
     * @param tile The tile to paint on
     * @param converter A converter to convert the tile to screen coordinates.
     */
    public void drawTileString(String text, TilePosition tile, TileCoordinateConverter converter) {
        AffineTransform transform = converter.getTransformForTile(tile, 0, 0, 0, .5, 1, .5);
        transform.scale(1.0 / 200, 1.0 / 200);
        AffineTransform oldTransform = g.getTransform();
        g.transform(transform);
        drawString(text, 10, 10, 180);
        g.setTransform(oldTransform);
    }

    /**
     * Add a text overlay for the map.
     * @param text The text to add.
     */
    public void addTextOverlay(String text) {
        overlayY += drawString(text, 120, overlayY, 500);
    }

    private int drawString(String text, int x, int y, int width) {
        String textToDraw = text;
        int maxLineWidth = 0;
        int wholeLineWidth = g.getFontMetrics().stringWidth(text);
        if (wholeLineWidth > width) {
            // text longer than tile size, split it
            StringBuilder line = new StringBuilder();
            StringBuilder ret = new StringBuilder();
            for (String s: text.split(" ")) {
                int lineWidth = g.getFontMetrics().stringWidth(line.toString() + s);
                if (lineWidth > width) {
                    ret.append(line).append('\n');
                    line.setLength(0);
                    lineWidth = g.getFontMetrics().stringWidth(s);
                }
                line.append(s).append(' ');
                maxLineWidth = Math.max(lineWidth, maxLineWidth);
            }
            ret.append(line);
            textToDraw = ret.toString();
        } else {
            maxLineWidth = wholeLineWidth;
        }

        return drawLines(x, y, textToDraw.split("\n"), maxLineWidth);
    }

    private int drawLines(int x, int y, String[] lines, int maxLineWidth) {
        int height = g.getFontMetrics().getHeight();

        // background
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x - 3, y - height - 1, maxLineWidth + 6, (3 + height) * lines.length + 2);

        int offset = 0;
        for (String s: lines) {
            // shadow
            g.setColor(Color.black);
            g.drawString(s, x + 1, y + offset + 1);
            g.setColor(Color.lightGray);
            g.drawString(s, x, y + offset);
            offset += height + 3;
        }
        return offset;
    }

}
