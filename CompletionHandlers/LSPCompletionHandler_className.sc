// Handle completions where the prefix looks like a class name, e.g.:
//   Class.metho
+LSPCompletionHandler {
    *classNameHandler {
        ^LSPCompletionHandler.prNew(
            name: "class_name",
            trigger: "",
            prefixHandler: { "" },
            action: {
                |prefixClass, trigger, completion, provideCompletionsFunc|
                var classNameMatch;
                completion = completion.stripWhiteSpace;
                // Strip leading non-identifier chars (e.g. opening paren)
                classNameMatch = completion.findRegexp("([A-Z][A-Za-z0-9_]*)$");
                if (classNameMatch.notEmpty) {
                    completion = classNameMatch[1][1];
                };
                provideCompletionsFunc.value(
                    LSPDatabase.findClasses(completion, 100),
                    true
                );
            }
        )
    }
}
