// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.layer.imagery;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * This class handles text painting on the tile source layer.
 * @author Michael Zangl
 * @since xxx
 */
public class TextPainter {
    private final int tileWidth;
    private int debugY;
    private Graphics2D g;

    public TextPainter(int tileWidth) {
        this.tileWidth = tileWidth;

    }

    /**
     * Reset internal state and start.
     */
    public void start(Graphics2D g) {
        this.g = g;
        debugY = 140;
    }

    public void addDebug(String debugLine) {
        debugY += drawString(debugLine, 50, debugY, 500);
    }

    private void drawTileString(String text, int x, int y) {
        // ??
    }

    /**
     * Add a text overlay for the map.
     * @param text The text to add.
     */
    public void addTextOverlay(String text) {
        drawString(text, 120, 120, 500);
    }

    private int drawString(String text, int x, int y, int width) {
        String textToDraw = text;
        int maxLineWidth = 0;
        if (g.getFontMetrics().stringWidth(text) > width) {
            // text longer than tile size, split it
            StringBuilder line = new StringBuilder();
            StringBuilder ret = new StringBuilder();
            for (String s: text.split(" ")) {
                int lineWidth = g.getFontMetrics().stringWidth(line.toString() + s);
                if (lineWidth > width) {
                    ret.append(line).append('\n');
                    line.setLength(0);
                }
                line.append(s).append(' ');
                maxLineWidth = Math.max(lineWidth, maxLineWidth);
            }
            ret.append(line);
            textToDraw = ret.toString();
        }

        return drawLines(x, y, textToDraw.split("\n"), maxLineWidth);
    }

    private int drawLines(int x, int y, String[] lines, int maxLineWidth) {
        int height = g.getFontMetrics().getHeight();

        // background
        g.setColor(new Color(0, 0, 0, 50));
        g.fillRect(x - 3, y - height - 3, maxLineWidth + 6, (3 + height) * (lines.length - 1) + 6);

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
