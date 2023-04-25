// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
EvaluateProvider : LSPProvider {
    var resultPrefix="> ";
    var postResult=true;
    
    *methodNames {
        ^[
            "textDocument/evaluateSelection",
        ]
    }
    *clientCapabilityName { ^"textDocument.evaluation" }
    *serverCapabilityName { ^"evaluationProvider" }
    
    init {
        |clientCapabilities|
        server.addDependant({
            |server, message, value|
            if (message == \clientOptions) {
                resultPrefix = value['sclang.evaluateResultPrefix'] ?? {"> "};
                postResult = value['sclang.postEvaluateResults'] ? true;
            }
        })
    }
    
    options {
        ^()
    }
    
    onReceived {
        |method, params|
        var source, document, function, result, resultStream;
        
        source = params["sourceCode"];
        document = LSPDocument.findByQUuid(params["textDocument"]["uri"].urlDecode);
        
        thisProcess.nowExecutingPath = document.path;
        
        result = ();
        
        function = source.compile();
        if (function.isNil) {
            result[\compileError] = "Compile error?"
        } {
            try {
                resultStream = CollStream("");
                function.value().printOn(resultStream);
                result[\result] = resultStream.collection;
                
                if (postResult) {
                    resultPrefix.post;
                    resultStream.contents.postln;
                }
            } {
                |error|
                result[\error] = error.errorString;
                
                if (postResult) {
                    resultPrefix.post;
                    error.dumpBackTrace();
                }
            };
        };
        
        thisProcess.nowExecutingPath = nil;
        
        ^result
    }
}
