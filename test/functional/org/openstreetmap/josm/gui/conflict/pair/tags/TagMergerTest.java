// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.conflict.pair.tags;

import java.awt.BorderLayout;

import javax.swing.JFrame;

import org.junit.Ignore;

@Ignore
public class TagMergerTest extends JFrame {

    private TagMerger tagMerger;

    protected void build() {
        tagMerger = new TagMerger();
        getContentPane().setLayout(new BorderLayout());
        getContentPane().add(tagMerger, BorderLayout.CENTER);
    }

    /**
     * Constructs a new {@code TagMergerTest}.
     */
    public TagMergerTest() {
        build();
        tagMerger.getModel().addItem(new TagMergeItem("key", "myvalue", "theirvalue"));
        tagMerger.getModel().addItem(new TagMergeItem("key", "myvalue", null));
        tagMerger.getModel().addItem(new TagMergeItem("key", null, "theirvalue"));
        tagMerger.getModel().addItem(new TagMergeItem("a very long key asdfasdf asdfasdf", "a very long value asdfasdf",
                "a very long value asdfasdf"));
        for (int i = 0; i < 50; i++) {
          tagMerger.getModel().addItem(new TagMergeItem("key", "myvalue", "theirvalue"));
        }
    }

    public static void main(String args[]) {
        TagMergerTest test  = new TagMergerTest();
        test.setSize(600, 600);
        test.setVisible(true);
    }
}
