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
        var code, deferred;
        
        switch (method)
            { 'shutdown' } {
                "*** STARTING SHUT DOWN ***".postln;
                Log('LanguageServer.quark').info("Preparing to shutdown");
                receivedShutdown = true;
                
                // SUBTLE: Calling thisProcess.shutdown kills network sockets, which breaks
                // our ability to call back to the LSP client. So we need to call everything
                // EXCEPT for socket disconnect stuff here, and then disconnect sockets later.
                ShutDown.run();
                Server.quitAll();
                Archive.write;
                
                {
                    "*** SERVER SHUTDOWN WAS LATE".postln;
                    1.exit;
                }.defer(2);
                
                "*** SHOWDOWN IMMINENT ***".postln;
                ^nil;
            }
            { 'exit' } {
                "*** EXITING ***".postln;
                Log('LanguageServer.quark').info("Exiting");
                File.closeAll;
                NetAddr.disconnectAll;
                {
                    receivedShutdown.if(0, 1).exit;
                }.defer(1);
                ^nil;
            }
    }
}
