package com.alayouni.ansihighlight;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.Pair;
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


    private static long processId = 0;

    private static Key<Long> ANSI_HIGHLIGHTER_PROCESS_KEY = Key.create("ansi-highlighter-process-id");

    private static final ANSITextAttributesEncoder[] ENCODER = new ANSITextAttributesEncoder[49];

    private static final TextAttributes[] ALL_ATTRIBUTES = new TextAttributes[1096];


    private static final int HIGHLIGHT_OPERATION_COUNT = 100;

    private static final int FOLD_OPERATION_COUNT = 100;

    private static final int SLEEP_MILLIS = 5;

    private final Project project;

    private final Application application;


    public ANSIHighlighter(Project project) {
        this.project = project;
        preloadAllTextAttributes();
        application = ApplicationManager.getApplication();
    }

    public void cleanupHighlights(Editor editor) {
        application.assertIsDispatchThread();
        //update process id to cancel any ongoing highlight tasks on this editor
        editor.putUserData(ANSI_HIGHLIGHTER_PROCESS_KEY, processId);
        editor.getMarkupModel().removeAllHighlighters();
        if(!(editor.getFoldingModel() instanceof FoldingModelEx)) return;
        FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
        fm.runBatchFoldingOperation(fm::clearFoldRegions, false);
    }

    public void highlightANSISequences(Editor editor) {
        application.assertIsDispatchThread();
        editor.putUserData(ANSI_HIGHLIGHTER_PROCESS_KEY, processId);
        final long id = processId ++;

        Task.Backgroundable task = new Task.Backgroundable(project, "Highlighting ANSI Sequences...", true){
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Pair<List<HighlightRangeData>, List<FoldRegion>> result = parseHighlights(editor);
                if(isEmptyHighlightResult(result)) return;
                applyHighlights(editor, result.first, result.second, indicator, id);
            }
        };
        ProgressIndicator indicator = new BackgroundableProcessIndicator(task);
        ProgressManager.getInstance().runProcessWithProgressAsynchronously(task, indicator);
    }

    private boolean isEmptyHighlightResult(Pair<List<HighlightRangeData>, List<FoldRegion>> result) {
        if(result == null) return true;
        return (result.first == null || result.first.isEmpty()) && (result.second == null || result.second.isEmpty());
    }

    private Pair<List<HighlightRangeData>, List<FoldRegion>> parseHighlights(Editor editor) {
        String text = editor.getDocument().getText();
        int escIndex = text.indexOf(ESC),
                start0 = 0;
        if(escIndex == -1) return null;

        HighlightRangeData range = new HighlightRangeData();
        range.start = escIndex;
        int id0 = -1;
        FoldingModelEx foldingModel = (FoldingModelEx) editor.getFoldingModel();
        FoldRegion foldRegion;
        List<FoldRegion> foldRegions = new ArrayList<>();
        List<HighlightRangeData> highlighters = new ArrayList<>();
        while (range.start >= 0) {
            extractTextAttributesFromANSIEscapeSequence(text, range);
            if(range.id == -1) {
                range.start = range.end < text.length() ? text.indexOf(ESC, range.end) : -1;
            } else {
                if(id0 > 0) {
                    highlighters.add(new HighlightRangeData(start0, range.start, id0));
                }
                foldRegion = foldingModel.createFoldRegion(range.start, range.end, "", null, true);
                foldRegions.add(foldRegion);

                start0 = range.end;
                if(start0 == text.length()) break;
                id0 = range.id == 0 ? -1 : range.id;
                range.start = text.indexOf(ESC, start0);
            }
            if(range.start == -1 && id0 > 0) {
                highlighters.add(new HighlightRangeData(start0, text.length(), id0));
            }
        }
        return Pair.create(highlighters, foldRegions);
    }

    private void applyHighlights(Editor editor, List<HighlightRangeData> highlighters, List<FoldRegion> foldRegions, ProgressIndicator indicator, long id) {
        try {
            int hIndex = 0, fIndex = 0;
            int hSize = highlighters == null ? 0 : highlighters.size(),
                    fSize = foldRegions == null ? 0 : foldRegions.size();
            while(hIndex < hSize || fIndex < fSize) {
                if(!editor.getUserData(ANSI_HIGHLIGHTER_PROCESS_KEY).equals(id)) return;
                final int hStartIndex = hIndex,
                        fStartIndex = fIndex;
                application.invokeAndWait(()-> {
                    if(!editor.getUserData(ANSI_HIGHLIGHTER_PROCESS_KEY).equals(id)) return;
                    applyHighlights(editor, highlighters, hStartIndex, null);
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

    private void applyHighlights(Editor editor, List<HighlightRangeData> highlighters, int startIndex, ProgressIndicator indicator) {
        MarkupModel markupModel = editor.getMarkupModel();
        if(startIndex >= highlighters.size()) return;
        int lastIndex = Math.min(startIndex + HIGHLIGHT_OPERATION_COUNT, highlighters.size());
        for(int i = startIndex; i < lastIndex; i++ ) {
            highlighters.get(i).apply(markupModel, ALL_ATTRIBUTES);
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



    private void extractTextAttributesFromANSIEscapeSequence(String text, HighlightRangeData range) {
        range.end = range.start;
        ANSITextAttributesEncoder encoder;
        range.id = 0;
        while (range.end < text.length() && text.startsWith(ESC, range.end)) {
            range.end += ESC.length();
            do {
                encoder = parseANSICode(text, range);
                if(encoder == null) {
                    range.id = -1;
                    return;
                }
                range.id = encoder.encode(range.id);
            } while(text.charAt(range.end - 1) == SEQ_DELIM);
        }
    }

    private ANSITextAttributesEncoder parseANSICode(String text, HighlightRangeData range) {
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


    //______________________________Pre-calculations to accelerate parsing______________________________________

    static {
        ENCODER[RESET] = new ANSITextAttributesEncoder(0, 0);
        ENCODER[BOLD] = new ANSITextAttributesEncoder(0xFFFFFFFE, 1);
        ENCODER[ITALIC] = new ANSITextAttributesEncoder(0xFFFFFFFD, 2);
        ENCODER[UNDERLINE] = new ANSITextAttributesEncoder(0xFFFFFFFB, 4);

        ANSIColor.setupColorsEncoders(ENCODER);
    }

    private static void preloadAllTextAttributes() {
        TextAttributesOperation[] operations = new TextAttributesOperation[5];
        operations[0] = null;
        setupAllAttributesItalic(operations, 0);
        operations[0] = (attributes) -> attributes.setFontType(attributes.getFontType() | Font.BOLD);
        setupAllAttributesItalic(operations, 1);
    }

    static void setupAllAttributesItalic(TextAttributesOperation[] operations, int id) {
        operations[1] = null;
        ANSIColor.setupAllAttributesForeground(operations, id);
        operations[1] = (attributes) -> attributes.setFontType(attributes.getFontType() | Font.ITALIC);
        ANSIColor.setupAllAttributesForeground(operations, id | 2);
    }


    static void setupAllAttributesUnderline(TextAttributesOperation[] operations, int id) {
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

    static void setupAttributes(TextAttributesOperation[] operations, int id) {
        ALL_ATTRIBUTES[id] = new TextAttributes();
        for(TextAttributesOperation op : operations) {
            if(op != null) {
                op.apply(ALL_ATTRIBUTES[id]);
            }
        }
    }
}
