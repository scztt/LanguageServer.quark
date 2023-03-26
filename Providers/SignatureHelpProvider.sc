// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp
SignatureHelpProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/signatureHelp",
		]
	}
	*clientCapabilityName { ^"textDocument.signatureHelp" }
	*serverCapabilityName { ^"referencesProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^(
			triggerCharacters: ["("],
			retriggerCharacters: [","]
		)
	}

	onReceived {
		|method, params|
		Log('LanguageServer.quark').info("Handling: %", method);

		^nil;
	}
}
