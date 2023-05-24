// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#window_showDocument
ShowDocument : LSPRequest {
    var sections, namespace="supercollider", clientOptions;
    
    *methodNames {
        ^[
            "window/showDocument",
        ]
    }
    *clientCapabilityName { ^"window.showDocument" }
    *serverCapabilityName { ^nil }
    
    init {
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


