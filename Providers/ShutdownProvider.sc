// https://microsoft.github.io/language-server-protocol/specifications/specification-current/#shutdown
ShutdownProvider : LSPProvider {
	var receivedShutdown = false;

	*methodNames {
		^[
			"shutdown",
			"exit"
		]
	}

	*clientCapabilityName { ^nil }
	*serverCapabilityName { ^nil }

	init {
		|clientCapabilities|
		// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#declarationClientCapabilities
	}

	options {
		// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocumentSyncOptions
		^()
	}

	handleRequest {
		|method, params|
		var code;
		Log('LanguageServer.quark').info("Handling: %", method);
		switch (method)
		{ 'shutdown' } {
			Log('LanguageServer.quark').info("Preparing to shutdown");
			receivedShutdown = true;
			^(result: "null", code: 0);
		}
		{ 'exit' } {
			code = receivedShutdown.if(0, 1);
			code.exit;
			^nil;
		}
	}
}
