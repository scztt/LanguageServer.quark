// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
CodeLensProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/codeLens",
		]
	}
	*clientCapabilityName { ^"textDocument.codeLens" }
	*serverCapabilityName { ^"codeLensProvider" }

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
		doc.string.postln;
		^LSPDatabase.getDocumentRegions(doc).collect {
			|range|
			(
				range: range,
				command: (
					title: "▶ Evaluate",
					command: "supercollider.executeSelection",
					arguments: [range]
				)
			)
		}
	}
}
