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
        Log('LanguageServer.quark').info("Handling: %", method);
        
        ^nil;
    }
}
