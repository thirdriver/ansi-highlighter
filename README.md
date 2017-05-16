This IntelliJ plugin supports the rendering of the most common <a href="https://en.wikipedia.org/wiki/ANSI_escape_code#graphics">ANSI graphic rendition escape sequences</a> under IntelliJ editor. The following features are available:
      <ul>
      <li>Configurable 'ANSI Aware' file extensions:
        <ul>
        <li>Go to Preferences | Editor | File Types</li>
        <li>Under 'Recognized File Types' select 'ANSI Aware'</li>
        <li>Under 'Registered Patterns' add your custom 'ANSI Aware' file name patterns, *.log is added by default</li>
        <li>Don't forget to hit the 'Apply' button</li>
        </ul></li>
      <li>Switch between Preview and Edit mode:
       <ul>
       <li>Right click on the editor</li>
       <li>Press 'Switch to Edit/Preview Mode'</li>
       <li>Alternatively use the shortcut 'ctrl meta A' while on the editor</li>
       </ul></li>
      <li>Only the below graphic rendition codes are supported:
        <ul>
        <li>Reset code (0)</li>
        <li>Bold or increased intensity code (1)</li>
        <li>Italic code (3)</li>
        <li>Single Underline code (4)</li>
        <li>All text foreground color codes (30-37)</li>
        <li>All text background color codes (40-47)</li>
        </ul>
      </li>

Note that proper rendering of ANSI escape sequences involves hiding ANSI markup text. The only way to hide text in IntelliJ editor (text folding aside) is by altering original text, which if loaded directly into the editor, would modify the backing file content. Altering the backing file content would obviously cause the loss of all ANSI escape sequences. To workaround this, the plugin implementation loads the altered text into an in-memory file everytime an ANSI aware file type is opened in the editor, a FileEditorManagerListener is used for this purpose. The workaround then is to close the original editor and replace it by an in-memory file backed editor for preview. Further work is then needed to keep the in-memory editor synced to eventual changes brought to original in-disk file, further UI related work is also necessary.
