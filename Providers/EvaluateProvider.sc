// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
EvaluateProvider : LSPProvider {
    classvar 
        <>resultStringLimit = 2000, 
        <>sourceCodeLineLimit=6,
        <>skipErrorConstructors=true;
    var resultPrefix="> ";
    var postResult=true, improvedErrorReports=false;
    var <>postBeforeEvaluate="", <>postAfterEvaluate="";
    
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
                postResult = value['sclang.postEvaluateResults'] !? (_ == "true") ?? true;
                improvedErrorReports = value['sclang.improvedErrorReports'] !? (_ == "true") ?? false;
            }
        })
    }
    
    options {
        ^()
    }
    
    *evaluateMethod {
        ^this.methods.detect({ |m| m.name === \doEvaluate })
    }
    
    doEvaluate {
        |func|
        var result = func.value();
        ^result;
    }
    
    onReceived {
        |method, params|
        var source, document, function, result, deferredResult;
        
        source = params["sourceCode"];
        document = LSPDocument.findByQUuid(params["textDocument"]["uri"].urlDecode);
        
        deferredResult = Deferred();
        
        thisProcess.interpreter.preProcessor !? { |pre| pre.value(source, thisProcess.interpreter) };
        function = source.compile();
        
        this.postBeforeEvaluate.value.postln;
        
        if (function.isNil) {
            deferredResult.value = (compileError: "Compile error?");
        } {
            thisProcess.nowExecutingPath = document.path;
            
            try {
                result = this.doEvaluate(function);
                
                result = String.streamContentsLimit({ 
                    |stream| 
                    result.printOn(stream); 
                }, resultStringLimit);
                
                if (resultStringLimit.size >= resultStringLimit, { ^(result ++ "...etc..."); });
                if (postResult) {
                    resultPrefix.post;
                    result.postln;
                };
                deferredResult.value = (result: result);
            } {
                |error|
                if (postResult) {
                    if (improvedErrorReports) {
                        error.postEvaluateBacktrace(this.class.evaluateMethod, error)
                    } {
                        error.reportError();
                    }
                };
                deferredResult.value = (error: error.errorString);
            };
            
            thisProcess.nowExecutingPath = nil;             
        };
        
        this.postAfterEvaluate.value.postln;
        
        ^deferredResult
    }
}

+Exception {
    formatSource {
        |out, source, indent|
        var minWhiteSpace = 9999, shortenedLines=0;
        source = source.replace("\t", "    ");
        source = source.split(Char.nl);
        source.do {
            |s|
            s.findRegexp("^\\s+")[0] !? {
                |found|
                minWhiteSpace = min(minWhiteSpace, found[1].size);
            } ?? { 
                minWhiteSpace = 0;
            }
        };
        
        if (source.size > EvaluateProvider.sourceCodeLineLimit) {
            shortenedLines = source.size - EvaluateProvider.sourceCodeLineLimit;
            source = source[0..(EvaluateProvider.sourceCodeLineLimit - 1)];
        };
        
        out << indent << "╭───" << Char.nl;
        source.do {
            |s|
            out << indent << "│ " << s[minWhiteSpace..] << Char.nl;
        };
        out << indent << "╰" << if(shortenedLines > 0) { "╌╌╌ (% more lines)".format(shortenedLines) } {"───"}
            << Char.nl;
    }
    
    postEvaluateBacktrace {
        |rootFunction, error|
        var out, currentFrame, def, ownerClass, methodName, pos, tempStr, skipped=0;
        out = CollStream.new;
        
        "\nPROTECTED CALL STACK:".postln;
        currentFrame = protectedBacktrace;
        while { currentFrame.notNil 
            and: { this.skippable(currentFrame) }
            and: { this.skippable(currentFrame.caller) }
        } {
            skipped = skipped + 1;
            currentFrame = currentFrame.caller;
        };
        
        if (skipped > 0) {
            out << "\t" 
                << "(skipped % stack frames - set `EvaluateProvider.skipErrorConstructors = false` to see these)".format(
                    skipped
                )
                << Char.nl << Char.nl;
        };
        
        while { currentFrame.notNil and: { 
            currentFrame.functionDef != rootFunction 
        }} {
            
            def = currentFrame.functionDef;
            
            if (def.isKindOf(Method)) {
                ownerClass = def.ownerClass;
                methodName = def.name;
                
                if ((ownerClass == Function) and: { #['protect', 'try'].includes(methodName) }) {
                    pos = out.pos;
                };
                
                if (ownerClass.isKindOf(Error)) {
                    
                };
                
                out << "\t%:%\t".format(ownerClass, methodName).padRight(30)
                    << "(file://%)".format(def.filenameSymbol)
                    << Char.nl;
            } {
                out << "\ta FunctionDef\t%\n".format(currentFrame.address);
                def.sourceCode !? {
                    this.formatSource(out, def.sourceCode, "\t\t")
                } ?? {
                    out << "<an open Function>"
                };
            };
            
            def.argNames.do {
                |name, i|
                
                out << "\t" 
                    << (i == 0).if("\targ ", "\t    ")
                    << "% = %".format(name, currentFrame.args[i])
                    << Char.nl;
            };
            
            def.varNames.do {
                |name, i|
                
                out << "\t"
                    << (i == 0).if("\tvar ", "\t    ")
                    << "% = %".format(name, currentFrame.vars[i])
                    << Char.nl;
            };
            
            currentFrame = currentFrame.caller;
        };
        
        // lose everything after the last Function:protect
        // it just duplicates the normal stack with less info
        // but, an Error in a routine in a Scheduler
        // may not have a try/protect in the protectedBacktrace
        // then, pos is nil and we should print everything
        postln(
            if(pos.notNil) {
                out.collection.copyFromStart(pos)
            } {
                out.collection
            }
        );
        
        this.errorString.postln;
    }
    
    skippable {
        |frame|
        ^(
            EvaluateProvider.skipErrorConstructors
                and: {
                    frame.functionDef.ownerClass.nonMetaClass.isSubclassOf(Error)
                }
                and: {
                    frame.functionDef.name == \new
                }
        )
    }
}

+Class {
    nonMetaClass {
        ^this.name.asString.replace("Meta_", "").asSymbol.asClass
    }
    
    isSubclassOf {
        |other|
        var superclass = this;
        while { superclass.notNil } {
            if (superclass == other) {
                ^true
            };
            superclass = superclass.superclass;
        };
        
        ^false
    }
}
