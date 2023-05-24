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

	onReceived {
		|method, params|
		var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
		^LSPDatabase.getDocumentRegions(doc).collect {
			|region|
			(
				range: region[\range],
				command: (
					title: "▶ Evaluate block",
					command: "supercollider.evaluateSelection",
					arguments: [region[\range]]
				)
			)
		}
	}
}
