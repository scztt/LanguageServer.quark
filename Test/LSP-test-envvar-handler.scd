(
/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
UnitTest2("environmentVariableHandler", {
    |test|
    var handler = LSPCompletionHandler.environmentVariableHandler;
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("validatePrefix", {
        test.notNil(
            handler.validatePrefix("", "~"),
            "No prefix is okay"
        );
        
        test.notNil(
            handler.validatePrefix("something ", "~"),
            "prefix with space is okay"
        );
        
        test.isNil(
            handler.validatePrefix("    something", "~"),
            "Prefix without space is not okay"
        );
        
        test.isNil(
            handler.validatePrefix("", "."),
            "Invalid trigger"
        );
    });
    
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("handle", {
        ~variable1 = "test";
        ~variable2 = "test";
        ~valiable = "test";
        
        handler.handle(Meta_LSPCompletionHandler, "~", "var").then {
            |result|
            
            test.equals(
                result[\items].size,
                3,
                "Correct number of completions"
            );
            test.equals(
                result[\items][1][\filterText],
                "variable1"
            );
            
            test.equals(
                result[\items][1][\insertText],
                "variable1$0"
            )
        }
    })
}).runOrAdd
)