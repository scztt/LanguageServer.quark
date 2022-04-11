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

	handleRequest {
		|method, params|
		var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
		^LSPDatabase.getDocumentRegions(doc).collect {
			|range|
			(
				startLine: 	range[\start][\line],
				endLine: 	range[\end][\line],
				kind: 		"region"
			)
		}
	}
}
