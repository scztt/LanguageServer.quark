// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
FindReferencesProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/references",
        ]
    }
    *clientCapabilityName { ^"textDocument.references" }
    *serverCapabilityName { ^"referencesProvider" }
    
    init {
        |clientCapabilities|
    }
    
    options {
        ^()
    }
    
    onReceived {
        |method, params|
        var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
        var wordAtCursor = LSPDatabase.getDocumentWordAt(
            doc,
            params["position"]["line"].asInteger,
            params["position"]["character"].asInteger
        );

        Log('LanguageServer.quark').info("Found word at cursor: %", wordAtCursor);
        
        ^(wordAtCursor !? { LSPDatabase.getReferences(wordAtCursor) } ?? {[]})
    }
}
