package com.alayouni.ansihighlight;

import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;

/**
 * Created by alayouni on 6/3/17.
 */
public class ANSIFoldingListener implements FoldingListener {
    private static final Key<ANSIFoldingListener> FOLDING_LISTENER_KEY = Key.create("ansiFoldingListener");

    private boolean enabled = false;
    private final FoldingModelEx fm;

    public ANSIFoldingListener(FoldingModelEx fm) {
        this.fm = fm;
    }

    @Override
    public void onFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
        if(!enabled || !foldRegion.isExpanded()) return;

        //workaround to prevent unfolding when under preview mode and caret falls inside a folded region
        fm.runBatchFoldingOperation(() -> foldRegion.setExpanded(false));
    }

    @Override
    public void onFoldProcessingEnd() {}

    public static void addFoldingListener(Editor editor, Project project) {
        if(!(editor.getFoldingModel() instanceof FoldingModelEx)) return;
        FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
        ANSIFoldingListener listener = new ANSIFoldingListener(fm);
        fm.addListener(listener, project);
        editor.putUserData(FOLDING_LISTENER_KEY, listener);
    }

    public static void setFoldingListenerEnabled(Editor editor, boolean enabled) {
        ANSIFoldingListener listener = editor.getUserData(FOLDING_LISTENER_KEY);
        if(listener != null) listener.enabled = enabled;
    }
}
