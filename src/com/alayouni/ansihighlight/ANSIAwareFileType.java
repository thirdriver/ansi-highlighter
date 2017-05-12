package com.alayouni.ansihighlight;

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Created by alayouni on 5/8/17.
 */
public class ANSIAwareFileType implements FileType {
    public static final Icon FILE = IconLoader.getIcon("/com/alayouni/ansihighlight/icons/console.png");

    private static final ANSIAwareFileType instance = new ANSIAwareFileType();

    private ANSIAwareFileType() {
    }

    @NotNull
    @Override
    public String getName() {
        return "ANSI Aware";
    }

    @NotNull
    @Override
    public String getDescription() {
        return "ANSI Aware";
    }

    @NotNull
    @Override
    public String getDefaultExtension() {
        return "log";
    }

    @Nullable
    @Override
    public Icon getIcon() {
        return FILE;
    }

    @Override
    public boolean isBinary() {
        return false;
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Nullable
    @Override
    public String getCharset(@NotNull VirtualFile virtualFile, @NotNull byte[] bytes) {
        return null;
    }

    public static ANSIAwareFileType getInstance() {
        return instance;
    }

    public static boolean isANSIColorable(VirtualFile file) {
        return file.getFileType() instanceof ANSIAwareFileType;
    }


}
