// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
DocumentationSearchProvider : LSPProvider {
	*methodNames {
		^[
			"documentation/search",
		]
	}
	*clientCapabilityName { ^nil }
	*serverCapabilityName { ^nil }

	init {
		|clientCapabilities|
		try {
			SCDoc.indexAllDocuments
		} {}
	}

	options {
		^(
		)
	}

	onReceived {
		|method, params|
		^(
			uri: params["searchString"].findHelpFile;
		)
	}
}
