// Handle completions where the prefix looks like an sclang Singleton / global object, e.g.:
//   Ndef(\name)
//   Pdef(\name)
//   MIDIdef(\name)

+LSPCompletionHandler {
    *singletonHandler {
        ^LSPCompletionHandler.prNew(
            name: "singleton",
            trigger: "(",
            prefixHandler: {
                |prefix|
                var prefixClass = prefix.findRegexp("[^\\w-]*([A-Z]\\w*)$");
                
                Log('LanguageServer.quark').info("prefix: %, prefixClass: %", prefix, prefixClass);
                
                if (prefixClass.notEmpty) {
                    prefixClass = prefixClass[1][1];
                    if ((prefixClass = prefixClass.asSymbol.asClass).notNil and: {
                        prefixClass.isDefClass
                    }) {
                        prefixClass
                    } {
                        nil
                    }
                } {
                    nil
                }
            },
            action: {
                |prefixClass, trigger, completion, provideCompletionsFunc|
                var results, defNames;
                
                defNames = prefixClass.prGetNames.asArray;
                
                results = defNames.collectAs({
                    |name|
                    var shouldQuote = matchRegexp("\\\\W", name.asString);
                    
                    shouldQuote.if({
                        name = "'" ++ name ++ "'"
                    }, {
                        name = "\\" ++ name
                    });
                    
                    name = name.asString;
                    (
                        label: 			name,
                        insertText:		"%$0".format(name),
                        insertTextFormat: 2, // Snippet,
                        labelDetails:	(
                            detail: "  %(%)".format(prefixClass.name, name),
                        ),
                        kind: 			6 // CompletionItemKind.Variable
                    )
                }, Array);
                
                provideCompletionsFunc.value(results, false);
            }
        )
    }
}

+Object  {
    *isDefClass { ^false }
        *prGetNames { ^this.all !? _.keys ?? {[]} } // this is valid for MOST def classes....
}

+Ndef {
    *isDefClass { ^true }
        *prGetNames {
            var proxyspace = this.all[Server.default.name];
            ^if(proxyspace.notNil) {
                proxyspace.keys
            } {
                Set.new
            }
        } // @TODO Search across all servers here?
}

+Pdef { *isDefClass { ^true } }
+Tdef { *isDefClass { ^true } }
+Fdef { *isDefClass { ^true } }
+HIDdef { *isDefClass { ^true } }
+MIDIdef { *isDefClass { ^true } }
+OSCdef { *isDefClass { ^true } }
