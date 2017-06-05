package com.alayouni.ansihighlight;

import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.ui.JBColor;

import java.awt.*;

import static com.alayouni.ansihighlight.ANSIHighlighter.setupAllAttributesUnderline;

/**
 * Created by alayouni on 6/5/17.
 */
class ANSIColor {
    private final TextAttributesKey key;
    private final Color defaultForeground;
    private final Color defaultBackground;

    private static final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    private static final ANSIColor[] ALL_COLORS = {
            new ANSIColor(ConsoleHighlighter.BLACK, JBColor.BLACK, JBColor.BLACK),
            new ANSIColor(ConsoleHighlighter.RED, JBColor.RED, JBColor.RED),
            new ANSIColor(ConsoleHighlighter.GREEN, JBColor.GREEN, JBColor.GREEN),
            new ANSIColor(ConsoleHighlighter.YELLOW, JBColor.YELLOW, JBColor.YELLOW),
            new ANSIColor(ConsoleHighlighter.BLUE, JBColor.BLUE, JBColor.BLUE),
            new ANSIColor(ConsoleHighlighter.MAGENTA, JBColor.MAGENTA, JBColor.MAGENTA),
            new ANSIColor(ConsoleHighlighter.CYAN, JBColor.CYAN, JBColor.CYAN),
            new ANSIColor(ConsoleHighlighter.WHITE, JBColor.WHITE, JBColor.WHITE)
    };

    ANSIColor(TextAttributesKey key, Color defaultForeground, Color defaultBackground) {
        this.key = key;
        this.defaultForeground = defaultForeground;
        this.defaultBackground = defaultBackground;
    }

    Color getForegroundColor() {
        Color cl = colorsScheme.getAttributes(key).getForegroundColor();
        if(cl == null) cl = key.getDefaultAttributes().getForegroundColor();
        if(cl == null) return defaultForeground;
        return cl;
    }

    Color getBackgroundColor() {
        Color cl = colorsScheme.getAttributes(key).getBackgroundColor();
        if(cl == null) cl = key.getDefaultAttributes().getBackgroundColor();
        if(cl == null) return defaultBackground;
        return cl;
    }

    static void setupAllAttributesForeground(TextAttributesOperation[] operations, int id) {
        TextAttributesForegroundOperation foregroundOperation = new TextAttributesForegroundOperation();
        operations[2] = foregroundOperation;
        for(int i = 0; i < ALL_COLORS.length; i++) {
            foregroundOperation.foreground = ALL_COLORS[i].getForegroundColor();
            setupAllAttributesBackground(operations, id | ((i + 1) << 3));
        }
        operations[2] = null;
        setupAllAttributesBackground(operations, id);
    }

    static void setupAllAttributesBackground(TextAttributesOperation[] operations, int id) {
        TextAttributesBackgroundOperation backgroundOperation = new TextAttributesBackgroundOperation();
        operations[3] = backgroundOperation;
        for(int i = 0; i < 8; i++) {
            backgroundOperation.background = ALL_COLORS[i].getBackgroundColor();
            setupAllAttributesUnderline(operations, id | ((i + 1) << 7));
        }
        operations[3] = null;
        setupAllAttributesUnderline(operations, id);
    }

    private static class TextAttributesForegroundOperation implements TextAttributesOperation {
        private Color foreground;

        @Override
        public void apply(TextAttributes attributes) {
            attributes.setForegroundColor(foreground);
        }
    }

    private static class TextAttributesBackgroundOperation implements TextAttributesOperation {
        private Color background;

        @Override
        public void apply(TextAttributes attributes) {
            attributes.setBackgroundColor(background);
        }
    }
}
