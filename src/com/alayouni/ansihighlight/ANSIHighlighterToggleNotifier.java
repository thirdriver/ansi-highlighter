package com.alayouni.ansihighlight;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.util.messages.Topic;

/**
 * Created by alayouni on 5/10/17.
 */
public interface ANSIHighlighterToggleNotifier {
    Topic<ANSIHighlighterToggleNotifier> TOGGLE_ANSI_HIGHLIGHTER_TOPIC = Topic.create("Switch ANSI editor preview mode", ANSIHighlighterToggleNotifier.class);

    void toggleANSIHighlighter(AnActionEvent e);
}
