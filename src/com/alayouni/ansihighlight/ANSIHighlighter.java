package com.alayouni.ansihighlight;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.FoldingModel;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by alayouni on 5/12/17.
 */
public class ANSIHighlighter {
    private static final String ESC = "\u001B[";
    private static final char SEQ_END = 'm';
    private static final char SEQ_DELIM = ';';

    private static final int RESET = 0;

    private static final int BOLD = 1;
    private static final int ITALIC = 3;
    private static final List<Integer> fontCodeToType = new ArrayList<>();
    static {
        fontCodeToType.add(null);
        fontCodeToType.add(Font.BOLD);
        fontCodeToType.add(null);
        fontCodeToType.add(Font.ITALIC);
    }
    private static final Map<Integer, Integer> fontTypeToCode = new HashMap<>();
    static {
        fontTypeToCode.put(Font.BOLD, BOLD);
        fontTypeToCode.put(Font.ITALIC, ITALIC);
    }

    private static final int UNDERLINE = 4;

    private static final int BLACK = 30;
    private static final int RED	 = 31;
    private static final int GREEN = 32;
    private static final int YELLOW = 33;
    private static final int BLUE = 34;
    private static final int MAGENTA = 35;
    private static final int CYAN = 36;
    private static final int WHITE = 37;
    private static final List<Color> codeToForeground = new ArrayList<>();
    static {
        for(int i = 0; i < BLACK; i ++) {
            codeToForeground.add(null);
        }
        codeToForeground.add(Color.BLACK);
        codeToForeground.add(Color.RED);
        codeToForeground.add(Color.GREEN);
        codeToForeground.add(Color.YELLOW);
        codeToForeground.add(Color.BLUE);
        codeToForeground.add(Color.MAGENTA);
        codeToForeground.add(Color.CYAN);
        codeToForeground.add(Color.LIGHT_GRAY);
    }
    private static final Map<Color, Integer> foregroundToCode = new HashMap<>();
    static {
        for(int i = BLACK; i <= WHITE; i++) {
            foregroundToCode.put(codeToForeground.get(i), i);
        }
    }

    private static final int BLACK_BG = 40;
    private static final int RED_BG = 41;
    private static final int GREEN_BG = 42;
    private static final int YELLOW_BG = 43;
    private static final int BLUE_BG = 44;
    private static final int MAGENTA_BG = 45;
    private static final int CYAN_BG = 46;
    private static final int WHITE_BG = 47;
    //might be used in future to save marked documents in ANSI format
    private static final List<Color> codeToBackground = new ArrayList<>();
    static {
        for(int i = 0; i < BLACK_BG; i++) {
            codeToBackground.add(null);
        }
        codeToBackground.add(Color.BLACK);
        codeToBackground.add(Color.RED);
        codeToBackground.add(Color.GREEN);
        codeToBackground.add(Color.YELLOW);
        codeToBackground.add(Color.BLUE);
        codeToBackground.add(Color.MAGENTA);
        codeToBackground.add(Color.CYAN);
        codeToBackground.add(Color.LIGHT_GRAY);
    }
    //might be used in future to save marked documents in ANSI format
    private static final Map<Color, Integer> backgroundToCode = new HashMap<>();
    static {
        for(int i = BLACK_BG; i <= WHITE_BG; i++) {
            backgroundToCode.put(codeToBackground.get(i), i);
        }
    }

    private static final TextAttributes RESET_MARKER = new TextAttributes();

    private final HighlightManager highlighter;

    public ANSIHighlighter(Project project) {
        this.highlighter = HighlightManager.getInstance(project);
    }

    public void highlightANSISequences(Editor editor) {
        String text = editor.getDocument().getText();
        int escIndex = text.indexOf(ESC),
                startIndex = 0;
        if(escIndex == -1) return;
        Pair<TextAttributes, Integer> attr = null;
        TextAttributes attr0 = null;
        FoldingModel folder = editor.getFoldingModel();
        while (escIndex >= 0) {
            if(attr != null) attr0 = attr.first;
            attr = extractTextAttributesFromANSIEscapeSequence(text, escIndex);
            if(attr == null) {
                escIndex = text.indexOf(ESC, escIndex + 1);
            } else {
                if(attr0 != null) {
                    highlighter.addRangeHighlight(editor, startIndex, escIndex, attr0, false, null);
                }
                final int foldStart = escIndex,
                        foldEnd = attr.second;
                folder.runBatchFoldingOperation(()->{
                    folder.addFoldRegion(foldStart, foldEnd, "").setExpanded(false);
                }, true);
                startIndex = attr.second;
                if(startIndex == text.length()) break;
                attr0 = attr.first == RESET_MARKER ? null : attr.first;
                escIndex = text.indexOf(ESC, startIndex);
            }
            if(escIndex == -1 && attr0 != null) {
                highlighter.addRangeHighlight(editor, startIndex, text.length(), attr0, false, null);
            }
        }
    }


    private Pair<TextAttributes, Integer> extractTextAttributesFromANSIEscapeSequence(String text, int escIndex) {
        List<Integer> ansiCodes = new ArrayList<>();
        TextAttributes attr = new TextAttributes();
        int endIndex = -1, index, startIndex;
        int ansiCode;
        char c;
        root: while (escIndex < text.length() && text.startsWith(ESC, escIndex)) {
            ansiCodes.clear();
            startIndex = index = escIndex + ESC.length();
            do {
                while (index < text.length() && isDigit(text.charAt(index))) index++;
                if (index == text.length()) break root;
                c = text.charAt(index);
                if (c == SEQ_DELIM || c == SEQ_END) {
                    ansiCode = toAnsiCodeIfSupported(text.substring(startIndex, index));
                    if (ansiCode == -1) break root;
                    ansiCodes.add(ansiCode);
                    startIndex = ++index;
                    if (c == SEQ_END) {
                        endIndex = escIndex = index;
                        attr = applyCodes(ansiCodes, attr);
                    }
                } else break root;
            } while(c == SEQ_DELIM);
        }
        if(endIndex == -1) return null;
        return new Pair<>(attr, endIndex);
    }

    private TextAttributes applyCodes(List<Integer> ansiCodes, TextAttributes attr) {
        for(int code : ansiCodes) {
            attr = applySupportedCode(code, attr);
        }
        if(attr.getEffectType() == EffectType.LINE_UNDERSCORE || attr.getEffectType() == EffectType.BOLD_LINE_UNDERSCORE) {//if so this is not RESET_MARKER
            if((attr.getFontType() & Font.BOLD) == Font.BOLD) {
                attr.setEffectType(EffectType.BOLD_LINE_UNDERSCORE);
            } else {
                attr.setEffectType(EffectType.LINE_UNDERSCORE);
            }
            if(attr.getForegroundColor() != null) {
                attr.setEffectColor(attr.getForegroundColor());
            } else {
                attr.setEffectColor(Color.BLACK);
            }
        }
        return attr;
    }

    private TextAttributes applySupportedCode(int code, TextAttributes attr) {
        if(code == RESET) return RESET_MARKER;
        if(attr == RESET_MARKER) attr = new TextAttributes();
        if(code == UNDERLINE) attr.setEffectType(EffectType.LINE_UNDERSCORE);
        else {
            int fontType = fontTypeFromCode(code);
            if(fontType != -1) {
                attr.setFontType(attr.getFontType() | fontType);
            } else {
                Color foreground = foregroundFromCode(code);
                if(foreground != null) {
                    attr.setForegroundColor(foreground);
                } else {
                    Color background = backgroundFromCode(code);
                    attr.setBackgroundColor(background);
                }
            }
        }
        return attr;
    }

    private boolean isDigit(char c) {
        return c >= '0' && c <= '9';
    }

    private int toAnsiCodeIfSupported(String ansiCodeStr) {
        try {
            int code = Integer.parseInt(ansiCodeStr);
            return isAnsiCodeSupported(code) ? code : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private boolean isAnsiCodeSupported(int ansiCode) {
        if(ansiCode == RESET || ansiCode == UNDERLINE) return true;
        if(fontTypeFromCode(ansiCode) != -1) return true;
        if(foregroundFromCode(ansiCode) != null) return true;
        if(backgroundFromCode(ansiCode) != null) return true;
        return false;
    }

    private int fontTypeFromCode(int fontTypeCode) {
        if(fontTypeCode >= fontCodeToType.size()) return -1;
        Integer fontType = fontCodeToType.get(fontTypeCode);
        return fontType == null ? -1 : fontType;
    }

    private Color foregroundFromCode(int foregroundCode) {
        if(foregroundCode >= codeToForeground.size()) return null;
        return codeToForeground.get(foregroundCode);
    }

    private Color backgroundFromCode(int backgroundCode) {
        if(backgroundCode >= codeToBackground.size()) return null;
        return codeToBackground.get(backgroundCode);
    }

}
