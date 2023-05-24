// https://microsoft.github.io/language-server-protocol/specifications/specification-current/#shutdown
ShutdownProvider : LSPProvider {
    var receivedShutdown = false;
    
    *methodNames {
        ^[
            "shutdown",
            "exit"
        ]
    }
    
    *clientCapabilityName { ^nil }
    *serverCapabilityName { ^nil }
    
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
        var code;
        
        switch (method)
            { 'shutdown' } {
                Log('LanguageServer.quark').info("Preparing to shutdown");
                receivedShutdown = true;
                Server.killAll;
                {
                    Log('LanguageServer.quark').info("We're still alive..... killing.");
                    1.exit;
                }.defer(2);
                
                ^nil
            }
            { 'exit' } {
                Log('LanguageServer.quark').info("Exiting");
                receivedShutdown.if(0, 1).exit;
                ^nil;
            }
    }
}