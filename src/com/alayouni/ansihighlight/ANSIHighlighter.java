package com.alayouni.ansihighlight;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by alayouni on 5/12/17.
 */
public class ANSIHighlighter {
    private static final String ESC = "\u001B[";
    private static final char SEQ_END = 'm';
    private static final char SEQ_DELIM = ';';

    private static final int RESET = 0;

    private static final int BOLD = 1;
    private static final int ITALIC = 3;
    private static final int UNDERLINE = 4;

    private static final int FOREGROUND_START_CODE = 30;
    private static final int FOREGROUND_END_CODE = 37;

    private static final int BACKGROUND_START_CODE = 40;
    private static final int BACKGROUND_END_CODE = 47;

    private static final TextAttributes[] ALL_ATTRIBUTES = new TextAttributes[1096];
    private static final TextAttributesEncoder[] ENCODER = new TextAttributesEncoder[49];
    private static final EditorColorsScheme colorsScheme = EditorColorsManager.getInstance().getGlobalScheme();
    private static final ColorKey[] ALL_COLORS = {
            new ColorKey(ConsoleHighlighter.BLACK, Color.BLACK, Color.BLACK),
            new ColorKey(ConsoleHighlighter.RED, Color.RED, Color.RED),
            new ColorKey(ConsoleHighlighter.GREEN, Color.GREEN, Color.GREEN),
            new ColorKey(ConsoleHighlighter.YELLOW, Color.YELLOW, Color.YELLOW),
            new ColorKey(ConsoleHighlighter.BLUE, Color.BLUE, Color.BLUE),
            new ColorKey(ConsoleHighlighter.MAGENTA, Color.MAGENTA, Color.MAGENTA),
            new ColorKey(ConsoleHighlighter.CYAN, Color.CYAN, Color.CYAN),
            new ColorKey(ConsoleHighlighter.WHITE, Color.WHITE, Color.WHITE)
    };

    static {
        ENCODER[RESET] = new TextAttributesEncoder(0, 0);
        ENCODER[BOLD] = new TextAttributesEncoder(0xFFFFFFFE, 1);
        ENCODER[ITALIC] = new TextAttributesEncoder(0xFFFFFFFD, 2);
        ENCODER[UNDERLINE] = new TextAttributesEncoder(0xFFFFFFFB, 4);

        int resetMask = 0xFFFFFF87;
        for(int colorCode = FOREGROUND_START_CODE; colorCode <= FOREGROUND_END_CODE; colorCode ++) {
            ENCODER[colorCode] = new TextAttributesEncoder(resetMask, (colorCode - FOREGROUND_START_CODE + 1) << 3);
        }
        ENCODER[FOREGROUND_END_CODE + 1] = new TextAttributesEncoder(resetMask, 0);//no foreground

        resetMask = 0xFFFFF87F;
        for(int colorCode = BACKGROUND_START_CODE; colorCode <= BACKGROUND_END_CODE; colorCode ++) {
            ENCODER[colorCode] = new TextAttributesEncoder(resetMask, (colorCode - BACKGROUND_START_CODE + 1) << 7);
        }
        ENCODER[BACKGROUND_END_CODE + 1] = new TextAttributesEncoder(resetMask, 0);//no background
    }

    private final HighlightManager highlighter;

    private final Project project;

    public ANSIHighlighter(Project project) {
        this.project = project;
        this.highlighter = HighlightManager.getInstance(project);
        preloadAllTextAttributes();
    }

    public void highlightANSISequences(Editor editor) {

//        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Highlighting ANSI Sequences...", false) {
//            @Override
//            public void run(@NotNull ProgressIndicator indicator) {
//                List<Range> foldRegions = highlightANSISequences(editor, indicator);
//                if(foldRegions == null || foldRegions.isEmpty()) return;
//                applyFoldRegions(editor, foldRegions, indicator);
//            }
//        });
        List<Range> foldRegions = highlightANSISequences(editor, null);
        if(foldRegions == null || foldRegions.isEmpty()) return;
        applyFoldRegions(editor, foldRegions, null);
    }

    private List<Range> highlightANSISequences(Editor editor, ProgressIndicator indicator) {
        String text = editor.getDocument().getText();
        MarkupModel markup = editor.getMarkupModel();
        int escIndex = text.indexOf(ESC),
                startIndex = 0;
        if(escIndex == -1) return null;

        int progress = 0, newProgress;
        if(indicator != null) indicator.setFraction(progress);
        double tot = text.length();

        TextAttributesRange res = new TextAttributesRange();
        res.start = escIndex;
        res.end = -1;
        TextAttributes attr0 = null;

        java.util.List<Range> foldRegions = new ArrayList<>();
        while (res.start >= 0) {
            if(res.end != -1) attr0 = ALL_ATTRIBUTES[res.id];
            res.id = 0;
            extractTextAttributesFromANSIEscapeSequence(text, res);
            if(res.end == -1) {
                res.start = text.indexOf(ESC, res.start + 1);
            } else {
                if(attr0 != null) {
                    //only addOccurrenceHighlight() among all other highlight methods gives the option to turn off highlight hiding when user presses escape (through 0 mask)
                    markup.addRangeHighlighter(startIndex, res.start, HighlighterLayer.ADDITIONAL_SYNTAX, attr0, HighlighterTargetArea.EXACT_RANGE);
//                    highlighter.addOccurrenceHighlight(editor, startIndex, res.start, attr0, 0,null, null);
                }
                foldRegions.add(new Range(res.start, res.end));

                startIndex = res.end;
                if(startIndex == text.length()) break;
                attr0 = res.id == 0 ? null : ALL_ATTRIBUTES[res.id];
                res.start = text.indexOf(ESC, startIndex);
            }
            if(res.start == -1 && attr0 != null) {
                //only addOccurrenceHighlight() among all other highlight methods gives the option to turn off highlight hiding when user presses escape (through 0 mask)
                markup.addRangeHighlighter(startIndex, text.length(), HighlighterLayer.ADDITIONAL_SYNTAX, attr0, HighlighterTargetArea.EXACT_RANGE);
//                highlighter.addOccurrenceHighlight(editor, startIndex, text.length(), attr0, 0, null, null);
            }
            if(indicator != null && res.start > 0) {
                newProgress = (int) Math.round(res.start / tot);
                if(newProgress != progress) indicator.setFraction(progress = newProgress);
            }
        }
        return foldRegions;
    }

    private void applyFoldRegions(Editor editor, List<Range> foldRegions, ProgressIndicator indicator) {
        FoldingModel folder = editor.getFoldingModel();
        folder.runBatchFoldingOperation(() -> {
            int progress = 0, newProgress;
            double total = foldRegions.size();
            if(indicator != null) indicator.setFraction(progress);
            int i = 0;
            for(Range range : foldRegions) {
                folder.addFoldRegion(range.start, range.end, "").setExpanded(false);
                i ++;
                newProgress = (int) Math.round(i / total);
                if(indicator != null && newProgress != progress) indicator.setFraction(progress = newProgress);
            }
        }, true);
    }



    private void extractTextAttributesFromANSIEscapeSequence(String text, TextAttributesRange res) {
        int endIndex = -1, index, startIndex, escIndex = res.start;
        TextAttributesEncoder encoder;
        char c;
        res.id = 0;
        root: while (escIndex < text.length() && text.startsWith(ESC, escIndex)) {
            startIndex = index = escIndex + ESC.length();
            do {
                while (index < text.length() && isDigit(text.charAt(index))) index++;
                if (index == text.length()) break root;
                c = text.charAt(index);
                if (c == SEQ_DELIM || c == SEQ_END) {
                    encoder = encoder(text.substring(startIndex, index));
                    if (encoder == null) break root;
                    res.id = encoder.encode(res.id);
                    startIndex = ++index;
                    if (c == SEQ_END) {
                        endIndex = escIndex = index;
                    }
                } else break root;
            } while(c == SEQ_DELIM);
        }
        if(endIndex == -1) res.end = -1;
        else res.end = endIndex;
    }

    private TextAttributesEncoder encoder(String codeStr) {
        try {
            int code = Integer.parseInt(codeStr);
            if(code >= ENCODER.length || code < 0) return null;
            return ENCODER[code];
        } catch (Throwable e) {
            return null;
        }
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }


    private static class Range {
        int start, end;

        public Range(int start, int end) {
            this.start = start;
            this.end = end;
        }

        public Range(){}
    }

    private static class TextAttributesRange  extends Range {
        private int id = 0;
    }

    private static class TextAttributesEncoder {
        private final int resetMask;
        private final int mask;

        public TextAttributesEncoder(int resetMask, int mask) {
            this.resetMask = resetMask;
            this.mask = mask;
        }

        public int encode(int id) {
            return (id & resetMask) | mask;
        }
    }

    private static void preloadAllTextAttributes() {
        TextAttributesOperation[] operations = new TextAttributesOperation[5];
        operations[0] = null;
        setupAllAttributesItalic(operations, 0);
        operations[0] = (attributes) -> attributes.setFontType(attributes.getFontType() | Font.BOLD);
        setupAllAttributesItalic(operations, 1);
    }

    private static void setupAllAttributesItalic(TextAttributesOperation[] operations, int id) {
        operations[1] = null;
        setupAllAttributesForeground(operations, id);
        operations[1] = (attributes) -> attributes.setFontType(attributes.getFontType() | Font.ITALIC);
        setupAllAttributesForeground(operations, id | 2);
    }

    private static void setupAllAttributesForeground(TextAttributesOperation[] operations, int id) {
        TextAttributesForegroundOperation foregroundOperation = new TextAttributesForegroundOperation();
        operations[2] = foregroundOperation;
        for(int i = 0; i < ALL_COLORS.length; i++) {
            foregroundOperation.foreground = ALL_COLORS[i].getForegroundColor();
            setupAllAttributesBackground(operations, id | ((i + 1) << 3));
        }
        operations[2] = null;
        setupAllAttributesBackground(operations, id);
    }

    private static void setupAllAttributesBackground(TextAttributesOperation[] operations, int id) {
        TextAttributesBackgroundOperation backgroundOperation = new TextAttributesBackgroundOperation();
        operations[3] = backgroundOperation;
        for(int i = 0; i < 8; i++) {
            backgroundOperation.background = ALL_COLORS[i].getBackgroundColor();
            setupAllAttributesUnderline(operations, id | ((i + 1) << 7));
        }
        operations[3] = null;
        setupAllAttributesUnderline(operations, id);
    }

    private static void setupAllAttributesUnderline(TextAttributesOperation[] operations, int id) {
        //must be the last operation to apply
        operations[4] = null;
        setupAttributes(operations, id);
        operations[4] = (attributes) -> {
            boolean isBold = (attributes.getFontType() & Font.BOLD) == Font.BOLD;
            attributes.setEffectType(isBold ? EffectType.BOLD_LINE_UNDERSCORE : EffectType.LINE_UNDERSCORE);
            attributes.setEffectColor(attributes.getForegroundColor());
        };
        setupAttributes(operations, id | 4);
    }

    private static void setupAttributes(TextAttributesOperation[] operations, int id) {
        ALL_ATTRIBUTES[id] = new TextAttributes();
        for(TextAttributesOperation op : operations) {
            if(op != null) {
                op.apply(ALL_ATTRIBUTES[id]);
            }
        }
    }

    private interface TextAttributesOperation {
        void apply(TextAttributes attributes);
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

    private static class ColorKey {
        private final TextAttributesKey key;
        private final Color defaultForeground;
        private final Color defaultBackground;

        public ColorKey(TextAttributesKey key, Color defaultForeground, Color defaultBackground) {
            this.key = key;
            this.defaultForeground = defaultForeground;
            this.defaultBackground = defaultBackground;
        }

        public Color getForegroundColor() {
            Color cl = colorsScheme.getAttributes(key).getForegroundColor();
            if(cl == null) cl = key.getDefaultAttributes().getForegroundColor();
            if(cl == null) return defaultForeground;
            return cl;
        }

        public Color getBackgroundColor() {
            Color cl = colorsScheme.getAttributes(key).getBackgroundColor();
            if(cl == null) cl = key.getDefaultAttributes().getBackgroundColor();
            if(cl == null) return defaultBackground;
            return cl;
        }
    }
}
