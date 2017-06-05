package com.alayouni.ansihighlight;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;

import java.util.List;

/**
 * Created by alayouni on 6/5/17.
 */
class HighlightTaskData {
    static final int MAX_HIGHLIGHT_OPERATION_COUNT_PER_UI_THREAD_CALL = 100;

    static final int MAX_FOLD_OPERATION_COUNT_PER_UI_THREAD_CALL = 100;

    private final Editor editor;
    private final List<HighlightRangeData> highlights;
    private final List<FoldRegion> foldRegions;
    private int highlightsStart = 0, foldRegionsStart = 0;

    public HighlightTaskData(Editor editor, List<HighlightRangeData> highlights, List<FoldRegion> foldRegions) {
        this.editor = editor;
        this.highlights = highlights;
        this.foldRegions = foldRegions;
    }

    private HighlightTaskData previous, next;

    public Editor getEditor() {
        return editor;
    }

    public List<HighlightRangeData> getHighlights() {
        return highlights;
    }

    public List<FoldRegion> getFoldRegions() {
        return foldRegions;
    }

    public int getHighlightsStart() {
        return highlightsStart;
    }

    public int getHighlightsEnd() {
        if(highlights == null || highlights.isEmpty()) return 0;
        int end = highlightsStart + MAX_HIGHLIGHT_OPERATION_COUNT_PER_UI_THREAD_CALL;
        return end <= highlights.size() ? end : highlights.size();
    }

    public int getFoldRegionsStart() {
        return foldRegionsStart;
    }

    public int getFoldRegionsEnd() {
        if(foldRegions == null || foldRegions.isEmpty()) return 0;
        int end = foldRegionsStart + MAX_FOLD_OPERATION_COUNT_PER_UI_THREAD_CALL;
        return end <= foldRegions.size() ? end : foldRegions.size();
    }

    HighlightTaskData previous() {
        return previous;
    }

    HighlightTaskData next() {
        return next;
    }

    void setPrevious(HighlightTaskData previous) {
        this.previous = previous;
    }

    void setNext(HighlightTaskData next) {
        this.next = next;
    }

    /**
     * returns true if task is fully processed
     * @return
     */
    boolean taskProcessedUpdateData() {
        highlightsStart = getHighlightsEnd();
        foldRegionsStart = getFoldRegionsEnd();
        return isHighlightsFullyProcessed() && isFoldRegionsFullyProcessed();
    }

    private boolean isHighlightsFullyProcessed() {
        return highlights == null || highlights.size() <= highlightsStart;
    }

    private boolean isFoldRegionsFullyProcessed() {
        return foldRegions == null || foldRegions.size() <= foldRegionsStart;
    }

    public void run(TextAttributes[] allAttributes) {
        ApplicationManager.getApplication().assertIsDispatchThread();
        applyHighlights(allAttributes);
        applyFoldRegions();
    }

    private void applyHighlights(TextAttributes[] allAttributes) {
        if(isHighlightsFullyProcessed()) return;
        MarkupModel markupModel = editor.getMarkupModel();
        for(int i = getHighlightsStart(); i < getHighlightsEnd(); i++ ) {
            highlights.get(i).apply(markupModel, allAttributes);
        }
    }


    private void applyFoldRegions() {
        if(isFoldRegionsFullyProcessed()) return;
        final FoldingModel folder = getEditor().getFoldingModel();
        folder.runBatchFoldingOperation(() -> {
            FoldRegion region;
            for(int i = getFoldRegionsStart(); i < getFoldRegionsEnd(); i++) {
                region = foldRegions.get(i);
                folder.addFoldRegion(region);
                region.setExpanded(false);
            }
        }, true);
    }

}
