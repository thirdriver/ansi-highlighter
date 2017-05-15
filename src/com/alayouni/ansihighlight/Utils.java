package com.alayouni.ansihighlight;

import com.intellij.conversion.CannotConvertException;
import com.intellij.conversion.ConversionContext;
import com.intellij.conversion.impl.ConversionContextImpl;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;

import javax.swing.*;
import java.io.*;
import java.util.Arrays;

/**
 * Created by alayouni on 5/2/17.
 */
public class Utils {

    private final Project project;
    private final ConversionContext projectConversionContext;

    public Utils(Project project) throws CannotConvertException {
        this.project = project;
        this.projectConversionContext = new ConversionContextImpl(project.getBaseDir().getPath());
    }

    public String collapsePath(VirtualFile file) {
        VirtualFile projectDir = project.getBaseDir();
        if(VfsUtilCore.isAncestor(projectDir, file, true)) {
            String collapsed = projectConversionContext.collapsePath(file.getPath());
            return collapsed;
        }
        VirtualFile commonAncestor = VfsUtilCore.getCommonAncestor(projectDir, file);
        String projectRelativePath = VfsUtilCore.getRelativePath(projectDir, commonAncestor);
        String fileConvertedPath = project.getBaseDir().getPath();
        if(!projectRelativePath.isEmpty()) {//if empty means file.equals(projectDir)
            String[] tokenReplacements = Arrays.stream(projectRelativePath.split(File.separator)).map((s) -> "..").toArray(String[]::new);
            String cancelProjectRelativePath = String.join(File.separator, tokenReplacements);
            String fileRelativeToCommonAncestorPath = VfsUtilCore.getRelativePath(file, commonAncestor);
            fileConvertedPath += (File.separatorChar + cancelProjectRelativePath + File.separatorChar + fileRelativeToCommonAncestorPath);
        }
        String collapsed = projectConversionContext.collapsePath(fileConvertedPath);
        return collapsed;
    }

    public EditorWindow windowForEditor(Editor editor, FileEditorManager fileEditorManager) {
        if(!(fileEditorManager instanceof FileEditorManagerImpl)) return null;
        FileEditorManagerImpl fem = (FileEditorManagerImpl)fileEditorManager;
        EditorWindow[] windows = fem.getWindows();
        for(EditorWindow window : windows) {
            JComponent editorComp = editor.getComponent();
            JComponent tabsComp = window.getTabbedPane().getComponent();
            if(tabsComp.isAncestorOf(editorComp)) return window;
        }
        return null;
    }

    public EditorWindow windowForFileEditor(FileEditor editor, FileEditorManager fileEditorManager) {
        Editor e;
        if(editor instanceof TextEditor) e = ((TextEditor)editor).getEditor();
        else if(editor instanceof Editor) e = (Editor)editor;
        else return null;
        return  windowForEditor(e, fileEditorManager);
    }


    public Editor getEditor(FileEditor fileEditor) {
        if(fileEditor instanceof TextEditor) {
            return ((TextEditor)fileEditor).getEditor();
        } else if(fileEditor instanceof Editor) {
            return (Editor)fileEditor;
        }
        return null;
    }

    public String readFile(VirtualFile virtualFile) {
        try {
            StringBuilder sb = new StringBuilder();
            //virtualFile.getInputStream() doesn't seem to get synced immediately => use FileInputStream instead
            InputStream in = new FileInputStream(virtualFile.getPath());
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
                sb.append('\n');
            }
            br.close();
            return sb.toString();
        } catch (IOException e) {
            return null;
        }
    }
}
