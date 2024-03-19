// Handle completions where the prefix looks like an environment variable, e.g.:
//   ~objec
+LSPCompletionHandler {
    *environmentVariableHandler {
        ^LSPCompletionHandler.prNew(
            name: "environment_variable",
            trigger: "~",
            prefixHandler: {
                |prefix|
                // @TODO improve regex / parsing?
                if (prefix.isEmpty || "\\W+?$".matchRegexp(prefix)) {
                    true
                } {
                    nil
                }
            },
            action: {
                |prefix, trigger, completion, provideCompletionsFunc|
                var results;
                
                // @TODO Matching from current environment - this is probably as good as we can do, right?
                results = currentEnvironment.keys;
                
                // @TODO Move dictionary constricture to LSPDatabase?
                results = results.asArray.collect({
                    |name|
                    var nameString = name.asString;
                    (
                        label: 			"~" ++ nameString,
                        insertText:		"%$0".format(nameString),
                        filterText:     nameString,
                        insertTextFormat: 2, // Snippet,
                        labelDetails:	(
                            detail: "   %".format(currentEnvironment[name]),
                        ),
                        kind: 			6 // CompletionItemKind.Variable
                    )
                });
                
                if (results.notEmpty) {
                    provideCompletionsFunc.(results, false);
                }
            }
        )
    }
}
