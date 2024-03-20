// TestSCDocHTMLRenderer.sc
// Brian Heim
// 2017-07-12

TestLSPSCDocMarkdownRenderer : UnitTest {

	var didSetUp = false;

	setUp {
		if(didSetUp.not) {

			if(SCDoc.didIndexDocuments.not) {
				SCDoc.indexAllDocuments;
			};

			didSetUp = true;
		}
	}

	/*******************************/
	/**** tests for htmlForLink ****/
	/*******************************/

	// external link tests

	test_markdown_output {

		var expectedFile;
		var expected;

		var testPath = TestLSPSCDocMarkdownRenderer.filenameSymbol().asString().dirname;

		var node = SCDoc.parseFileFull(testPath +/+ "SampleDoc.schelp");
		var doc = SCDocEntry(node, "Class/SampleDoc");

		var stream = CollStream("");
		LSPSCDocMarkdownRenderer.renderOnStream(stream, doc, node);

		expectedFile = File(testPath +/+ "SampleDoc_expected.md", "r");
		expected = expectedFile.readAllString;
		expectedFile.close();

		this.assertEquals(stream.contents, expected);
		this.assertEquals(stream.collection.size, expected.size);
	}
}
