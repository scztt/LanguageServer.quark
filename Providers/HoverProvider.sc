HoverProvider : LSPProvider {
  *methodNames{ ^["textDocument/hover"] }
  *clientCapabilityName{ ^"textDocument.hover" }
  *serverCapabilityName{ ^"hoverProvider" }
  init{
    |clientCapabilities|
  }
  options{ ^() }

  onReceived {
		|method, params|
    var result, body_desc, docTree, helpSourceRootPath; 
    var title, summary, categories, desc, all_desc, example_code;
    var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
		var wordAtCursor = LSPDatabase.getDocumentWordAt(
			doc,
			params["position"]["line"].asInteger,
			params["position"]["character"].asInteger
		);

    var isClass = SCDoc.documents.includesKey("Classes" +/+ wordAtCursor);
    var isGuides = SCDoc.documents.includesKey("Guides" +/+ wordAtCursor);
    var isOverviews = SCDoc.documents.includesKey("Overviews" +/+ wordAtCursor);
    var isReference = SCDoc.documents.includesKey("Reference" +/+ wordAtCursor);

    if ( isClass.not && isGuides.not && isOverviews.not  && isReference.not , { ^nil });

    if (isClass, { helpSourceRootPath = "Classes" }); 
    if (isGuides, { helpSourceRootPath = "Guides" }); 
    if (isOverviews, { helpSourceRootPath = "Overviews" }); 
    if (isReference, { helpSourceRootPath = "Reference" }); 

    docTree = SCDoc.parseFileFull(SCDoc.helpSourceDir +/+ helpSourceRootPath +/+ wordAtCursor ++ ".schelp");
    docTree ?? { ^nil };

    docTree.children.do{ |t|
      switch(t.id, 
        \HEADER, { t.children.do{ |tc| 
          switch(tc.id, 
            \TITLE, { title = tc.text; },
            \SUMMARY, { summary = tc.text; },
            \CATEGORIES, { categories = tc.children.collect(_.text) },
          );
        }},
        \BODY, { t.children.do { |tc|
          switch(tc.id,
            \EXAMPLES, { tc.children.do { |exampleChild|
              switch(exampleChild.id, 
                \CODEBLOCK, { example_code = exampleChild.text; },
                \PROSE, {},
              )
            }},
            \DESCRIPTION, { tc.children.do { |descChild| 
              switch(descChild.id, 
                \PROSE, { 
                  descChild.text !? desc = desc ++ descChild.text; 
                  desc = desc ++ descChild.children.collect{ |c| 
                    switch(c.id,
                      \TEXT, c.text,
                      \LINK, { 
                        var link = SCDocHTMLRenderer.mkLink(c.text);
                        format("%", link);
                      }
                    ) };
                }
              )
              }},
          )
        } }
    );};
    body_desc = format("## %\n*%*\n \n% \n\n",title,categories, summary);
    desc !? { body_desc = format("% ### Description:\n\n  % \n \n\n --- \n", body_desc, desc.join(""));};
    example_code !? { body_desc = format("% ### Examples \n ```supercollider %  \n",body_desc, example_code); }
    
    ^(
      contents: ( 
        kind: "markdown", 
        value: body_desc
      )
    )
	}
}

// grabbed from official `SCDocRenderer.sc` with slighly modifications.
// since we cannot access to `BaseDir` in `SCDocHTMLRenderer` class, 
// hence it'll throw an error for `+/+` (see https://github.com/supercollider/supercollider/blob/ef627ce2c564fe323125234e4374c9c4b0fc7f1d/SCClassLibrary/SCDoc/SCDocRenderer.sc#L62)
+SCDocHTMLRenderer {
  // returns: the <a> tag HTML representation of the original `link`
  // Possible, non-exhaustive input types for `link`:
	//   "#-decorator#decorator"
	//   "#-addAction"
	//   "Classes/View#-front#shown"
	//   "Guides/GUI-Introduction#view"
	//   "Classes/FlowLayout"
	//   "#*currentDrag#drag&drop data"
	//   "#Key actions"
  *mkLink { |link, escape = true|
		var linkBase, linkAnchor, linkText, linkTarget;

		// Get the link base, anchor, and text from the original string
		// Replace them with empty strings if any are nil
		#linkBase, linkAnchor, linkText = link.split($#);
		linkBase = linkBase ? "";
		linkAnchor = linkAnchor ? "";
		linkText = linkText ? "";

		// Check whether the link is a URL or a relative path (starts with a `/`),
		// NOTE: the second condition is not triggered by anything in the core library's
		// help system. I am not sure if it is safe to remove. - Brian H
		if("^[a-zA-Z]+://.+".matchRegexp(link) or: (link.first == $/)) {
			// Process a link that goes to a URL outside the help system

			// If there was no link text, set it to be the same as the original link
			linkText = if(linkText.isEmpty) { link } { linkText };
			linkTarget = SCDocHTMLRenderer.prLinkTargetForExternalLink(linkBase, linkAnchor);
		} {
		    // Process a link that goes to a URL within the help system
			linkText = SCDocHTMLRenderer.prLinkTextForInternalLink(linkBase, linkAnchor, linkText);
			linkTarget = SCDocHTMLRenderer.prLinkTargetForInternalLinkWithCustomBaseDir(linkBase, linkAnchor, link, (Platform.userConfigDir +/+ "Help"));
		};

		// Escape special characters in the link text if requested
		if(escape) { linkText = SCDocHTMLRenderer.escapeSpecialChars(linkText) };

    ^"<a target=\"_blank\" noreferer href=\"" ++ linkTarget ++ "\">" ++ linkText ++ "</a>";
	}

  *prLinkTargetForInternalLinkWithCustomBaseDir { |linkBase, linkAnchor, originalLink, customBaseDir|
		var doc, result;

		if(linkBase.isEmpty) {
			result = "";
		} {
			doc = SCDoc.documents[linkBase];
			result = customBaseDir +/+ linkBase;

			// If this is an existing document, just add .html to get the target
			if(doc.notNil) {
				result = result ++ ".html"
			} {
				// If the document doesn't exist according to SCDoc, check the filesystem
				// to see if the link target is present
				if(File.exists(SCDoc.helpTargetDir +/+ linkBase ++ ".html")) {
					result = result ++ ".html"
				} {
					// If the link target doesn't exist as an HTML file, check to see if the
					// raw filepath exists. If it does, do nothing with it -- we're done. If
					// it doesn't, then consider this a broken link.
					if(File.exists(SCDoc.helpTargetDir +/+ linkBase).not) {
						"SCDoc: In %\n"
						"  Broken link: '%'"
						.format(currDoc.fullPath, originalLink).warn;
					};
				};
			};
		};

    ^result

		// if(linkAnchor.isEmpty) {
		// 	^result
		// } {
		// 	^result ++ "#" ++ SCDocHTMLRenderer.escapeSpacesInAnchor(linkAnchor);
		// }
	}
}