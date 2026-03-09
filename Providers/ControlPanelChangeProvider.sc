// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
ControlPanelChangeProvider : LSPProvider {
    *methodNames {
        ^[
            "supercollider/controlPanelSetNormalized",
            "supercollider/controlPanelSetValue",
            "supercollider/controlPanelAction",
        ]
    }
    *clientCapabilityName { ^"supercollider.controlPanelChange" }
    *serverCapabilityName { ^"controlPanelChange" }
    
    init {
        |clientCapabilities|
    }
    
    options {
        ^()
    }
    
    onReceived {
        |method, params|
        var path, value;
        
        path = params["path"];
        value = params["value"];
        
        case
            { method == 'supercollider/controlPanelSetNormalized' } {
                Log('LanguageServer.quark').info("Control panel normalized changed: % % -> %", path, value);
                
                ControlPanelChangeProvider.changed(path.join("_").asSymbol, value.asFloat);
            }
            { method == 'supercollider/controlPanelSetValue' } {
                Log('LanguageServer.quark').info("Control panel value changed: % % -> %", path, value);
                
                ControlPanelChangeProvider.changed(path.join("_").asSymbol, value.asFloat);
            }
            { method == 'supercollider/controlPanelAction' } {
                Log('LanguageServer.quark').info("Control panel action: %", path);
                
                ControlPanelChangeProvider.changed(path.join("_").asSymbol);
            }
            
            ^nil
    }
}
