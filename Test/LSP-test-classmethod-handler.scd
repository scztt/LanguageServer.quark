/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
(
UnitTest2("classMethodHandler", {
    |test|
    var handler = LSPCompletionHandler.classMethodHandler;
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("validatePrefix", {
        test.equals(
            handler.validatePrefix("IdentityDictionary", "."), Meta_IdentityDictionary,
            "Valid classname"
        );
        test.equals(
            handler.validatePrefix("    IdentityDictionary", "."), Meta_IdentityDictionary,
            "Valid classname with space"
        );
        
        test.equals(
            handler.validatePrefix("    IdentityFictionary", "."), nil,
            "Valid classname but not a class"
        );
        
        test.equals(
            handler.validatePrefix("    IdentityDictionary", "("), nil,
            "Invalid trigger"
        );
        
        test.equals(
            handler.validatePrefix("    lowercaseDictionary", "."), nil,
            "Invalid classname"
        );
        
        test.equals(
            handler.validatePrefix(" dictionary ", "."), nil,
            "Invalid classname"
        );
    });
    
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("handle", {
        handler.handle(Meta_LSPCompletionHandler, ".", "addHan").then {
            |result|
            var methods = result[\items].collect(_['label']);
            
            test.assert(
                methods.includesEqual("addHandler"),
                "finds addHandler completion"
            );
            
            test.equals(
                result[\items][0][\filterText],
                "addHandler"
            );
            
            test.equals(
                result[\items][0][\labelDetails],
                (
                    detail: "(name, trigger, prefixHandler, action)",
                    description: "Meta_LSPCompletionHandler"
                ),
            );
            
            test.equals(
                result[\items][0][\label],
                "addHandler"
            )
        };
    })
}).runOrAdd
);