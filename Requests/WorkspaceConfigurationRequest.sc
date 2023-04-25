// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
WorkspaceConfiguration : LSPRequest {
    var sections, namespace="supercollider", clientOptions;
    
    *methodNames {
        ^[
            "workspace/configuration",
        ]
    }
    *clientCapabilityName { ^"workspace.configuration" }
    *serverCapabilityName { ^nil }
    
    init {
        |clientCapabilities|
        Log('LanguageServer.quark').info("initializing WorkspaceConfiguration");
        
        sections = [
            "sclang.evaluateResultPrefix",
            "languageServerLogLevel"
        ];
        clientOptions = ();
        
        fork {
            0.01.wait; // @TODO This fails if we fire it immediately on launch. What should we wait for?
            this.doRequest();
        }
    }
    
    doRequest {
        this.sendRequest((
            items: sections.collect {
                |section|
                (section: [namespace, section].join("."))
            }
        )).then({
            |options|
            Log('LanguageServer.quark').info("client options: %", options);
            
            clientOptions.clear();
            options.do {
                |option, index|
                clientOptions[sections[index].asSymbol] = option;
            };
            
            server.changed(\clientOptions, clientOptions);
        }).onError({
            |e|
            e.dumpBackTrace
        });
    }
    
    options {
        ^(
        )
    }
}


