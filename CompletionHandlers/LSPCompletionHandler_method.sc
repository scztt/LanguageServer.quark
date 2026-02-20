// Handle completions where the prefix looks likeregular method call syntax, e.g.:
//   object.metho
+LSPCompletionHandler {
    *methodHandler {
        ^LSPCompletionHandler.prNew(
            name: "method",
            trigger: ".",
            prefixHandler: {
                |prefix|
                // @TODO improve regex / parsing ...
                var prefixString = prefix.findRegexp("(?:^|[\\s,;=+*/%(){}])([~0-9a-z][\\w\\.]*)$");

                if (prefixString.notEmpty) {
                    prefixString = prefixString[1][1];
                    // Reject trailing dot (double-dot case, e.g. "variable..")
                    if (prefixString.last == $.) {
                        nil
                    } {
                        prefixString.stripWhiteSpace
                    };
                    // @TODO If prefix is environment var, only show completions for that type?
                } {
                    nil
                }
            },
            action: {
                |prefixString, trigger, completion, provideCompletionsFunc|
                var methodIndexRange, methods, results, isIncomplete;
                
                // @TODO should just return methods themselves, not indexes?
                methodIndexRange = LSPDatabase.matchMethods(completion);
                
                Log('LanguageServer.quark').info("Found % completions, returning %", methodIndexRange.size, min(100, methodIndexRange.size));
                
                // @TODO Make 100 into a "maxCompletions" option somewhere?
                isIncomplete = (methodIndexRange.size > 100);
                methodIndexRange.size = min(100, methodIndexRange.size);
                
                results = Array(methodIndexRange.size);
                
                methodIndexRange.collect {
                    |index|
                    var method = LSPDatabase.allMethods[index];
                    results = results.add(LSPDatabase.makeMethodCompletion(method))
                };
                
                provideCompletionsFunc.(results, isIncomplete)
            }
        )
    }
}
