package com.alayouni.ansihighlight;

import com.intellij.openapi.editor.markup.HighlighterLayer;
import com.intellij.openapi.editor.markup.HighlighterTargetArea;
import com.intellij.openapi.editor.markup.MarkupModel;
import com.intellij.openapi.editor.markup.TextAttributes;


/**
 * Created by alayouni on 6/5/17.
 */
public class HighlightRangeData {
    int start, end, id;

    public HighlightRangeData(){}

    public HighlightRangeData(int start, int end, int id) {
        this.start = start;
        this.end = end;
        this.id = id;
    }

    public void apply(MarkupModel markupModel, TextAttributes[] allAttributes) {
        markupModel.addRangeHighlighter(start, end, HighlighterLayer.ADDITIONAL_SYNTAX, allAttributes[id], HighlighterTargetArea.EXACT_RANGE);
    }
}
