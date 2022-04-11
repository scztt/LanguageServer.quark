// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
CodeActionProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/codeAction",
		]
	}
	*clientCapabilityName { ^"textDocument.codeAction" }
	*serverCapabilityName { ^"codeActionProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^(
		)
	}

	handleRequest {
		|method, params|
		Log('LanguageServer.quark').info("Handling: %", method);
		^nil
	}
}
