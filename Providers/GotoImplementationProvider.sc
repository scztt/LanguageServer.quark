// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
GotoImplementationProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/implementation",
        ]
    }
    *clientCapabilityName { ^"textDocument.implementation" }
    *serverCapabilityName { ^"implementationProvider" }
    
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
                
        ^(wordAtCursor !? { this.getDefinitionsForWord(wordAtCursor) })
    }
    
    getDefinitionsForWord {
        |word|
        ^LSPDatabase.findDefinitions(word.asSymbol)
    }
}
