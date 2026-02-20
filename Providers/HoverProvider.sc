// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover
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
        var value;

        wordAtCursor ?? { ^nil };

        value = if (wordAtCursor[0].isUpper) {
            wordAtCursor.asSymbol.asClass !? { |class|
                LSPDatabase.classHoverInfo(class)
            }
        } {
            LSPDatabase.methodHoverInfo(wordAtCursor)
        };

        value ?? { ^nil };

        ^(
            contents: (
                kind: "markdown",
                value: value,
            )
        )
    }
}
