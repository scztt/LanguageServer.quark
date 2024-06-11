// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
EvaluateProvider : LSPProvider {
    classvar <>resultStringLimit = 2000;
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
        var source, document, function, result, resultString;
        
        source = params["sourceCode"];
        document = LSPDocument.findByQUuid(params["textDocument"]["uri"].urlDecode);
        
        result = ();
        
        thisProcess.interpreter.preProcessor !? { |pre| pre.value(source, thisProcess.interpreter) };
        function = source.compile();
        
        if (function.isNil) {
            result[\compileError] = "Compile error?"
        } {
            LSPConnection.handlerThread.next({
                thisProcess.nowExecutingPath = document.path;
                try {
                    resultString = String.streamContentsLimit({ 
                        |stream| 
                        function.value().printOn(stream); 
                    }, resultStringLimit);
                    
                    if (resultStringLimit.size >= resultStringLimit, { ^(resultString ++ "...etc..."); });
                    result[\result] = resultString;
                    
                    if (postResult) {
                        resultPrefix.post;
                        resultString.postln;
                    }
                } {
                    |error|
                    result[\error] = error.errorString;
                    
                    if (postResult) {
                        error.reportError
                    }
                };
                thisProcess.nowExecutingPath = nil;
            });
        };
        
        ^result
    }
}


