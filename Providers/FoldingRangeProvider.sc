// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
FoldingRangeProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/foldingRange",
        ]
    }
    *clientCapabilityName { ^"textDocument.foldingRange" }
    *serverCapabilityName { ^"foldingRangeProvider" }
    
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
        ^LSPDatabase.getDocumentRegions(doc).collect {
            |range|
            (
                kind: 			"region",
                
                startLine: 		range[\start][\line],
                startCharacter: range[\start][\character],
                endLine: 		range[\end][\line],
                endCharacter:	range[\end][\character],
            )
        }
    }
}
