// https://microsoft.github.io/language-server-protocol/specifications/specification-current/#workspace_symbol
WorkspaceSymbolProvider : LSPProvider {
	*methodNames {
		^[
			"workspace/symbol"
		]
	}
	*clientCapabilityName { ^"workspace.symbol" }
	*serverCapabilityName { ^"workspaceSymbolProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^()
	}

	onReceived {
		|method, params|
		var query = params["query"];

		^LSPDatabase.findSymbols(query, 100);
	}
}

// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_configuration
WorkspaceConfigurationProvider : LSPProvider {
	*methodNames {
		^[
			"workspace/configuration"
		]
	}
	*clientCapabilityName { ^"workspace.configuration" }
	*serverCapabilityName { ^nil }

	init {
		|clientCapabilities|
	}

	options {
		^()
	}

	onReceived {
		|method, params|
		var query = params["query"];

		^LSPDatabase.findSymbols(query, 100);
	}
}
