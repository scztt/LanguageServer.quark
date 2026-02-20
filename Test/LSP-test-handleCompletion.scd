/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
// End-to-end tests for LSPCompletionHandler.handleCompletion
// Tests the full flow: line text -> parsing -> handler selection -> completion results
(
UnitTest2("handleCompletion", {
    |test|

    // Re-initialize handlers to ensure they're set up
    LSPCompletionHandler.completionHandlers = nil;
    LSPCompletionHandler.initClass;

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class method completion (ClassName.meth)
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("class method completion", {
        LSPCompletionHandler.handleCompletion("SinOsc.ar").then {
            |result|
            var arItem;

            test.notNil(result, "SinOsc.ar returns result");

            arItem = result[\items].detect { |item| item[\filterText] == "ar" };
            test.notNil(arItem, "SinOsc.ar: 'ar' method found");
        };

        LSPCompletionHandler.handleCompletion("Array.new").then {
            |result|
            var newItem;

            test.notNil(result, "Array.new returns result");

            newItem = result[\items].detect { |item| item[\filterText] == "new" };
            test.notNil(newItem, "Array.new: 'new' method found");
        };

        LSPCompletionHandler.handleCompletion("SinOsc.").then {
            |result|
            test.notNil(result, "SinOsc. returns result");
            test.assert(result[\items].size > 0, "SinOsc. returns completions");
        };

        // After assignment
        LSPCompletionHandler.handleCompletion("x = SinOsc.ar").then {
            |result|
            test.notNil(result, "x = SinOsc.ar returns result");

            test.notNil(
                result[\items].detect { |item| item[\filterText] == "ar" },
                "SinOsc.ar after assignment: 'ar' found"
            );
        };
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Instance method completion (variable.meth)
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("instance method completion", {
        LSPCompletionHandler.handleCompletion("variable.coll").then {
            |result|
            test.notNil(result, "variable.coll returns result");

            test.notNil(
                result[\items].detect { |item| item[\filterText] == "collect" },
                "variable.coll: 'collect' method found"
            );
        };

        LSPCompletionHandler.handleCompletion("~envVar.postl").then {
            |result|
            test.notNil(result, "~envVar.postl returns result");

            test.notNil(
                result[\items].detect { |item| item[\filterText] == "postln" },
                "~envVar.postl: 'postln' method found"
            );
        };

        // Method after open paren
        LSPCompletionHandler.handleCompletion("(variable.postl").then {
            |result|
            test.notNil(result, "(variable.postl returns result");

            test.notNil(
                result[\items].detect { |item| item[\filterText] == "postln" },
                "(variable.postl: 'postln' method found"
            );
        };

        // Very common: empty completion after dot
        LSPCompletionHandler.handleCompletion("variable.").then {
            |result|
            test.notNil(result, "variable. returns result");
            test.assert(result[\items].size > 0, "variable. returns completions");
        };
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class name completion (just typing a class name)
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("class name completion", {
        LSPCompletionHandler.handleCompletion("SinOsc").then {
            |result|
            test.notNil(result, "SinOsc returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "SinOsc" },
                "SinOsc class name found"
            );
        };

        LSPCompletionHandler.handleCompletion("Arr").then {
            |result|
            test.notNil(result, "Arr returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "Array" },
                "Arr: Array class found"
            );
        };

        // After assignment
        LSPCompletionHandler.handleCompletion("var x = Arr").then {
            |result|
            test.notNil(result, "var x = Arr returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "Array" },
                "var x = Arr: Array found"
            );
        };
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Singleton completion (Pdef(, Ndef(, etc.)
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("singleton completion", {
        Pdef(\__e2e_test_1, Pbind());
        Pdef(\__e2e_test_2, Pbind());

        LSPCompletionHandler.handleCompletion("Pdef(").then {
            |result|
            test.notNil(result, "Pdef( returns result");
            test.assert(result[\items].size > 0, "Pdef( returns completions");

            test.notNil(
                result[\items].detect { |item| item[\label].asString.contains("__e2e_test_1") },
                "Pdef(: test Pdef found"
            );
        };

        // Non-def class with ( should NOT trigger singleton handler
        (LSPCompletionHandler.handleCompletion("Array(") !? {
            |deferred|
            deferred.then {
                |result|
                test.assert(
                    result[\items].detect { |item| item[\label].asString.contains("__e2e_test") }.isNil,
                    "Array( should not show Pdef names"
                );
            }
        }) ?? {
            // Returns nil = no handler matched, which is acceptable
            test.assert(true, "Array( returns no completion handler (expected)")
        };
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Tricky / edge case scenarios
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("tricky scenarios", {
        // Chained method calls
        LSPCompletionHandler.handleCompletion("a.collect.fl").then {
            |result|
            test.notNil(result, "chained method returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "flat" },
                "a.collect.fl: 'flat' found"
            );
        };

        // Method on integer literal
        LSPCompletionHandler.handleCompletion("42.abs").then {
            |result|
            test.notNil(result, "42.abs returns result");
        };

        // Empty line
        test.isNil(
            LSPCompletionHandler.handleCompletion(""),
            "Empty line returns nil"
        );

        // Line with only whitespace and a lowercase word - no handler should match
        test.isNil(
            LSPCompletionHandler.handleCompletion("  variable"),
            "lowercase word without trigger returns nil"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Method name edge cases
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("method name patterns", {
        // Methods with underscore prefix (private-ish methods)
        LSPCompletionHandler.handleCompletion("variable.pr").then {
            |result|
            test.notNil(result, "variable.pr returns result");
            test.assert(
                result[\items].size > 0,
                "variable.pr: private methods found"
            );
        };

        // Very short completion
        LSPCompletionHandler.handleCompletion("variable.a").then {
            |result|
            test.notNil(result, "variable.a returns result");
            test.assert(
                result[\items].size > 0,
                "variable.a: single char completions found"
            );
        };

        // Common patterns
        LSPCompletionHandler.handleCompletion("variable.do").then {
            |result|
            test.notNil(result, "variable.do returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "do" },
                "variable.do: 'do' method found"
            );
        };

        // Iteration methods
        LSPCompletionHandler.handleCompletion("variable.collect").then {
            |result|
            test.notNil(result, "variable.collect returns result");
            test.notNil(
                result[\items].detect { |item| item[\filterText] == "collect" },
                "variable.collect: 'collect' found"
            );
        };
    });
}).runOrAdd
)
