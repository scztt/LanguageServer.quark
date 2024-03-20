// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition
HoverProvider : LSPProvider {
    *methodNames {
        ^[
			"textDocument/hover",
        ]
    }
	*clientCapabilityName { ^"textDocument.hover" }
	*serverCapabilityName { ^"hoverProvider" }

    init {
        |clientCapabilities|
    }

    options {
        ^()
    }

    onReceived {
        |method, params|
        var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
        var wordAtCursor = LSPDatabase.getDocumentWordAt(
            doc,
            params["position"]["line"].asInteger,
            params["position"]["character"].asInteger
        );


		^(wordAtCursor !? { this.getHoverForWord(wordAtCursor.asSymbol) })
    }

	getHoverForWord {
        |word|
		var asClass;
		if (word.isClassName and: { (asClass = word.asClass).notNil })
		{
        		Log('LanguageServer.quark').info("Found class at cursor: %", word);
				^(
					contents: LSPDatabase.classDocString(asClass)
				)
		}
		{
        	Log('LanguageServer.quark').info("No class found at cursor: %", word);
			^nil
		}
	}
}
