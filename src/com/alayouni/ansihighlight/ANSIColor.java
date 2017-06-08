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

    //______________________pre-calculations to accelerate parsing___________________________________

    private static final int FOREGROUND_START_CODE = 30;
    private static final int FOREGROUND_END_CODE = 37;

    private static final int BACKGROUND_START_CODE = 40;
    private static final int BACKGROUND_END_CODE = 47;

    private static ANSIColor[] ALL_COLORS = new ANSIColor[8];

    static EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();

    static void initAllANSIColors() {
        ALL_COLORS[0] = new ANSIColor(ConsoleHighlighter.BLACK, JBColor.BLACK, JBColor.BLACK);
        ALL_COLORS[1] = new ANSIColor(ConsoleHighlighter.RED, JBColor.RED, JBColor.RED);
        ALL_COLORS[2] = new ANSIColor(ConsoleHighlighter.GREEN, JBColor.GREEN, JBColor.GREEN);
        ALL_COLORS[3] = new ANSIColor(ConsoleHighlighter.YELLOW, JBColor.YELLOW, JBColor.YELLOW);
        ALL_COLORS[4] = new ANSIColor(ConsoleHighlighter.BLUE, JBColor.BLUE, JBColor.BLUE);
        ALL_COLORS[5] = new ANSIColor(ConsoleHighlighter.MAGENTA, JBColor.MAGENTA, JBColor.MAGENTA);
        ALL_COLORS[6] = new ANSIColor(ConsoleHighlighter.CYAN, JBColor.CYAN, JBColor.CYAN);
        ALL_COLORS[7] = new ANSIColor(ConsoleHighlighter.WHITE, JBColor.WHITE, JBColor.WHITE);
    }

    static void setupColorsEncoders(ANSITextAttributesIDEncoder[] encoders) {
        //see ANSIHighlighter.ALL_TEXT_ATTRIBUTES and ANSIHighlighter.ENCODER
        int resetMask = 0xFFFFFF87;
        for(int colorCode = FOREGROUND_START_CODE; colorCode <= FOREGROUND_END_CODE; colorCode ++) {
            encoders[colorCode] = new ANSITextAttributesIDEncoder(resetMask, (colorCode - FOREGROUND_START_CODE + 1) << 3);
        }
//        encoders[FOREGROUND_END_CODE + 1] = new ANSITextAttributesIDEncoder(resetMask, 0);//no foreground

        //see ANSIHighlighter.ALL_TEXT_ATTRIBUTES and ANSIHighlighter.ENCODER
        resetMask = 0xFFFFF87F;
        for(int colorCode = BACKGROUND_START_CODE; colorCode <= BACKGROUND_END_CODE; colorCode ++) {
            encoders[colorCode] = new ANSITextAttributesIDEncoder(resetMask, (colorCode - BACKGROUND_START_CODE + 1) << 7);
        }
//        encoders[BACKGROUND_END_CODE + 1] = new ANSITextAttributesIDEncoder(resetMask, 0);//no background

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
