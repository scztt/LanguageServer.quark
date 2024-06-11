// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting
TextDocumentFormattingProvider : LSPProvider {
    classvar <>formatter, <>formatterEnabled=false;
    
    *methodNames { 
        ^["textDocument/formatting"] 
    }
    
    *clientCapabilityName { ^"textDocument.formatting" }
    *serverCapabilityName { ^"documentFormattingProvider" }
    
    *initClass {
        StartUp.add {
            if (formatterEnabled) {
                formatter = CodeFormatter();
            }
        }
    }
    
    init {
        |clientCapabilities|
    }
    
    options {
        if (formatterEnabled) {
            ^(
            )
        } {
            ^nil
        }
    }
    
    onReceived {
        |method, params|
        Log('LanguageServer.quark').info("Handling: %", method);
        
        if (formatterEnabled.not) { ^nil };
        
        switch(
            method,
            'textDocument/formatting', {
                ^this.class.format(
                    uri: 			params["textDocument"]["uri"],
                    tabSize:		params["options"]["tabSize"].asInteger,
                    insertSpaces: 	params["options"]["insertSpaces"] == "true"
                )
            },
            
            {
                Error("Couldn't handle method: %".format(method)).throw
            }
        );
        
        ^nil
    }
    
    *format {
        |uri, tabSize, insertSpaces|
        var doc = LSPDocument.findByQUuid(uri);
        var text = doc.text;
        
        if (formatter.isRunning.not or: {
            (tabSize != formatter.tabSize) || (insertSpaces != formatter.insertSpaces)
        }) {
            // Log('LanguageServer.quark').info("Restarting formatter with new settings: tabSize=%->% insertSpaces=%->%".format(
            // 	formatter.tabSize, tabSize,
            // 	formatter.insertSpaces, insertSpaces
            // ));
            // formatter.free();
            // formatter = CodeFormatter(tabSize, insertSpaces)
        };
        
        text = formatter.format(text);
        Log('LanguageServer.quark').info("Reformatting to:\n%", text);
        
        ^[(
            range: (
                start: ( line: 0, character: 0 ),
                end: ( line: 99999, character: 0 )
            ),
            newText: text
        )]
    }
}

// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting
TextDocumentTypeFormattingProvider : LSPProvider {
    *methodNames { ^["textDocument/onTypeFormatting"] }
        *clientCapabilityName { ^"textDocument.onTypeFormatting" }
        *serverCapabilityName { ^"documentOnTypeFormattingProvider" }
        
        init {
            |clientCapabilities|
        }
        
        options {
            ^(
                firstTriggerCharacter: "\n",
                moreTriggerCharacter: [
                    ")", "}", "|", ";"
                ]
            )
        }
        
        onReceived {
            |method, params|
            Log('LanguageServer.quark').info("Handling: %", method);
            
            if (TextDocumentFormattingProvider.formatterEnabled.not) { ^nil };
            
            switch(
                method,
                'textDocument/onTypeFormatting', {
                    ^this.class.format(
                        uri: 			params["textDocument"]["uri"],
                        tabSize:		params["options"]["tabSize"].asInteger,
                        insertSpaces: 	params["options"]["insertSpaces"] == "true"
                    )
                },
                
                {
                    Error("Couldn't handle method: %".format(method)).throw
                }
            );
            
            ^nil
        }
        
        *format {
            |uri, tabSize, insertSpaces|
            ^TextDocumentFormattingProvider.format(uri, tabSize, insertSpaces)
        }
}
