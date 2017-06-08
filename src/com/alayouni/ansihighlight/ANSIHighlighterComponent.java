package com.alayouni.ansihighlight;

import com.intellij.AppTopics;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.editor.event.EditorFactoryAdapter;
import com.intellij.openapi.editor.event.EditorFactoryEvent;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.FoldingListener;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileDocumentManagerAdapter;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorHistoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerAdapter;
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
                ansiHighlighter.highlightANSISequences(editor);
                disableUnfoldingOnPreviewMode(editor);
                if(!(editor instanceof EditorEx)) return;
                ((EditorEx)editor).setViewer(true);
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
                        ansiHighlighter.highlightANSISequences(editor);
                    }
                }
            }
        });

        connection.subscribe(TOGGLE_ANSI_HIGHLIGHTER_TOPIC, this);

        connection.subscribe(EditorColorsManager.TOPIC, (editorColorsScheme) -> {
            ANSIHighlighter.preloadAllTextAttributes();
            Editor[] openEditors = EditorFactory.getInstance().getAllEditors();
            if(openEditors == null || openEditors.length == 0) return;
            FileDocumentManager fdm = FileDocumentManager.getInstance();
            for(Editor editor : openEditors) {
                Document doc = editor.getDocument();
                VirtualFile file = fdm.getFile(doc);
                if(!ANSIAwareFileType.isANSIAware(file)) continue;
                ansiHighlighter.highlightANSISequences(editor);
            }
        });

        applyWorkaroundToDisableFoldingStateRestoration();
    }

    /**
     * workaround to prevent unfolding when under preview mode and caret falls inside a folded region
     * @param editor
     */
    private void disableUnfoldingOnPreviewMode(Editor editor) {
        if(!(editor.getFoldingModel() instanceof FoldingModelEx)) return;
        FoldingModelEx fm = (FoldingModelEx)editor.getFoldingModel();
        fm.addListener(new FoldingListener() {
            @Override
            public void onFoldRegionStateChange(@NotNull FoldRegion foldRegion) {
                if(!fm.isFoldingEnabled() || !foldRegion.isExpanded() || !editor.isViewer()) return;

                fm.runBatchFoldingOperation(() -> foldRegion.setExpanded(false));
            }

            @Override
            public void onFoldProcessingEnd() {}
        }, project);
    }

    /**
     * For ansi aware files with a large number of ansi sequences causing a large number of fold regions,
     * the ui freezes for several seconds when the ide restores folding regions internally, this method applies a workaround
     * to prevent the freeze, it takes into consideration files opened more than once in multiple splitters.
     */
    private void applyWorkaroundToDisableFoldingStateRestoration() {
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {

            @Override
            public void fileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (!ANSIAwareFileType.isANSIAware(file)) return;
                turnFoldingOn(file);
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if (!ANSIAwareFileType.isANSIAware(file)) return;
                turnFoldingOn(file);
            }
        });


        connection.subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER, new FileEditorManagerListener.Before.Adapter() {
            @Override
            public void beforeFileOpened(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if(!ANSIAwareFileType.isANSIAware(file)) return;
                turnFoldingOff(file);
            }

            @Override
            public void beforeFileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if(!ANSIAwareFileType.isANSIAware(file)) return;
                turnFoldingOff(file);
            }
        });
        ProjectManager.getInstance().addProjectManagerListener(project, new ProjectManagerAdapter() {
            @Override
            public boolean canCloseProject(Project project) {
                //canClose is the only callback that fires before saving the project
                VirtualFile[] openFiles = FileEditorManagerEx.getInstanceEx(project).getOpenFiles();
                for(VirtualFile file : openFiles) {
                    if(!ANSIAwareFileType.isANSIAware(file)) continue;
                    turnFoldingOff(file);
                }
                return true;
            }
        });
    }

    private void turnFoldingOff(VirtualFile file) {
        Document doc = FileDocumentManager.getInstance().getDocument(file);
        Editor[] editors = EditorFactory.getInstance().getEditors(doc);
        if(editors == null || editors.length == 0) return;
        for(Editor editor : editors) {
            FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
            fm.setFoldingEnabled(false);
        }
        EditorHistoryManager.getInstance(project).updateHistoryEntry(file, false);
    }

    private void turnFoldingOn(VirtualFile file) {
        Document doc = FileDocumentManager.getInstance().getDocument(file);
        Editor[] editors = EditorFactory.getInstance().getEditors(doc);
        if(editors == null || editors.length == 0) return;

        for(Editor editor : editors) {
            FoldingModelEx fm = (FoldingModelEx) editor.getFoldingModel();
            fm.setFoldingEnabled(true);
        }
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
