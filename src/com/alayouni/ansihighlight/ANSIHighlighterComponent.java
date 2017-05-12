package com.alayouni.ansihighlight;

import com.intellij.AppTopics;
import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.conversion.CannotConvertException;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.util.messages.MessageBus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by alayouni on 5/2/17.
 */
public class ANSIHighlighterComponent implements ProjectComponent, ANSIHighlighterToggleNotifier {

    class OpenLightFileInfo {
        final String realFilePath, collapsedRealFilePath, lightFileName;
        OpenFileDescriptor lightFileDescriptor;
        int openEditorCount = 0;

        public OpenLightFileInfo(VirtualFile realFile, String lightFileName) {
            this.realFilePath = realFile.getPath();
            this.collapsedRealFilePath = utils.collapsePath(realFile);
            this.lightFileName = lightFileName;
        }
    }

    private Project project;
    private Map<String, OpenLightFileInfo> lightFileToReal;
    private Map<String, OpenLightFileInfo> realFileToLight;
    private Utils utils;
    private boolean isProjectInitialized = false;
    private Editor lastSelectedEditor;
    private VirtualFile lastSelectedFile;
    private boolean isTogglingANSIHighlighter = false;

    public ANSIHighlighterComponent(Project project) {
        this.project = project;
        lightFileToReal = new HashMap<>();
        realFileToLight = new HashMap<>();
        try {
            utils = new Utils(project);
        } catch (CannotConvertException e) {
            utils = null;
            e.printStackTrace();
        }
        StartupManager.getInstance(project).runWhenProjectIsInitialized(()->{
            isProjectInitialized = true;
        });
    }

    @Override
    public void initComponent() {
        MessageBus messageBus = project.getMessageBus();
        messageBus.connect().subscribe(TOGGLE_ANSI_HIGHLIGHTER_TOPIC, this);
        messageBus.connect().subscribe(AppTopics.FILE_DOCUMENT_SYNC, new FileDocumentManagerAdapter() {
            @Override
            public void beforeAllDocumentsSaving() {
                System.out.println("before doc saving  ");
            }
        });

        messageBus.connect().subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, new FileEditorManagerListener() {
            @Override
            public void fileOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile file) {
                if(!ANSIAwareFileType.isANSIColorable(file)) return;
                OpenLightFileInfo fileInfo = infoForFile(file);
                fileInfo.openEditorCount ++;
                if (file instanceof LightVirtualFile) {
                    lightFileOpened(fileEditorManager, file, fileInfo);
                } else if(!isTogglingANSIHighlighter) {
                     if (fileInfo.openEditorCount == 1) {
                        realFileOpenedFirstTime(fileEditorManager, file, fileInfo);
                    } else {
                        realFileReOpened(fileEditorManager, file, fileInfo);
                    }
                }
                isTogglingANSIHighlighter = false;
            }

            @Override
            public void fileClosed(@NotNull FileEditorManager source, @NotNull VirtualFile file) {
                if(!ANSIAwareFileType.isANSIColorable(file)) return;
                OpenLightFileInfo info = infoForFile(file);
                info.openEditorCount --;
                if(info.openEditorCount == 0) {
                    realFileToLight.remove(info.realFilePath);
                    lightFileToReal.remove(info.lightFileName);
                }
            }


            @Override
            public void selectionChanged(@NotNull FileEditorManagerEvent e) {
                lastSelectedEditor = e.getOldEditor() == null ? null : ((TextEditor)e.getOldEditor()).getEditor();
                lastSelectedFile = e.getOldFile();
            }
        });
    }

    private OpenLightFileInfo infoForFile(VirtualFile file) {
        OpenLightFileInfo info = (file instanceof LightVirtualFile) ? lightFileToReal.get(file.getName()) : infoForRealFile(file);
        return info;
    }

    private void lightFileOpened(@NotNull FileEditorManager fileEditorManager, VirtualFile file, OpenLightFileInfo info) {
        utils.runInEDT(() -> {
            Editor editor = ((TextEditor) fileEditorManager.getSelectedEditor(file)).getEditor();
            info.lightFileDescriptor.navigate(false);
            editor.getMarkupModel().removeAllHighlighters();

            TextAttributes attr = new TextAttributes(Color.red, null, null, null, Font.PLAIN);
            TextAttributes attr2 = new TextAttributes(Color.orange, null, null, null, Font.PLAIN);
            HighlightManager.getInstance(project).addRangeHighlight(editor, 0, 4, attr, false, null);
            HighlightManager.getInstance(project).addRangeHighlight(editor, 6, 10, attr2, false, null);
        }, false);

//                    editor.getDocument().addDocumentListener(new DocumentAdapter() {
//                        @Override
//                        public void documentChanged(DocumentEvent e) {
//                            long fileModification = file.getModificationStamp(),
//                                    documentStamp = e.getDocument().getModificationStamp();
//                            String cmp = cmp(fileModification, documentStamp);
//
//                            System.out.println("After: fileName=" + file.getName() + "   file modi: " + fileModification + "-" + fileModification + cmp + " document modification: " + documentStamp);
//                        }
//                    });

    }

    /**
     * Called when a real file got opened for the first time, which could occur either during project initialization or if triggered explicitly by user.
     * In both cases the real file will be closed and the associated light file passed in parameter will be opened instead under the
     * same splitter where the closed real file got originally opened.
     *
     * @param fileEditorManager
     * @param file
     * @param associatedLightFileInfo
     */
    private void realFileOpenedFirstTime(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile file, OpenLightFileInfo associatedLightFileInfo) {
        //todo remove below
        String fileContent = file.getName();
        fileContent += (fileContent + "   " + fileContent + "   " + fileContent + "   " + fileContent + "   " + fileContent + "   " + fileContent + "   " + fileContent + "   ");
        //todo remove above

        FileType fileType = file.getFileType();
        VirtualFile lightFile = new LightVirtualFile(associatedLightFileInfo.lightFileName, fileType, fileContent, file.getModificationStamp());
        associatedLightFileInfo.lightFileDescriptor = new OpenFileDescriptor(project, lightFile);
        utils.runInEDT(() -> {
            fileEditorManager.openTextEditor(associatedLightFileInfo.lightFileDescriptor, true);
            fileEditorManager.closeFile(file);//safe to close using file reference since here file got opened for the first time
        }, !isProjectInitialized);
    }

    /**
     * <p>Called when a real file already got opened once or multiple times (under different splitters) previously, and just got re-opened again,
     * which could occur either during project initialization or if triggered explicitly by user.</p>
     *
     * <p>If the real file got re-opened during project initialization, it means the project was closed with the file opened in multiple
     * splitters. In this case the real file will be closed and the associated light file passed in parameter will be opened instead under
     * the same splitter where the closed real file got originally opened. The end result will be the same light file opened under multiple
     * splitters / multiple editors, but all editors will be bound to the same unique document to keep the editors synced since they all represent one file.</p>
     *
     * <p>If the real file re-opening was triggered explicitly by the user, the re-opening won't be allowed, instead the real file
     * will get closed, and the associated light file (passed in parameter), which is already open, will get selected and receive
     * the focus regardless of which splitter it resides under.</p>
     *
     * @param fileEditorManager
     * @param file
     * @param associatedLightFileInfo
     */
    private void realFileReOpened(@NotNull FileEditorManager fileEditorManager, @NotNull VirtualFile file, OpenLightFileInfo associatedLightFileInfo) {
//        Editor editor = ((TextEditor) fileEditorManager.getSelectedEditor(file)).getEditor();
        utils.runInEDT(() -> {
            EditorWindow window = ((FileEditorManagerEx)fileEditorManager).getSplitters().getCurrentWindow();
            if(this.lastSelectedEditor != null && isProjectInitialized) {
                EditorWindow window1 = utils.windowForEditor(this.lastSelectedEditor, fileEditorManager);
                if(window1 != window) {
                    //different windows means different splitters => below is necessary in the following scenario:
                    //splitter1 has multiple tabs open and tab3 has focus initially, splitter2 contains associated light file to select instead of user triggered real file.
                    //when user double clicks to re-open real file, the real file gets briefly opened under splitter1 next to tab3,
                    //then it gets immediately closed (see code under if(isProjectInitialized)) to select associated light file instead.
                    //the closing of real file under splitter1 will cause the selection of tab1 (instead of the initial tab3) under splitter1,
                    //then associated light file under splitter2 will get selected and receive focus/caret.
                    //the code below ensures splitter1 re-selects tab3 (which was initially open under splitter1) without giving it the focus/caret,
                    //the focus should obviously remain under splitter2/associated light file
                    final VirtualFile lastSelected = this.lastSelectedFile;
                    fileEditorManager.openTextEditor(new OpenFileDescriptor(project, lastSelected), false);
                }
            }

            if(isProjectInitialized) {//triggered by user => don't re-open the file just select and focus the already opened editor
                //closing the real file must occur first or otherwise the associatedLightFile editor might lose the focus/caret at the end
                //here the opening is triggered by user => there's no chance the real file got opened in a new splitter => the real file is not alone in the splitter
                //=> closing the real file editor first is safe since it won't cause an empty splitter
                fileEditorManager.closeFile(file);
                fileEditorManager.openTextEditor(associatedLightFileInfo.lightFileDescriptor, true);
            } else {
                //triggered during project initialization => the real file can get opened in a new splitter => there's a chance the
                //the real file editor would be alone under its splitter => closing the real file editor first might result in an empty splitter, which
                //causes bug => opening the associated light file before closing the real file would prevent an empty splitter
                //=> reporting the closing of the real file is not an issue here (during initialization) since the caret/focus end position is not a priority
                ((FileEditorManagerImpl) fileEditorManager).openFileImpl2(window, associatedLightFileInfo.lightFileDescriptor.getFile(), false);

                fileEditorManager.closeFile(file);
            }
        }, !isProjectInitialized);
    }

    private OpenLightFileInfo infoForRealFile(VirtualFile realFile) {
        String realPath = realFile.getPath();
        OpenLightFileInfo info = realFileToLight.get(realPath);
        if(info != null) return info;

        //make sure the file name doesn't collide with other open file names
        int index = realPath.length();
        String name = null;
        while((index = realPath.lastIndexOf(File.separatorChar, index - 1)) != -1) {
            name = realPath.substring(index + 1);
            info = lightFileToReal.get(name);
            if(info == null) break;
        }
        if(name == null) name = realPath.substring(1);

        info = new OpenLightFileInfo(realFile, name);
        lightFileToReal.put(name, info);
        realFileToLight.put(realPath, info);
        return info;
    }

    @Override
    public void toggleANSIHighlighter(AnActionEvent e) {
        utils.runInEDT(() -> {
            VirtualFile file = e.getData(CommonDataKeys.VIRTUAL_FILE);
            if(!ANSIAwareFileType.isANSIColorable(file)) return;
            FileEditorManagerEx fem = FileEditorManagerEx.getInstanceEx(project);
            if(fem == null) return;
            EditorWindow window = fem.getSplitters().getOrCreateCurrentWindow(file);
            OpenLightFileInfo info = infoForFile(file);
            if(info == null || info.lightFileDescriptor == null) return;
            VirtualFile fileToOpen = VirtualFileManager.getInstance().getFileSystem(StandardFileSystems.FILE_PROTOCOL).findFileByPath(info.realFilePath);
            if(fileToOpen == null) return;
            if(!(file instanceof LightVirtualFile)) {
                fileToOpen = info.lightFileDescriptor.getFile();
            }

            isTogglingANSIHighlighter = true;
            ((FileEditorManagerImpl)fem).openFileImpl2(window, fileToOpen, true);
            window.closeFile(file);
        }, false);
    }

    @Override
    public void disposeComponent() {
        // TODO: insert component disposal logic here
    }

    @Override
    @NotNull
    public String getComponentName() {
        return "ANSIHighlighter";
    }

    @Override
    public void projectOpened() {
        // called when project is opened
    }

    @Override
    public void projectClosed() {
        // called when project is being closed
        String workspaceXmlPath = project.getWorkspaceFile().getCanonicalPath();
        new LightFileReferenceXMLUpdater().replaceMockFileReferencesInXML(workspaceXmlPath, lightFileToReal);
        System.out.println("project closed ");
    }
}