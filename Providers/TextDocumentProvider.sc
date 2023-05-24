// https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize
TextDocumentProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/didOpen",
            "textDocument/didChange",
            "textDocument/didClose",
            "textDocument/didSave",
        ]
    }
    *clientCapabilityName { ^"textDocument.synchronization" }
    *serverCapabilityName { ^"textDocumentSync" }
    
    init {
    }
    
    options {
        // https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentSyncOptions
        ^(
            openClose: true,
            change: 2, // Incremental
            save: true
        )
    }
    
    onReceived {
        |method, params|
        Log('LanguageServer.quark').info("Handling: %", method);
        
        switch(
            method,
            
            'textDocument/didChange', {
                this.didChange(
                    params["textDocument"]["uri"],
                    params["textDocument"]["version"],
                    params["contentChanges"]
                )
            },
            'textDocument/didOpen', {
                this.didOpen(
                    uri: 		params["textDocument"]["uri"],
                    languageId: params["textDocument"]["languageId"],
                    version:	params["textDocument"]["version"].asInteger,
                    text:		params["textDocument"]["text"],
                );
            },
            'textDocument/didClose', {
                this.didClose(
                    uri: params["textDocument"]["uri"]
                )
            },
            'textDocument/didSave', {
                this.didSave(
                    uri: params["textDocument"]["uri"]
                )
            },
            {
                Error("Couldn't handle method: %".format(method)).throw
            }
        );
        
        ^nil
    }
    
    didOpen {
        |uri, languageId, version, text|
        LSPDocument.findByQUuid(uri).initFromLSP(
            languageId,
            version,
            text
        ).isOpen_(true);
    }
    
    didClose {
        |uri|
        LSPDocument.findByQUuid(uri).isOpen_(false)
    }
    
    didSave {
        |uri|
        LSPDocument.findByQUuid(uri).documentWasSaved();
    }
    
    didChange {
        |uri, version, changes|
        var doc = LSPDocument.findByQUuid(uri);
        var range;
        
        if (doc.isOpen.not) {
            Exception("Changing an LSPDocument(%) that is not open - something is wrong...".format(uri)).throw;
        };
        
        changes = changes.collect {
            |change|
            if (change["range"].notNil) {
                range = change["range"];
                LSPDocumentChange(
                    range["start"]["line"].asInteger,
                    range["start"]["character"].asInteger,
                    range["end"]["line"].asInteger,
                    range["end"]["character"].asInteger,
                    change["text"]
                )
            } {
                LSPDocumentChange.wholeDocument(change["text"])
            }
        };
        
        changes.do(doc.applyChange(version, _));
    }
}
