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
				provideCompletionsFunc.value(
					LSPDatabase.findClasses(completion.stripWhiteSpace, 100),
					true
				);
			}
		)
	}
}
