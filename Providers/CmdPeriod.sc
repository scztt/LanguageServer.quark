// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
CmdPeriodProvider : LSPProvider {
	*methodNames {
		^[
			"workspace/cmdPeriod",
		]
	}
	*clientCapabilityName { ^nil }
	*serverCapabilityName { ^nil }

	handleRequest {
		CmdPeriod.run();
		^nil
	}
}
