/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
// Edge case tests for handler validatePrefix methods.
// These test tricky patterns that real users would type in an IDE.
(
UnitTest2("validatePrefixEdgeCases", {
    |test|
    var methodHandler, classMethodHandler, singletonHandler, envVarHandler;
    
    methodHandler = LSPCompletionHandler.methodHandler;
    classMethodHandler = LSPCompletionHandler.classMethodHandler;
    singletonHandler = LSPCompletionHandler.singletonHandler;
    envVarHandler = LSPCompletionHandler.environmentVariableHandler;
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Method handler - tricky prefixes
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("method handler edge cases", {
        // Single character variable
        test.equals(
            methodHandler.validatePrefix("a", "."), "a",
            "Single char variable"
        );
        
        // Variable after equals sign
        test.equals(
            methodHandler.validatePrefix("x = result", "."), "result",
            "Variable after assignment"
        );
        
        // Variable after comma (inside array, function args)
        test.equals(
            methodHandler.validatePrefix("[1, variable", "."), "variable",
            "Variable after comma in array"
        );
        
        // Variable after semicolon
        test.equals(
            methodHandler.validatePrefix("x.foo; result", "."), "result",
            "Variable after semicolon"
        );
        
        // Result of array access: array[0].
        test.equals(
            methodHandler.validatePrefix("array[0]", "."),
            nil,
            "After array access bracket (no valid prefix)"
        );
        
        // Negative number
        test.equals(
            methodHandler.validatePrefix("    -1", "."),
            nil,
            "Negative number prefix (dash not in valid start chars)"
        );
        
        // Underscore prefix - not a valid SC identifier
        test.equals(
            methodHandler.validatePrefix("    _foo", "."),
            nil,
            "Underscore-prefixed identifier (not valid in SC)"
        );
        
        // this keyword
        test.equals(
            methodHandler.validatePrefix("    this", "."), "this",
            "this keyword"
        );
        
        // super keyword
        test.equals(
            methodHandler.validatePrefix("    super", "."), "super",
            "super keyword"
        );
        
        // true/false/nil
        test.equals(
            methodHandler.validatePrefix("    true", "."), "true",
            "true literal"
        );
        
        test.equals(
            methodHandler.validatePrefix("    nil", "."), "nil",
            "nil literal"
        );
        
        // Multiple dots (chaining) - the prefix is what comes before the last dot
        // For "a.b.c.", the prefix would be "a.b.c" and trigger is "."
        test.equals(
            methodHandler.validatePrefix("a.b", "."), "a.b",
            "Chained prefix with one dot"
        );
        
        // Env var with dotted method chain
        test.equals(
            methodHandler.validatePrefix("~data.keys", "."), "~data.keys",
            "Env var chained method"
        );
    });
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class method handler - tricky prefixes
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("class method handler edge cases", {
        // Single letter class (unlikely but valid in SC)
        // Most single-letter classes don't exist, but test the pattern
        test.equals(
            classMethodHandler.validatePrefix("Object", ".").notNil, true,
            "Known class returns non-nil"
        );
        
        // Meta_ prefixed class
        test.dontrun({
            test.equals(
                classMethodHandler.validatePrefix("Meta_Object", "."), Meta_Object,
                "Meta_ class prefix"
            );
        }, "Handler calls .class on resolved class, so Meta_Object becomes Class instead of Meta_Object");
        
        // Class after assignment
        test.equals(
            classMethodHandler.validatePrefix("x = Array", "."), Meta_Array,
            "Class after assignment"
        );
        
        // Class after open paren
        test.equals(
            classMethodHandler.validatePrefix("(Array", "."), Meta_Array,
            "Class after open paren"
        );
        
        // Class after comma
        test.equals(
            classMethodHandler.validatePrefix("[1, Array", "."), Meta_Array,
            "Class after comma in array"
        );
        
        // Lowercase then uppercase (camelCase variable, not a class)
        test.equals(
            classMethodHandler.validatePrefix("myArray", "."), nil,
            "camelCase variable is not a class"
        );
        
        // Class name that is also a method name (edge case)
        test.equals(
            classMethodHandler.validatePrefix("    Float", "."), Meta_Float,
            "Float class"
        );
        
        // Wrong trigger
        test.equals(
            classMethodHandler.validatePrefix("Array", "("), nil,
            "Class with ( trigger (should be handled by singleton)"
        );
    });
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Singleton handler - tricky prefixes
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("singleton handler edge cases", {
        // Various def classes
        test.equals(
            singletonHandler.validatePrefix("Pdef", "("), Pdef,
            "Pdef"
        );
        
        test.equals(
            singletonHandler.validatePrefix("Tdef", "("), Tdef,
            "Tdef"
        );
        
        test.equals(
            singletonHandler.validatePrefix("MIDIdef", "("), MIDIdef,
            "MIDIdef"
        );
        
        test.equals(
            singletonHandler.validatePrefix("OSCdef", "("), OSCdef,
            "OSCdef"
        );
        
        // After assignment
        test.equals(
            singletonHandler.validatePrefix("x = Pdef", "("), Pdef,
            "Pdef after assignment"
        );
        
        // Non-def class
        test.equals(
            singletonHandler.validatePrefix("Array", "("), nil,
            "Array is not a def class"
        );
        
        test.equals(
            singletonHandler.validatePrefix("SinOsc", "("), nil,
            "SinOsc is not a def class"
        );
        
        // Part of a larger word (regression test)
        test.equals(
            singletonHandler.validatePrefix("myPdef", "("), nil,
            "myPdef is not a standalone class name"
        );
        
        // With various whitespace/prefix contexts
        test.equals(
            singletonHandler.validatePrefix("    Pdef", "("), Pdef,
            "Pdef with leading whitespace"
        );
        
        test.equals(
            singletonHandler.validatePrefix("(Pdef", "("), Pdef,
            "Pdef after open paren"
        );
    });
    
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Environment variable handler
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("envvar handler edge cases", {
        // After various prefixes
        test.notNil(
            envVarHandler.validatePrefix("x = ", "~"),
            "After assignment with space"
        );
        
        test.notNil(
            envVarHandler.validatePrefix("", "~"),
            "Start of line"
        );
        
        test.isNil(
            envVarHandler.validatePrefix("x=", "~"),
            "After assignment without space"
        );
        
        // After operator
        test.notNil(
            envVarHandler.validatePrefix("x + ", "~"),
            "After binary operator with space"
        );
        
        test.isNil(
            envVarHandler.validatePrefix("x +", "~"),
            "After binary operator without trailing space"
        );
    });
}).runOrAdd
)
