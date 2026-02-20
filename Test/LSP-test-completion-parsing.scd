/*
UnitTestScript("LSP", thisProcess.nowExecutingPath).runScript;
*/
// Tests for prGetCompletionString - the line parser that extracts
// [prefix, trigger, completion] from a line of code.
// This is the critical first step in auto-completion.
(
UnitTest2("completionParsing", {
    |test|
    var parse;

    // Shorthand: parse a line with default trigger chars "(."
    parse = { |line, triggers="(."| LSPCompletionHandler.prGetCompletionString(line, triggers) };

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Basic method call patterns
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("basic method calls", {
        test.equals(
            parse.("variable.method"),
            ["variable", ".", "method"],
            "Simple method call"
        );

        test.equals(
            parse.("variable."),
            ["variable", ".", ""],
            "Dot with no completion yet"
        );

        test.equals(
            parse.("  variable.meth"),
            ["variable", ".", "meth"],
            "Method call with leading whitespace"
        );

        test.equals(
            parse.("~envVar.method"),
            ["~envVar", ".", "method"],
            "Env var method call"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class method patterns
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("class method calls", {
        test.equals(
            parse.("SinOsc.ar"),
            ["SinOsc", ".", "ar"],
            "Class method call"
        );

        test.equals(
            parse.("SinOsc."),
            ["SinOsc", ".", ""],
            "Class dot with no completion"
        );

        test.equals(
            parse.("  Array.new"),
            ["Array", ".", "new"],
            "Class method with leading whitespace"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Class name completion (no trigger, just typing a class name)
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("class name typing", {
        test.equals(
            parse.("SinOsc"),
            ["", "", "SinOsc"],
            "Bare class name"
        );

        test.equals(
            parse.("  SinOsc"),
            ["", "", "SinOsc"],
            "Class name with leading whitespace"
        );

        test.equals(
            parse.("Sin"),
            ["", "", "Sin"],
            "Partial class name"
        );

        test.equals(
            parse.("x = SinOsc"),
            ["", "", "SinOsc"],
            "Class name after assignment"
        );

        test.equals(
            parse.("var x = SinOsc"),
            ["", "", "SinOsc"],
            "Class name after var assignment"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Singleton patterns: Pdef(, Ndef(, etc.
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("singleton patterns", {
        test.equals(
            parse.("Pdef("),
            ["Pdef", "(", ""],
            "Pdef open paren"
        );

        test.equals(
            parse.("Pdef(\\name"),
            ["Pdef", "(", "\\name"],
            "Pdef with symbol start"
        );

        test.equals(
            parse.("  Ndef(\\so"),
            ["Ndef", "(", "\\so"],
            "Ndef with partial symbol"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Environment variable trigger
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("environment variable trigger", {
        test.equals(
            parse.("~var", "(." ++ "~"),
            [nil, "~", "var"],
            "Env var with ~ in triggers"
        );

        test.equals(
            parse.("~", "(." ++ "~"),
            [nil, "~", ""],
            "Just tilde"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Chained method calls - likely problematic
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("chained methods", {
        test.equals(
            parse.("a.b.c"),
            ["b", ".", "c"],
            "Chained method: should complete 'c' after 'b'"
        );

        test.equals(
            parse.("variable.collect.fl"),
            ["collect", ".", "fl"],
            "Chained method with partial completion"
        );

        test.equals(
            parse.("SinOsc.ar."),
            ["ar", ".", ""],
            "Class method chain: dot after method result"
        );

        test.equals(
            parse.("a.b.c.d"),
            ["c", ".", "d"],
            "Triple chain"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Numeric receivers - likely tricky
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("numeric receivers", {
        test.equals(
            parse.("42.abs"),
            ["42", ".", "abs"],
            "Integer method"
        );

        test.equals(
            parse.("42."),
            ["42", ".", ""],
            "Integer dot"
        );

        // This is tricky: 3.14 is a float literal, but 3.14.round is a method call on a float
        test.dontrun({
            test.equals(
                parse.("3.14.round"),
                ["3.14", ".", "round"],
                "Float method call"
            );
        }, "Parser can't distinguish decimal dot from method trigger");

        test.dontrun({
            test.equals(
                parse.("-1.abs"),
                ["-1", ".", "abs"],
                "Negative integer method"
            );
        }, "Parser prefix regex doesn't capture leading negative sign");
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // After operators and expressions
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("after expressions", {
        test.equals(
            parse.("x + SinOsc.ar"),
            ["SinOsc", ".", "ar"],
            "Class method after binary op"
        );

        test.equals(
            parse.("x + variable.meth"),
            ["variable", ".", "meth"],
            "Method call after binary op"
        );

        test.equals(
            parse.("a = SinOsc.ar"),
            ["SinOsc", ".", "ar"],
            "Class method after assignment"
        );

        test.equals(
            parse.("[1, 2, SinOsc.ar"),
            ["SinOsc", ".", "ar"],
            "Class method inside array literal"
        );

        test.equals(
            parse.("if (cond) { result.meth"),
            ["result", ".", "meth"],
            "Method call inside block"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Parenthesized expressions
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("parenthesized expressions", {
        test.equals(
            parse.("(variable.meth"),
            ["variable", ".", "meth"],
            "Method call after open paren"
        );

        test.equals(
            parse.("(SinOsc.ar"),
            ["SinOsc", ".", "ar"],
            "Class method after open paren"
        );

        test.equals(
            parse.("func.value(arg.meth"),
            ["arg", ".", "meth"],
            "Method call inside function args"
        );

        // This might be interpreted as a singleton completion
        test.equals(
            parse.("Array("),
            ["Array", "(", ""],
            "Constructor-like call"
        );
    });

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // Edge cases
    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    test.section("edge cases", {
        // Empty and minimal strings
        test.equals(
            parse.(""),
            [nil, nil, nil],
            "Empty string"
        );

        test.equals(
            parse.("."),
            [nil, ".", ""],
            "Just a dot"
        );

        test.equals(
            parse.("a"),
            [nil, nil, nil],
            "Single lowercase char (no trigger)"
        );

        // Double dots
        test.dontrun({
            test.equals(
                parse.("variable.."),
                ["variable.", ".", ""],
                "Double dot (range operator?)"
            );
        }, "Prefix regex doesn't include dots, so 'variable.' can't be extracted");

        // Bracket method access
        test.dontrun({
            test.equals(
                parse.("array[0].meth"),
                ["0", ".", "meth"],
                "Method after bracket access"
            );
        }, "Brackets break prefix extraction at end-of-string anchor");

        // Semicolon-separated
        test.equals(
            parse.("a.b; c.d"),
            ["c", ".", "d"],
            "Method after semicolon"
        );

        // String context (parser doesn't know about strings)
        test.dontrun({
            test.equals(
                parse.("\"hello\".rev"),
                ["\"hello\"", ".", "rev"],
                "Method on string literal"
            );
        }, "Parser doesn't understand string literals");
    });
}).runOrAdd
)
