// LSP-regions-realworld.scd
// The cases from LSP-regions-test.scd laid out as an actual document, so region
// detection can be checked against a real file. Open this in the editor to see
// which regions are detected, or run the validation block at the bottom of
// LSP-regions-test.scd, which loads this file from disk and checks the results.
//
// Every block starting "( // <Name>" (plus the two labeled unnamed blocks) should
// be detected as a region. Anything labeled "NOT" must not produce a region.

// NOT: code after the open paren
( "not a region".postln )

// NOT: empty parens
( )

// NOT: block comment then code after the paren
( /* foo */ "not a region".postln )

// NOT: code directly after the paren
(1 + 1
).postln;

// unnamed region -> "[block 0]"
(
x = 1;
x + 1;
)

( // My Region
"named via line comment".postln;
)

(// Tight Name
"no space before the slashes".postln;
)

( /* Block Name */
"named via block comment".postln;
)

// unnamed region, trailing whitespace after the paren -> "[block 4]"
(  	
x = 2;
)

( // Nested Parens
x = max(1, (2 + 3));
)

( // Paren In String
x = ") not a close";
)

( // Escaped Quotes
x = "say \") \"";
)

( // Escaped Backslash
x = "path\\" ++ ") ok";
)

( // Paren In Line Comment
// ) not a close
x = 3;
)

( // Paren In Block Comment
/* ) still a comment
) more */
x = 4;
)

( // Apostrophe In Comment
// don't break parsing
x = 5;
)

( // Apostrophe In String
x = "it's fine";
)

( // Symbol With Paren
x = 'weird ) symbol';
)

( // Character Literals
x = $);
y = $(;
z = $";
)

( // Nested Block Comments
/* outer /* inner */ still a comment ) */
x = 6;
)

( // Line Comment Inside Block Comment
/* // still block
) */
x = 7;
)

( // Slash Star Slash
/*/
) still a comment */
x = 8;
)

( // Outer
x = 9;
( // Inner
y = 10;
);
x + y;
)

// NOT: region-lookalike inside a top-level block comment
/*
(
"never detected".postln;
)
*/

// NOT: region-lookalike inside a multi-line string
x = "
(
not a region
)
";

( // Multi-Line String Inside
x = "line one
) 
not a close
end";
)

// NOT: unclosed region at end of file
(
x = 11;
