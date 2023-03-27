// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction
SelectionRangeProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/selectionRange",
		]
	}
	*clientCapabilityName { ^"textDocument.selectionRange" }
	*serverCapabilityName { ^"selectionRangeProvider" }

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
		var positions = params["positions"];
		var regions = LSPDatabase.getDocumentRegions(doc);

		^positions.collect {
			|position|
			position = (
				line: position["line"].asInteger,
				character: position["character"].asInteger,
			);
			(
				range: regions.detect({
					|region|
					(position[\line] >= region[\start][\line])
					and: { position[\line] <= region[\end][\line] }
				}) ?? {( 
					// empty
				)}
			)
		}
	}
}
