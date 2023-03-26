// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
WorkspaceConfiguration : LSPRequest {
	*methodNames {
		^[
			"workspace/configuration",
		]
	}
	*clientCapabilityName { ^"textDocument.codeLens" }
	*serverCapabilityName { ^nil }

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
			|range|
			(
				range: range,
				command: (
					title: "▶ Evaluate block",
					command: "supercollider.evaluateSelection",
					arguments: [range]
				)
			)
		}
	}
}
