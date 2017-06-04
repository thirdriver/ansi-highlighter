package com.alayouni.ansihighlight;

import com.intellij.execution.process.ConsoleHighlighter;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.*;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.JBColor;
import org.jetbrains.annotations.NotNull;

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
            new ColorKey(ConsoleHighlighter.BLACK, JBColor.BLACK, JBColor.BLACK),
            new ColorKey(ConsoleHighlighter.RED, JBColor.RED, JBColor.RED),
            new ColorKey(ConsoleHighlighter.GREEN, JBColor.GREEN, JBColor.GREEN),
            new ColorKey(ConsoleHighlighter.YELLOW, JBColor.YELLOW, JBColor.YELLOW),
            new ColorKey(ConsoleHighlighter.BLUE, JBColor.BLUE, JBColor.BLUE),
            new ColorKey(ConsoleHighlighter.MAGENTA, JBColor.MAGENTA, JBColor.MAGENTA),
            new ColorKey(ConsoleHighlighter.CYAN, JBColor.CYAN, JBColor.CYAN),
            new ColorKey(ConsoleHighlighter.WHITE, JBColor.WHITE, JBColor.WHITE)
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

    private static final int HIGHLIGHT_OPERATION_COUNT = 100;

    private static final int FOLD_OPERATION_COUNT = 100;

    private static final int SLEEP_MILLIS = 10;

    private final Project project;

    private final Application application;

    public ANSIHighlighter(Project project) {
        this.project = project;
        preloadAllTextAttributes();
        application = ApplicationManager.getApplication();
    }

    public void cleanupHighlights(Editor editor) {
        editor.getMarkupModel().removeAllHighlighters();
        if(!(editor.getFoldingModel() instanceof FoldingModelEx)) return;
        FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
        fm.runBatchFoldingOperation(fm::clearFoldRegions, false);
    }

    public void highlightANSISequences(Editor editor) {
        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Highlighting ANSI Sequences...", true){
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Pair<List<MyHighlightInfo>, List<FoldRegion>> result = parseHighlights(editor);
                if(isEmptyHighlightResult(result)) return;
                applyHighlights(editor, result.first, result.second, indicator);
            }
        });
    }

    private boolean isEmptyHighlightResult(Pair<List<MyHighlightInfo>, List<FoldRegion>> result) {
        if(result == null) return true;
        return (result.first == null || result.first.isEmpty()) && (result.second == null || result.second.isEmpty());
    }

    private Pair<List<MyHighlightInfo>, List<FoldRegion>> parseHighlights(Editor editor) {
        String text = editor.getDocument().getText();
        MarkupModel markup = editor.getMarkupModel();
        int escIndex = text.indexOf(ESC),
                start0 = 0;
        if(escIndex == -1) return null;

        TextAttributesRange range = new TextAttributesRange();
        range.start = escIndex;
        int id0 = -1;
        FoldingModelEx foldingModel = (FoldingModelEx) editor.getFoldingModel();
        FoldRegion foldRegion;
        List<FoldRegion> foldRegions = new ArrayList<>();
        List<MyHighlightInfo> highlighters = new ArrayList<>();
        while (range.start >= 0) {
            extractTextAttributesFromANSIEscapeSequence(text, range);
            if(range.id == -1) {
                range.start = range.end < text.length() ? text.indexOf(ESC, range.end) : -1;
            } else {
                if(id0 > 0) {
                    highlighters.add(new MyHighlightInfo(markup, start0, range.start, id0));
                }
                foldRegion = foldingModel.createFoldRegion(range.start, range.end, "", null, true);
                foldRegions.add(foldRegion);

                start0 = range.end;
                if(start0 == text.length()) break;
                id0 = range.id == 0 ? -1 : range.id;
                range.start = text.indexOf(ESC, start0);
            }
            if(range.start == -1 && id0 > 0) {
                highlighters.add(new MyHighlightInfo(markup, start0, text.length(), id0));
            }
        }
        return Pair.create(highlighters, foldRegions);
    }

    private void applyHighlights(Editor editor, List<MyHighlightInfo> highlighters, List<FoldRegion> foldRegions, ProgressIndicator indicator) {
        try {
            int hIndex = 0, fIndex = 0;
            int hSize = highlighters == null ? 0 : highlighters.size(),
                    fSize = foldRegions == null ? 0 : foldRegions.size();
            while(hIndex < hSize || fIndex < fSize) {
                final int hStartIndex = hIndex,
                        fStartIndex = fIndex;
                application.invokeLater(()-> {
                        applyHighlights(highlighters, hStartIndex, null);
                        applyFoldRegions(editor, foldRegions, fStartIndex, indicator);
                });
                hIndex += HIGHLIGHT_OPERATION_COUNT;
                fIndex += FOLD_OPERATION_COUNT;
                Thread.sleep(SLEEP_MILLIS);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void applyHighlights(List<MyHighlightInfo> highlighters, int startIndex, ProgressIndicator indicator) {
        if(startIndex >= highlighters.size()) return;
        int lastIndex = Math.min(startIndex + HIGHLIGHT_OPERATION_COUNT, highlighters.size());
        for(int i = startIndex; i < lastIndex; i++ ) {
            highlighters.get(i).apply();
        }
        if(indicator != null) indicator.setFraction(lastIndex / (double) highlighters.size());
    }


    private void applyFoldRegions(Editor editor, List<FoldRegion> foldRegions, int startIndex, ProgressIndicator indicator) {
        if(startIndex >= foldRegions.size()) return;
        final FoldingModel folder = editor.getFoldingModel();
        int lastIndex = Math.min(startIndex + FOLD_OPERATION_COUNT, foldRegions.size());
        folder.runBatchFoldingOperation(() -> {
            FoldRegion region;
            for(int i = startIndex; i < lastIndex; i++) {
                region = foldRegions.get(i);
                folder.addFoldRegion(region);
                region.setExpanded(false);
            }
        }, true);
        if(indicator != null) indicator.setFraction(lastIndex / (double)foldRegions.size());
    }



    private void extractTextAttributesFromANSIEscapeSequence(String text, TextAttributesRange range) {
        range.end = range.start;
        TextAttributesEncoder encoder;
        range.id = 0;
        while (range.end < text.length() && text.startsWith(ESC, range.end)) {
            range.end += ESC.length();
            do {
                encoder = parseANSICodeEncoder(text, range);
                if(encoder == null) {
                    range.id = -1;
                    return;
                }
                range.id = encoder.encode(range.id);
            } while(text.charAt(range.end - 1) == SEQ_DELIM);
        }
    }

    private TextAttributesEncoder parseANSICodeEncoder(String text, TextAttributesRange range) {
        int code = 0, d;
        boolean atLeastOneDigit = false;
        char c;
        while (range.end < text.length()) {
            c = text.charAt(range.end ++);
            if(c == SEQ_DELIM || c == SEQ_END) return atLeastOneDigit ? ENCODER[code] : null;
            d = c - '0';
            if(d < 0 || d > 9) return null;
            atLeastOneDigit = true;
            code = code * 10 + d;
            if(code >= ENCODER.length) return null;
        }
        return null;
    }


    private static class TextAttributesRange {
        private int id = 0, start, end;
    }

    private static class TextAttributesEncoder {
        private final int resetMask;
        private final int mask;

        private TextAttributesEncoder(int resetMask, int mask) {
            this.resetMask = resetMask;
            this.mask = mask;
        }

        private int encode(int id) {
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

    private static class MyHighlightInfo {
        final MarkupModel markupModel;
        final int start, end, attrId;

        public MyHighlightInfo(MarkupModel markupModel, int start, int end, int attrId) {
            this.markupModel = markupModel;
            this.start = start;
            this.end = end;
            this.attrId = attrId;
        }

        public void apply() {
            markupModel.addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, ALL_ATTRIBUTES[attrId], HighlighterTargetArea.EXACT_RANGE);
        }
    }

    private static class ColorKey {
        private final TextAttributesKey key;
        private final Color defaultForeground;
        private final Color defaultBackground;

        private ColorKey(TextAttributesKey key, Color defaultForeground, Color defaultBackground) {
            this.key = key;
            this.defaultForeground = defaultForeground;
            this.defaultBackground = defaultBackground;
        }

        private Color getForegroundColor() {
            Color cl = colorsScheme.getAttributes(key).getForegroundColor();
            if(cl == null) cl = key.getDefaultAttributes().getForegroundColor();
            if(cl == null) return defaultForeground;
            return cl;
        }

        private Color getBackgroundColor() {
            Color cl = colorsScheme.getAttributes(key).getBackgroundColor();
            if(cl == null) cl = key.getDefaultAttributes().getBackgroundColor();
            if(cl == null) return defaultBackground;
            return cl;
        }
    }
}
