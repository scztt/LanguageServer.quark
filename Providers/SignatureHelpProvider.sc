// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp
SignatureHelpProvider : LSPProvider {
    *methodNames {
        ^[
            "textDocument/signatureHelp",
        ]
    }
    *clientCapabilityName { ^"textDocument.signatureHelp" }
    *serverCapabilityName { ^"signatureHelpProvider" }
    
    init {
        |clientCapabilities|
        SinOsc.ar()
    }
    
    options {
        ^(
            triggerCharacters: ["("],
            retriggerCharacters: [","]
        );
    }
    
    onReceived {
        |method, params|
        var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
        var position = (
            line: params["position"]["line"].asInteger,
            character: params["position"]["character"].asInteger,
        );
        var context = params["context"];
        var range = doc.charRangeForLine(position[\line]);
        var char = range[0] + position[\character];
        var methodName, commaCount, ownerClass;
        #methodName, ownerClass, commaCount = this.findMethodName(doc.string, char);
        
        if (methodName.notNil) {
            if (methodName[0].isUpper) {
                ownerClass = methodName !? _.asSymbol !? _.asClass !? _.class;
                
                methodName !? _.asSymbol !? _.asClass !? _.class
                    !? {
                        |classes|
                        classes = [classes] ++ classes.superclasses;
                        classes.do {
                            |c|
                            c.methods.detect({
                                |method|
                                method.name == \new
                            }) !? {
                                |method|    
                                ^(
                                    activeParameter: commaCount,
                                    signatures: [LSPDatabase.constructorSignature(method)]
                                )
                            }
                        }
                    };
                
                ^[]
            } {
                ^(
                    activeParameter: commaCount,
                    signatures: LSPDatabase.methodsForName(methodName.asSymbol)
                        !? {
                            |ms|
                            ownerClass = ownerClass !? _.asSymbol !? _.asClass !? _.class;
                            if (ownerClass.isNil) {
                                ms
                            } {
                                ms.select({ |m| m.ownerClass == ownerClass })
                            }
                        }
                        !? _.collect {
                            |method|
                            LSPDatabase.methodSignature(method)
                        }
                        ?? {[]}
                )
            }
        };
        
        ^nil
    }
    
    findMethodName {
        |string, end|
        var parenScope = 1;
        var commaCount = 0;
        var match, ch;
        
        Log('LanguageServer.quark').info("Finding method name before %", end);
        end.reverseDo {
            |i|
            ch = string[i];
            
            if (ch == $)) {
                parenScope = parenScope + 1
            };
            
            if (ch == $() {
                parenScope = parenScope - 1
            };
            
            if (parenScope == 1) {
                if (ch == $,) {
                    commaCount = commaCount + 1
                }
            };
            
            if (parenScope == 0) {
                var start = string.findBackwards("\n", true, i);
                start = start !? { start + 1 } ?? { 0 };
                match = string[start..i].findRegexp("\\b(([A-Z][A-Za-z0-9_]*)\\.)?([A-Za-z][a-zA-Z0-9_]*)\\($");
                if (match.size >= 2) {
                    ^[
                        match[3][1],
                        match[2][1],
                        commaCount
                    ]
                } {
                    ^[]
                }
            }
        };
        ^[nil, nil]
    }
}

