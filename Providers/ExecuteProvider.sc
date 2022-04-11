// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
EvaluateProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/evaluateSelection",
		]
	}
	*clientCapabilityName { ^"textDocument.evaluation" }
	*serverCapabilityName { ^"evaluationProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^()
	}

	handleRequest {
		|method, params|
		var source, document, function, result, resultStream;

		source = params["sourceCode"];
		document = LSPDocument.findByQUuid(params["textDocument"]["uri"]);

		thisProcess.nowExecutingPath = document.path;

		result = ();

		function = source.compile();
		if (function.isNil) {
			result[\compileError] = "Compile error?"
		} {
			try {
				resultStream = CollStream("");
				function.value().printOn(resultStream);
				result[\result] = resultStream.collection;
			} {
				|error|
				result[\error] = error.errorString;
			};
		};

		thisProcess.nowExecutingPath = nil;

		^result
	}
}
