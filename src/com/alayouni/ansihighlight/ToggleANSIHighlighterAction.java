package com.alayouni.ansihighlight;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Created by alayouni on 5/8/17.
 */
public class ToggleANSIHighlighterAction extends AnAction {

    @Override
    public void actionPerformed(AnActionEvent e) {
        ANSIHighlighterToggleNotifier publisher = e.getProject().getMessageBus().
                syncPublisher(ANSIHighlighterToggleNotifier.TOGGLE_ANSI_HIGHLIGHTER_TOPIC);
        publisher.toggleANSIHighlighter(e);

    }

    @Override
    public void update(AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(ANSIAwareFileType.isANSIColorable(file));
    }
}
