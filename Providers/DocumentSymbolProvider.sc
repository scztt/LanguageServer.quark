// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol
DocumentSymbolProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/documentSymbol",
        ]
    }
    *clientCapabilityName { ^"textDocument.documentSymbol" }
    *serverCapabilityName { ^"documentSymbolProvider" }
    
    init {
        |clientCapabilities|
    }
    
    options {
        ^(
        )
    }
    
    onReceived {
        |method, params|
        var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
        
        if (params["textDocument"]["uri"].endsWith(".sc")) {
            ^nil
        } {
            if (params["textDocument"]["uri"].endsWith(".scd")) {
                ^LSPDatabase.getDocumentRegions(doc).collect {
                    |region|
                    (
                        name: region[\text],
                        kind: 0,
                        range: region[\range],
                        selectionRange: region[\range]
                    )
                }
            }
        }
        
        ^nil
    }
}
