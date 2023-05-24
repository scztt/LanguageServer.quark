// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
FoldingRangeProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/foldingRange",
		]
	}
	*clientCapabilityName { ^"textDocument.foldingRange" }
	*serverCapabilityName { ^"foldingRangeProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^(
		)
	}

	onReceived {
		|method, params|
		var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
		^LSPDatabase.getDocumentRegions(doc).collect {
			|region|
			(
				kind: 			"region",

				startLine: 		region[\range][\start][\line],
				startCharacter: region[\range][\start][\character],
				endLine: 		region[\range][\end][\line],
				endCharacter:	region[\range][\end][\character],
			)
		}
	}
}
