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
        var line = params["position"]["line"].asInteger;
        var character = params["position"]["character"].asInteger;
        var wordAtCursor = LSPDatabase.getDocumentWordAt(doc, line, character);
        var lineString, isMethodCall, value;

        wordAtCursor ?? { ^nil };

        // Check if preceded by "." to distinguish method calls from bare words
        lineString = LSPDatabase.getDocumentLine(doc, line);
        isMethodCall = this.prHasDotPrefix(lineString, character);

        value = case
            { wordAtCursor[0] == $~ } {
                LSPDatabase.envVarHoverInfo(wordAtCursor[1..])
            }
            { wordAtCursor[0].isUpper } {
                wordAtCursor.asSymbol.asClass !? { |class|
                    LSPDatabase.defClassHoverInfo(class)
                    ?? { LSPDatabase.classHoverInfo(class) }
                }
            }
            { isMethodCall } {
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

    prHasDotPrefix {
        |lineString, character|
        var i = character;

        // Scan back to find word start
        while { (i > 0) and: {
            var ch = lineString[i - 1];
            ch.isAlphaNum or: { ch == $_ }
        }} {
            i = i - 1;
        };

        // Check the character before the word
        ^(i > 0) and: { lineString[i - 1] == $. }
    }
}
