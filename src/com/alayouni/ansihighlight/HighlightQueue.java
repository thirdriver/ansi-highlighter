package com.alayouni.ansihighlight;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldRegion;
import com.intellij.openapi.util.Key;

import java.util.List;

/**
 * Created by alayouni on 6/5/17.
 */
class HighlightQueue {
    private static Key<HighlightTaskData> EDITOR_HIGHLIGHT_TASK_DATA = Key.create("editor-highlight-task-data");

    private HighlightTaskData current;
    private int totalEstimatedWork = 0, doneWork = 0;

    private Application application = ApplicationManager.getApplication();

    public void removeEditorHighlightTaskIfQueed(Editor editor) {
        application.assertIsDispatchThread();
        HighlightTaskData task = editor.getUserData(EDITOR_HIGHLIGHT_TASK_DATA);
        if(task != null) removeTask(task);
    }

    public void removeTask(HighlightTaskData task) {
        application.assertIsDispatchThread();
        task.getEditor().putUserData(EDITOR_HIGHLIGHT_TASK_DATA, null);
        doneWork -= task.getFoldRegionsStart();
        totalEstimatedWork -= task.getFoldRegions().size();
        if(task == current) current = task.next() == task ? null : task.next();
        if(task.next() != task) {
            task.next().setPrevious(task.previous());
            task.previous().setNext(task.next());
        }
    }

    public void addNewTask(Editor editor, List<HighlightRangeData> highlights, List<FoldRegion> foldRegions) {
        application.assertIsDispatchThread();
        HighlightTaskData task = new HighlightTaskData(editor, highlights, foldRegions);
        editor.putUserData(EDITOR_HIGHLIGHT_TASK_DATA, task);
        if(current == null) {
            current = task;
            task.setNext(task);
            task.setPrevious(task);
        } else {
            current.next().setPrevious(task);
            task.setNext(current.next());
            current.setNext(task);
            task.setPrevious(current);
        }
        totalEstimatedWork += task.getFoldRegions().size();
    }

    public HighlightTaskData next() {
        application.assertIsDispatchThread();
        return current == null ? null : (current = current.next());
    }

    public void taskProcessedUpdateQueue(HighlightTaskData task) {
        application.assertIsDispatchThread();
        //estimated work must be calculated before call to task.taskProcessedUpdateData() since the call will update task.foldRegionsStart
        int estimatedWork = task.getFoldRegionsEnd() - task.getFoldRegionsStart();
        if(task.taskProcessedUpdateData()) {
            removeTask(task);
        } else {
            doneWork += estimatedWork;
        }
    }


    double getProgressFraction() {
        return doneWork/ (double)totalEstimatedWork;
    }

    public boolean isEmpty() {
        return current == null;
    }
}
