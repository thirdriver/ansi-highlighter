package com.alayouni.ansihighlight;

import com.intellij.AppTopics;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.messages.MessageBusConnection;
import org.jetbrains.annotations.NotNull;

/**
 * Created by alayouni on 5/2/17.
 */
public class ANSIHighlighterComponent implements ProjectComponent, ANSIHighlighterToggleNotifier {

    private final Project project;

    private final ANSIHighlighter ansiHighlighter;
    final MessageBusConnection connection;

    public ANSIHighlighterComponent(Project project) {
        this.project = project;
        this.ansiHighlighter = new ANSIHighlighter(project);
        connection = project.getMessageBus().connect();
    }

    @Override
    public void initComponent() {
        EditorFactory.getInstance().addEditorFactoryListener(new EditorFactoryAdapter() {
            @Override
            public void editorCreated(@NotNull EditorFactoryEvent e) {
                Editor editor = e.getEditor();
                VirtualFile file = FileDocumentManager.getInstance().getFile(editor.getDocument());
                if(!ANSIAwareFileType.isANSIAware(file)) return;
                //listener must be added first
//                ansiHighlighter.addFoldingListener(editor);

                ansiHighlighter.highlightANSISequences(editor);

                if(!(editor instanceof EditorEx)) return;
                ((EditorEx)editor).setViewer(true);
            }


            @Override
            public void editorReleased(@NotNull EditorFactoryEvent e) {
                Editor editor = e.getEditor();

            }
        }, project);

        //sync editor highlights to external changes brought to file
        connection.subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
            @Override
            public void fileContentReloaded(@NotNull VirtualFile file, @NotNull Document document) {
                if(!ANSIAwareFileType.isANSIAware(file)) return;
                Editor[] editors = EditorFactory.getInstance().getEditors(document);
                if(editors == null || editors.length == 0) return;
                for(Editor editor : editors) {
                    if(editor.isViewer()) {
                        ansiHighlighter.cleanupHighlights(editor);
                        ansiHighlighter.highlightANSISequences(editor);
                    }
                }
            }
        });

        connection.subscribe(TOGGLE_ANSI_HIGHLIGHTER_TOPIC, this);
    }

    @Override
    public void toggleANSIHighlighter(AnActionEvent e) {
        VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if(!ANSIAwareFileType.isANSIAware(file)) return;
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        if(!(editor instanceof EditorEx)) return;
        EditorEx ex = (EditorEx) editor;
        ex.setViewer(!ex.isViewer());
        if(ex.isViewer()) {//preview mode
            ansiHighlighter.highlightANSISequences(editor);
        } else {
            ansiHighlighter.cleanupHighlights(editor);
        }
    }

    @Override
    public void disposeComponent() {
        connection.disconnect();
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "ANSIHighlighter";
    }

    @Override
    public void projectOpened() {

    }

    @Override
    public void projectClosed() {

    }
}
