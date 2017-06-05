package com.alayouni.ansihighlight;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
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


    private long currentBackgroundTaskId = 0;
    private HighlightQueue queue = new HighlightQueue();

    private static final int SLEEP_MILLIS = 5;

    private final Project project;

    private final Application application;


    public ANSIHighlighter(Project project) {
        this.project = project;
        this.application = ApplicationManager.getApplication();
        preloadAllTextAttributes();
    }

    public void cleanupHighlights(Editor editor) {
        application.assertIsDispatchThread();
        queue.removeEditorHighlightTaskIfQueed(editor);
        //update process id to cancel any ongoing highlight tasks on this editor
        editor.getMarkupModel().removeAllHighlighters();
        if(!(editor.getFoldingModel() instanceof FoldingModelEx)) return;
        FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
        fm.runBatchFoldingOperation(fm::clearFoldRegions, false);
    }

    public void highlightANSISequences(Editor editor) {
        application.assertIsDispatchThread();
        //increment id to gracefully neutralize and end other EDT scheduled highlight tasks
        final long id = ++currentBackgroundTaskId;
        cleanupHighlights(editor);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Highlighting ANSI Sequences...", true) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Pair<List<HighlightRangeData>, List<FoldRegion>> result = parseHighlights(editor);
                applyHighlights(editor, result, indicator, id);
            }
        });
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

    private void applyHighlights(Editor editor,
                                 Pair<List<HighlightRangeData>, List<FoldRegion>> result,
                                 ProgressIndicator indicator,
                                 long id) {
        try {
            application.invokeAndWait(() -> {
                if(!isEmptyHighlightResult(result))
                    queue.addNewTask(editor, result.first, result.second);
            });

            while(id == currentBackgroundTaskId && !queue.isEmpty()) {
                application.invokeAndWait(() -> {
                    if(id != currentBackgroundTaskId || queue.isEmpty()) return;
                    HighlightTaskData task = queue.next();
                    if(task.getEditor().isDisposed()) {
                        queue.removeTask(task);
                        return;
                    }
                    task.run(ALL_ATTRIBUTES);
                    queue.taskProcessedUpdateQueue(task);
                });

                indicator.setFraction(queue.getProgressFraction());
                //sleep a bit to prevent freezing the UI for large highlight tasks
                Thread.sleep(SLEEP_MILLIS);
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    //______________________________Pre-calculations to accelerate parsing______________________________________

    private static final ANSITextAttributesEncoder[] ENCODER = new ANSITextAttributesEncoder[49];

    private static final TextAttributes[] ALL_ATTRIBUTES = new TextAttributes[1096];

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
