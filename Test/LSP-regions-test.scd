// Tests for LSPDatabase.getDocumentRegions
//
// A region is "(" at column 0, followed only by whitespace and/or a comment
// (the comment text becomes the region name), closed by the matching ")".
//
// The "real-world file" section validates against LSP-regions-realworld.scd,
// which contains the same cases laid out as an actual on-disk document.

UnitTest2("LSPDatabase.getDocumentRegions", { |test|
	// regions as [text, startLine, endLine] triples, in emit order
	// (inner regions close first)
	var regionsFor = { |source|
		var doc = (
			path: "/LSP-regions-test/%".format(source.hash),
			string: source,
			version: 1
		);
		LSPDatabase.getDocumentRegions(doc).collect { |r|
			[r[\text], r[\range][\start][\line], r[\range][\end][\line]]
		}
	};

	test.section("not a region") {
		test.equals(regionsFor.("( code.run )"), [],
			"single-line ( code.run )");
		test.equals(regionsFor.("( ) "), [],
			"single-line ( )");
		test.equals(regionsFor.("(code
)"), [],
			"code directly after paren");
		test.equals(regionsFor.("( /* foo */ code.run )"), [],
			"block comment then code after paren");
		test.equals(regionsFor.("(
x;"), [],
			"unclosed region yields nothing");
	};

	test.section("region names") {
		test.equals(regionsFor.("(
var x;
x = 1;
)"), [["[block 0]", 0, 3]],
			"unnamed region");

		test.equals(regionsFor.("( // My Region
x;
)"), [["My Region", 0, 2]],
			"named via line comment");

		test.equals(regionsFor.("(// Tight Name
x;
)"), [["Tight Name", 0, 2]],
			"no space before //");

		test.equals(regionsFor.("( /* Block Name */
x;
)"), [["Block Name", 0, 2]],
			"named via block comment");

		test.equals(regionsFor.("(   \t
x;
)"), [["[block 0]", 0, 2]],
			"trailing whitespace after paren");
	};

	test.section("strings") {
		test.equals(regionsFor.("(
x = \") not a close\";
)"), [["[block 0]", 0, 2]],
			"close paren inside a string");

		test.equals(regionsFor.("(
x = \"say \\\") \\\"\";
)"), [["[block 0]", 0, 2]],
			"escaped quotes inside a string");

		test.equals(regionsFor.("(
x = \"path\\\\\" ++ \") ok\";
)"), [["[block 0]", 0, 2]],
			"escaped backslash then real close-quote");

		test.equals(regionsFor.("(
x = \"it's fine\";
)"), [["[block 0]", 0, 2]],
			"apostrophe inside a string");

		test.equals(regionsFor.("(
x = \"line one
) not a close
end\";
)"), [["[block 0]", 0, 4]],
			"multi-line string inside region");

		test.equals(regionsFor.("x = \"
(
not real
)
\";
( // real
y;
)"), [["real", 5, 7]],
			"region start inside a multi-line string is ignored");
	};

	test.section("comments") {
		test.equals(regionsFor.("(
// ) not a close
x;
)"), [["[block 0]", 0, 3]],
			"close paren inside a line comment");

		test.equals(regionsFor.("(
/* ) still comment
) more */
x;
)"), [["[block 0]", 0, 4]],
			"close paren inside a block comment");

		test.equals(regionsFor.("(
// don't break parsing
x;
)"), [["[block 0]", 0, 3]],
			"apostrophe in a comment");

		test.equals(regionsFor.("(
/* outer /* inner */ still a comment ) */
x;
)"), [["[block 0]", 0, 3]],
			"nested block comments");

		test.equals(regionsFor.("(
/* // still block
) */
x;
)"), [["[block 0]", 0, 4]],
			"line comment marker inside block comment is inert");

		test.equals(regionsFor.("(
/*/
) still comment */
x;
)"), [["[block 0]", 0, 4]],
			"slash-star-slash does not close the comment it opens");

		test.equals(regionsFor.("/*
(
x;
)
*/
( // real
y;
)"), [["real", 5, 7]],
			"region start inside top-level block comment is ignored");
	};

	test.section("structure") {
		test.equals(regionsFor.("(
x = f(a, (b + c));
)"), [["[block 0]", 0, 2]],
			"nested parens inside region");

		test.equals(regionsFor.("(
x = 'weird ) symbol';
)"), [["[block 0]", 0, 2]],
			"quoted symbol containing close paren");

		test.equals(regionsFor.("(
x = $);
y = $(;
z = $\";
)"), [["[block 0]", 0, 4]],
			"character literals $( $) $\"");

		test.equals(regionsFor.("(
x;
)

( // second
y;
)"), [["[block 0]", 0, 2], ["second", 4, 6]],
			"two sequential regions");

		test.equals(regionsFor.("( // outer
x;
( // inner
y;
)
z;
)"), [["inner", 2, 4], ["outer", 0, 6]],
			"nested regions (inner emitted first)");
	};

	test.section("real-world file") {
		var path, source, lines, doc, regions;

		path = LSPDatabase.filenameSymbol.asString.dirname
			+/+ "Test" +/+ "LSP-regions-realworld.scd";
		source = File.readAllString(path);
		lines = source.split($\n);

		doc = (path: path, string: source, version: 1);
		regions = LSPDatabase.getDocumentRegions(doc);

		test.equals(
			regions.collect(_[\text]),
			[
				"[block 0]",
				"My Region",
				"Tight Name",
				"Block Name",
				"[block 4]",
				"Nested Parens",
				"Paren In String",
				"Escaped Quotes",
				"Escaped Backslash",
				"Paren In Line Comment",
				"Paren In Block Comment",
				"Apostrophe In Comment",
				"Apostrophe In String",
				"Symbol With Paren",
				"Character Literals",
				"Nested Block Comments",
				"Line Comment Inside Block Comment",
				"Slash Star Slash",
				"Inner",
				"Outer",
				"Multi-Line String Inside",
			],
			"detected region names, in emit order"
		);

		regions.do { |r|
			var startLine = lines[r[\range][\start][\line]];
			var endLine = lines[r[\range][\end][\line]];
			var endChar = endLine[r[\range][\end][\character] - 1];

			test.assert(startLine.beginsWith("("),
				"% starts on a '(' line".format(r[\text]));
			test.equals(endChar, $),
				"% ends on its ')'".format(r[\text]));
		};
	};
}).runOrAdd;
