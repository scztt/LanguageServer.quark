// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration
GotoDeclarationProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/declaration",
        ]
    }
    *clientCapabilityName { ^"textDocument.declaration" }
    *serverCapabilityName { ^"declarationProvider" }
    
    init {
        |clientCapabilities|
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#declarationClientCapabilities
    }
    
    options {
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentSyncOptions
        ^()
    }
    
    onReceived {
        |method, params|
        var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
        var wordAtCursor = LSPDatabase.getDocumentWordAt(
            doc,
            params["position"]["line"],
            params["position"]["character"]
        );
        
        Log('LanguageServer.quark').info("Found word at cursor: %", wordAtCursor);
        
        ^nil
    }
}
