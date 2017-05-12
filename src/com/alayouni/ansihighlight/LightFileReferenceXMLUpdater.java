package com.alayouni.ansihighlight;

import org.jdom.Attribute;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.File;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;

/**
 * Created by alayouni on 5/5/17.
 */
public class LightFileReferenceXMLUpdater {

    private static final String COMPONENT_ELEMENT_NAME = "component";
    private static final String COMPONENT_NAME_ATTRIBUTE = "name";
    private static final String FILE_EDITOR_MANAGER_COMPONENT_NAME = "FileEditorManager";

    private static final String FILE_NODE_NAME = "file";
    private static final String FILE_LEAF_FILE_NAME_ATTR = "leaf-file-name";

    private static final String ENTRY_NODE_NAME = "entry";
    private static final String ENTRY_FILE_ATTR = "file";

    private static final String MOCK_PROTOCOL = "mock://";


    public void replaceMockFileReferencesInXML(String workspaceXmlPath, Map<String, ANSIHighlighterComponent.OpenLightFileInfo> mockToReal) {
        try {
            SAXBuilder saxBuilder = new SAXBuilder();
            Document document = saxBuilder.build(new File(workspaceXmlPath));
            Element project = document.getRootElement(),
                    editorManager = getFileEditorManagerComponent(project);
            if(editorManager == null) return;
            processAllMockFileReferencesUnder(editorManager, mockToReal);

            XMLOutputter xmlOutput = new XMLOutputter();
            // display xml
            xmlOutput.setFormat(Format.getPrettyFormat());
            PrintStream ps = new PrintStream(workspaceXmlPath);
            xmlOutput.output(document, ps);
            ps.close();
        } catch (Throwable e) {
            e.printStackTrace();
        }

    }

    private Element getFileEditorManagerComponent(Element root) {
        List<Element> components = root.getChildren(COMPONENT_ELEMENT_NAME);
        if(components == null) return null;

        for(Element c : components) {
            if(!c.getName().equals(COMPONENT_ELEMENT_NAME)) continue;
            String compName = getAttrValue(c, COMPONENT_NAME_ATTRIBUTE);
            if(compName != null && compName.equals(FILE_EDITOR_MANAGER_COMPONENT_NAME)) return c;
        }
        return null;
    }


    private void processAllMockFileReferencesUnder(Element parent, Map<String, ANSIHighlighterComponent.OpenLightFileInfo> mockToReal) {
        List<Element> children = parent.getChildren();
        if(children == null) return;
        for1: for(Element c : children) {
            if(c.getName().equals(FILE_NODE_NAME)) {
                String fileName = getAttrValue(c, FILE_LEAF_FILE_NAME_ATTR);
                if(fileName != null) {
                    List<Element> entryNodes = c.getChildren(ENTRY_NODE_NAME);
                    if(entryNodes != null && entryNodes.size() == 1)
                        replaceMpckReferenceIfApplicable(entryNodes.get(0), fileName, mockToReal);
                    continue for1;
                }
            }
            processAllMockFileReferencesUnder(c, mockToReal);
        }
    }

    private void replaceMpckReferenceIfApplicable(Element entryNode, String mockFileName, Map<String, ANSIHighlighterComponent.OpenLightFileInfo> mockToReal) {
        String filePath = getAttrValue(entryNode, ENTRY_FILE_ATTR);
        if(filePath == null || !filePath.startsWith(MOCK_PROTOCOL)) return;
        ANSIHighlighterComponent.OpenLightFileInfo info = mockToReal.get(mockFileName);
        if(info == null) return;
        Attribute pathAttr = entryNode.getAttribute(ENTRY_FILE_ATTR);
        pathAttr.setValue("file://" + info.collapsedRealFilePath);
    }

    private String getAttrValue(Element node, String attributeName) {
        Attribute attr = node.getAttribute(attributeName);
        return attr == null ? null : attr.getValue();
    }
}
