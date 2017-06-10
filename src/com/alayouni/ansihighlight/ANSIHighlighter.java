package com.alayouni.ansihighlight;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.progress.PerformInBackgroundOption;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.BoundedTaskExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.ide.PooledThreadExecutor;

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

    private HighlightQueue queue = new HighlightQueue();

    private final Project project;

    private final Application application;

    private final BoundedTaskExecutor executor;


    public ANSIHighlighter(Project project) {
        this.project = project;
        this.application = ApplicationManager.getApplication();
        executor = new BoundedTaskExecutor("ANSI Highlighter Executor", PooledThreadExecutor.INSTANCE, 1, project);
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

    //todo remove
    int count = 0;
    public void highlightANSISequences(Editor editor) {
        application.assertIsDispatchThread();
        //increment id to gracefully neutralize and end other EDT scheduled highlight tasks
        cleanupHighlights(editor);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Highlighting ANSI Sequences...", false) {
            //here using a backgroundable task to allow processing of editor content in multiple threads
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                Pair<List<HighlightRangeData>, List<FoldRegion>> result = parseHighlights(editor);
                applyHighlights(editor, result);
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
        ANSITextAttributesIDEncoder encoder;
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

    private ANSITextAttributesIDEncoder parseANSICode(String text, HighlightRangeData range) {
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
                                 Pair<List<HighlightRangeData>, List<FoldRegion>> result) {
        if(isEmptyHighlightResult(result)) return;
        application.invokeAndWait(() ->
            queue.addNewTask(editor, result.first, result.second)
        );

        executor.execute(() -> {
            //dequeue and submit tasks to UI thread from single thread using BoundedTaskExecutor, to avoid overloading the UI thread
            if(queue.isEmpty()) return;
            ItemHolder<BackgroundableProcessIndicator> indicator = new ItemHolder<>();
            application.invokeAndWait(() -> {
                indicator.set(new BackgroundableProcessIndicator(project, "Highlighting ANSI Sequences...", PerformInBackgroundOption.ALWAYS_BACKGROUND,
                        null, null, false));
                indicator.get().start();
            });
            while(!queue.isEmpty()) {
                application.invokeAndWait(() -> {
                    if(queue.isEmpty()) return;
                    HighlightTaskData task = queue.next();
                    if(task.getEditor().isDisposed()) {
                        queue.removeTask(task);
                        return;
                    }
                    task.run(ALL_ATTRIBUTES);
                    queue.taskProcessedUpdateQueue(task);
                });
                indicator.get().setFraction(queue.getProgressFraction());
            }
            application.invokeAndWait(() -> {
                indicator.get().stop();
                indicator.get().processFinish();
            });
        });
    }


    //______________________________Pre-calculations to accelerate parsing______________________________________

    /**
     * <p>
     *     A supported ANSI sequence can result in exactly 648 different text attributes, that is:
     *     <ul>
     *         <li>2 (bold set/not set)</li>
     *         <li>&times; 2 (italic set /not set)</li>
     *         <li>&times; 2 (underlined set/not set) </li>
     *         <li>&times; 9 (1 of 8 foreground colors or not set)</li>
     *         <li>&times; 9 (1 of 8 background colors or not set).</li>
     *
     *     </ul>
     * </p>
     *
     * <p>
     *     Pre-loading and caching all the different values in an array at startup makes them efficiently accessible
     *     through their indexes using bitwise operations if their indexes get slightly expanded to line-up as follows:
     *     <ul>
     *         <li>bit 0: bold set(1)/not set(0)</li>
     *         <li>bit 1: italic set(1)/not set(0)</li>
     *         <li>bit 2: underlined set(1)/not set(0)</li>
     *         <li>bit 3-6 (4 bits): 0 if not set, otherwise 1(black) to 8(white)</li>
     *         <li>bit 7-10 (4 bits): 0 if not set, otherwise 1(black) to 8(white)</li>
     *     </ul>
     *     This makes the highest possible index value 1000 1000 111b = 1095. Id's such as 100 1111 111b don't map
     *     to a valid/supported sequence and are be left unset/null.
     * </p>
     */
    private static final TextAttributes[] ALL_ATTRIBUTES = new TextAttributes[1096];

    /**
     * To infer the index (or id) of a cached TextAttributes instance matching a parsed ansi sequence, the parser
     * proceeds as follows:
     * <ul>
     *     <li>Start by initializing the id to 0</li>
     *     <li>For each parsed code in the sequence apply the following formula <code>id = (id & resetMask) | mask</code></li>
     * </ul>
     * <p>
     *     Example: say the parsed code is 35 (magenta foreground), here the matching <code>resetMask</code> would be
     *     ...1111 0000 111b, and <code>mask</code> would be ...0000 0110 000b.
     *     Notice the use of 0110b = 6 instead of 0101b = 5, that is because 0000 maps to null (no foreground color
     *     code specified in the sequence), which means black (code 30) should map to 0001b instead of 0000b.
     * </p>
     *
     * <p>
     *     <code>ENCODER</code> is pre-calculated at startup and maps each supported ANSI code (ranges from 0 to 47) to the
     *     corresponding mask/resetMask encapsulated under {@link ANSITextAttributesIDEncoder} instances.
     * </p>
     *
     * <p>
     *     Using the cached encoders to calculate id's (indexes) of cached TextAttributes is very efficient, which is
     *     important for large files with a large number of ansi sequences.
     * </p>
     */
    private static final ANSITextAttributesIDEncoder[] ENCODER = new ANSITextAttributesIDEncoder[48];

    static {
        ENCODER[RESET] = new ANSITextAttributesIDEncoder(0, 0);
        ENCODER[BOLD] = new ANSITextAttributesIDEncoder(0xFFFFFFFE, 1);
        ENCODER[ITALIC] = new ANSITextAttributesIDEncoder(0xFFFFFFFD, 2);
        ENCODER[UNDERLINE] = new ANSITextAttributesIDEncoder(0xFFFFFFFB, 4);

        ANSIColor.setupColorsEncoders(ENCODER);
    }

    public static void preloadAllTextAttributes() {
        ANSIColor.initAllANSIColors();

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
