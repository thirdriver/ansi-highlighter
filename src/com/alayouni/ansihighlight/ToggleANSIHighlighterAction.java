package com.alayouni.ansihighlight;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.LightVirtualFile;

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
        boolean isAnsiAwareFile = ANSIAwareFileType.isANSIAware(file);
        e.getPresentation().setEnabledAndVisible(isAnsiAwareFile);
        if(isAnsiAwareFile) {
            if(file instanceof LightVirtualFile) {
                e.getPresentation().setText("Switch To Edit Mode");
            } else {
                e.getPresentation().setText("Switch To Preview Mode");
            }
        }
    }
}
