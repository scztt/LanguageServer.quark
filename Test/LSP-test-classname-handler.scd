/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
UnitTest2("classNameHandler", {
    |test|
    var handler = LSPCompletionHandler.classNameHandler;
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    handler.handle(Meta_LSPCompletionHandler, "", "LSPCompleti").then {
        |result|
        
        result.postln;
        
        test.equals(
            result[\items].size,
            1,
            "Correct number of completions"
        );
    };
    
    handler.handle(Meta_LSPCompletionHandler, "", "(LSPCompleti").then {
        |result|
        
        result.postln;
        
        test.equals(
            result[\items].size,
            1,
            "Correct number of completions"
        );
    };
    
    
}).runOrAdd