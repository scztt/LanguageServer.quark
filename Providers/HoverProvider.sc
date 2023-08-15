HoverProvider : LSPProvider {
  classvar <allUserExtensionDocs; 

  *methodNames{ ^["textDocument/hover"] }
  *clientCapabilityName{ ^"textDocument.hover" }
  *serverCapabilityName{ ^"hoverProvider" }
  init{
    |clientCapabilities|

    // make user's extension database, for quick indexing.
    // it will scan through 
    // - LanguageConfig.includeDefaultPaths
    // - Platform.userExtensionDir
    // - platform.systemExtensionDir
    allUserExtensionDocs = Dictionary.newFrom(
      SCDoc.prRescanHelpSourceDirs.collect{ |path| 
        var p = PathName.new(PathName.new(path).parentPath.withoutTrailingSlash);
        // the regex will extract only fileName(exclude `.` ) and use as a key.
        // eg. `LanguageServer.quark` will become `LanguageServer`
        [p.fileName.findRegexp("^([A-z0-9]+)\.?.*$")[1][1].asSymbol, p.fullPath]
      }.flatten
    );
  }
  options{ ^() }

  onReceived {
		|method, params|
    var result, docTree, helpSourceCategoryChildPath, stream; 
    var title, summary, categories, desc, all_desc, example_code;
    var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
		var wordAtCursor = LSPDatabase.getDocumentWordAt(
			doc,
			params["position"]["line"].asInteger,
			params["position"]["character"].asInteger
		);

    // check if wordAtCursor is a documented class.
    // `SCDoc.documents` include both "system documents" and "user extension documents".
    // ? how can we figure out category folder without hard-coding ("Classes" or "Guides" or "Overviews", etc)
    var isDocumentedClass = if (SCDoc.documents["Classes" +/+ wordAtCursor].isNil,{ false },{ SCDoc.documents["Classes" +/+ wordAtCursor].isUndocumentedClass.not });
    var isDocumentedGuides = if (SCDoc.documents["Guides" +/+ wordAtCursor].isNil,{ false },{ SCDoc.documents["Guides" +/+ wordAtCursor].isUndocumentedClass.not });
    var isDocumentedOverviews = if (SCDoc.documents["Overviews" +/+ wordAtCursor].isNil,{ false },{ SCDoc.documents["Overviews" +/+ wordAtCursor].isUndocumentedClass.not });
    var isDocumentedReference = if (SCDoc.documents["Reference" +/+ wordAtCursor].isNil,{ false },{ SCDoc.documents["Reference" +/+ wordAtCursor].isUndocumentedClass.not });
    
    // return nothing if wordAtCursor is not a ClassName, otherwise return "undocumented".
    if ([isDocumentedClass, isDocumentedGuides, isDocumentedOverviews, isDocumentedReference ].every(_.not) , { 
      if ( wordAtCursor.asSymbol.isClassName, { ^( contents: ( kind: "markdown", value: "undocumented" )) },{ ^nil } ) 
     });

    // extract helpSource child path.
    if (isDocumentedClass, { helpSourceCategoryChildPath = "Classes" }); 
    if (isDocumentedGuides, { helpSourceCategoryChildPath = "Guides" }); 
    if (isDocumentedOverviews, { helpSourceCategoryChildPath = "Overviews" }); 
    if (isDocumentedReference, { helpSourceCategoryChildPath = "Reference" }); 

    // check if `wordAtCursor` is a member of `allUserExtensionDocs`
    // if so use different path from system extension
    if (allUserExtensionDocs.includesKey(wordAtCursor.asSymbol), { 
      var userExtPath = allUserExtensionDocs[wordAtCursor.asSymbol] +/+ "HelpSource" +/+ helpSourceCategoryChildPath;
      docTree = SCDoc.parseFileFull(userExtPath +/+ wordAtCursor  ++ ".schelp");
    }, {
      docTree = SCDoc.parseFileFull(SCDoc.helpSourceDir +/+ helpSourceCategoryChildPath +/+ wordAtCursor ++ ".schelp");
    }); 

    docTree ?? { ^nil };

    // initialized stream for rendering subtree (`\DESCRIPTION`)
    stream = CollStream(""); 

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
              descChild.children.collect { |c| 
                SCDocHTMLRenderer.renderSubTreeForLSP(stream, c); 
              };
            }},
          )
        } }
    );};
    desc = format("## %\n*%*\n \n% \n\n",title,categories, summary);
    desc = format("% ### Description:\n\n  % \n \n\n --- \n", desc, stream.collection);
    example_code !? { desc = format("% ### Examples \n ```supercollider %  \n",desc, example_code); }
    
    ^(
      contents: ( 
        kind: "markdown", 
        value: desc 
      )
    )
	}
}

// mostly from official `SCDocRenderer.sc` with slighly modifications (notated by suffix eg. `ForLSP` or `lsp`).
+SCDocHTMLRenderer {

  // grabbed from `SCDocHTMLRenderer.htmlForLink`
  // returns: the <a> tag HTML representation of the original `link`
  // Possible, non-exhaustive input types for `link`:
	//   "#-decorator#decorator"
	//   "#-addAction"
	//   "Classes/View#-front#shown"
	//   "Guides/GUI-Introduction#view"
	//   "Classes/FlowLayout"
	//   "#*currentDrag#drag&drop data"
	//   "#Key actions"
  *htmlForLinkForLSP { |link, escape = true|
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
			linkTarget = SCDocHTMLRenderer.prLinkTargetForInternalLinkForLSP(linkBase, linkAnchor, link);
		};

		// Escape special characters in the link text if requested
		if(escape) { linkText = SCDocHTMLRenderer.escapeSpecialChars(linkText) };

    ^"<a target=\"_blank\" noreferer href=\"" ++ linkTarget ++ "\">" ++ linkText ++ "</a>";
	}

  // grabbed from `SCDocHTMLRenderer.prLinkTargetForInternalLink`
  // since we cannot access to `BaseDir` in original method, 
  // hence it'll throw an error for `+/+` (because `BaseDir` initialized in the method for ScIDE) (see https://github.com/supercollider/supercollider/blob/ef627ce2c564fe323125234e4374c9c4b0fc7f1d/SCClassLibrary/SCDoc/SCDocRenderer.sc#L62)
  // so it use `lspBaseDir` instead if `baseDir`.
  *prLinkTargetForInternalLinkForLSP { |linkBase, linkAnchor, originalLink|
		var doc, result, lspBaseDir;

		if(linkBase.isEmpty) {
			result = "";
		} {
			doc = SCDoc.documents[linkBase];
      lspBaseDir = PathName.new(PathName.new(URI.new(SCDoc.findHelpFile(linkBase)).asLocalPath).parentPath.withoutTrailingSlash).parentPath;

			result = lspBaseDir +/+ linkBase;

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

  // grabbed from `SCDocHTMLRenderer.renderChildren`
  *renderChildrenForLSP {|stream, node|
		node.children.do {|child| this.renderSubTreeForLSP(stream, child) };
	}


  // grabbed from `SCDocHTMLRenderer.renderSubTree` 
  // and modified `this.htmlForLink` to `this.htmlForLinkForLSP` 
  // to omit the error when encounter "Path concatenation operator" (+/+)
  *renderSubTreeForLSP { |stream, node|
		var f, z, img;
		switch(node.id,
			\PROSE, {
        // the official is just checking for `noParBreak`.
        // since this method is grabbed from `SCDocHTMLRenderer.renderSubTree`,
        // somehow `noParBreak` hasn't initialized yet
        // thus, it might throw an errors if not checking for `nil`.
        // eg. when hover on `Dictionary`.
				if(noParBreak.notNil && noParBreak) {
					noParBreak = false;
				} {
					stream << "\n<p>";
				};
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\NL, { }, 
			\TEXT, {
				stream << SCDocHTMLRenderer.escapeSpecialChars(node.text);
			},
			\LINK, {
				stream << this.htmlForLinkForLSP(node.text);
			},
			\CODEBLOCK, {
				stream << format("\n ```supercollider \n%\n```", SCDocHTMLRenderer.escapeSpecialChars(node.text));
			},
			\CODE, {
				stream << "<code>"
				<< SCDocHTMLRenderer.escapeSpecialChars(node.text)
				<< "</code>";
			},
			\EMPHASIS, {
				stream << "<em>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</em>";
			},
			\TELETYPEBLOCK, {
				stream << "<pre>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</pre>";
			},
			\TELETYPE, {
				stream << "<code>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</code>";
			},
			\STRONG, {
				stream << "<strong>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</strong>";
			},
			\SOFT, {
				stream << "<span class='soft'>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</span>";
			},
			\ANCHOR, {
				stream << "<a class='anchor' name='" << SCDocHTMLRenderer.escapeSpacesInAnchor(node.text) << "'>&nbsp;</a>";
			},
			\KEYWORD, {
				node.children.do { |child|
					stream << "<a class='anchor' name='kw_" << SCDocHTMLRenderer.escapeSpacesInAnchor(child.text) << "'>&nbsp;</a>";
				}
			},
			\IMAGE, {
				f = node.text.split($#);
				stream << "<div class='image'>";
				img = "<img src='" ++ f[0] ++ "'/>";
				if(f[2].isNil) {
					stream << img;
				} {
					stream << this.htmlForLinkForLSP(f[2]++"#"++(f[3]?"")++"#"++img,false);
				};
				f[1] !? { stream << "<br><b>" << f[1] << "</b>" }; // ugly..
				stream << "</div>\n";
			},
// Other stuff
			\NOTE, {
        stream << "<div class='note'><span class='notelabel'>NOTE:</span> ";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</div>";
			},
			\WARNING, {
				stream << "<div class='warning'><span class='warninglabel'>WARNING:</span> ";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</div>";
			},
			\FOOTNOTE, {
				footNotes = footNotes.add(node);
				stream << "<a class='footnote anchor' name='footnote_org_"
				<< footNotes.size
				<< "' href='#footnote_"
				<< footNotes.size
				<< "'><sup>"
				<< footNotes.size
				<< "</sup></a> ";
			},
			\CLASSTREE, {
				stream << "<ul class='tree'>";
				SCDocHTMLRenderer.renderClassTree(stream, node.text.asSymbol.asClass);
				stream << "</ul>";
			},
// Lists and tree
			\LIST, {
				stream << "<ul>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</ul>\n";
			},
			\TREE, {
				stream << "<ul class='tree'>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</ul>\n";
			},
			\NUMBEREDLIST, {
				stream << "<ol>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</ol>\n";
			},
			\ITEM, { // for LIST, TREE and NUMBEREDLIST
				stream << "<li>";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
// Definitionlist
			\DEFINITIONLIST, {
				stream << "<dl>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</dl>\n";
			},
			\DEFLISTITEM, {
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\TERM, {
				stream << "<dt>";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\DEFINITION, {
				stream << "<dd>";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
// Tables
			\TABLE, {
				stream << "<table>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</table>\n";
			},
			\TABROW, {
				stream << "<tr>";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\TABCOL, {
				stream << "<td>";
				noParBreak = true;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
// Methods
			\CMETHOD, {
				SCDocHTMLRenderer.renderMethod(
					stream, node,
					\classMethod,
					currentClass !? {currentClass.class},
					currentImplClass !? {currentImplClass.class}
				);
			},
			\IMETHOD, {
				SCDocHTMLRenderer.renderMethod(
					stream, node,
					\instanceMethod,
					currentClass,
					currentImplClass
				);
			},
			\METHOD, {
				SCDocHTMLRenderer.renderMethod(
					stream, node,
					\genericMethod,
					nil, nil
				);
			},
			\CPRIVATE, {},
			\IPRIVATE, {},
			\COPYMETHOD, {},
			\CCOPYMETHOD, {},
			\ICOPYMETHOD, {},
			\ARGUMENTS, {
				stream << "<h4>Arguments:</h4>\n<table class='arguments'>\n";
				currArg = 0;
				if(currentMethod.notNil and: {node.children.size < (currentNArgs-1)}) {
					"SCDoc: In %\n"
					"  Method %% has % args, but doc has % argument:: tags.".format(
						currDoc.fullPath,
						if(currentMethod.ownerClass.isMetaClass) {"*"} {"-"},
						currentMethod.name,
						currentNArgs-1,
						node.children.size,
					).warn;
				};
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</table>";
			},
			\ARGUMENT, {
				currArg = currArg + 1;
				stream << "<tr><td class='argumentname'>";
				if(node.text.isNil) {
					currentMethod !? {
						if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)}) {
							stream << "... ";
						};
						stream << if(currArg < currentMethod.argNames.size) {
							if(currArg > minArgs) {
								"("++currentMethod.argNames[currArg]++")";
							} {
								currentMethod.argNames[currArg];
							}
						} {
							"(arg"++currArg++")" // excessive arg
						};
					};
				} {
					stream << if(currentMethod.isNil or: {currArg < currentMethod.argNames.size}) {
						currentMethod !? {
							f = currentMethod.argNames[currArg].asString;
							if(
								(z = if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)})
										{"... "++f} {f}
								) != node.text;
							) {
								"SCDoc: In %\n"
								"  Method %% has arg named '%', but doc has 'argument:: %'.".format(
									currDoc.fullPath,
									if(currentMethod.ownerClass.isMetaClass) {"*"} {"-"},
									currentMethod.name,
									z,
									node.text,
								).warn;
							};
						};
						if(currArg > minArgs) {
							"("++node.text++")";
						} {
							node.text;
						};
					} {
						"("++node.text++")" // excessive arg
					};
				};
				stream << "<td class='argumentdesc'>";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\RETURNS, {
				stream << "<h4>Returns:</h4>\n<div class='returnvalue'>";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				stream << "</div>";

			},
			\DISCUSSION, {
				stream << "<h4>Discussion:</h4>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
// Sections
			\CLASSMETHODS, {
				if(node.notPrivOnly) {
					stream << "<h2><a class='anchor' name='classmethods'>Class Methods</a></h2>\n";
				};
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\INSTANCEMETHODS, {
				if(node.notPrivOnly) {
					stream << "<h2><a class='anchor' name='instancemethods'>Instance Methods</a></h2>\n";
				};
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\DESCRIPTION, {
				stream << "<h2><a class='anchor' name='description'>Description</a></h2>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\EXAMPLES, {
				stream << "<h2><a class='anchor' name='examples'>Examples</a></h2>\n";
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			},
			\SECTION, {
				stream << "<h2><a class='anchor' name='" << SCDocHTMLRenderer.escapeSpacesInAnchor(node.text)
				<< "'>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</a></h2>\n";
				if(node.makeDiv.isNil) {
					SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				} {
					stream << "<div id='" << node.makeDiv << "'>";
					SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
					stream << "</div>";
				};
			},
			\SUBSECTION, {
				stream << "<h3><a class='anchor' name='" << SCDocHTMLRenderer.escapeSpacesInAnchor(node.text)
				<< "'>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</a></h3>\n";
				if(node.makeDiv.isNil) {
					SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
				} {
					stream << "<div id='" << node.makeDiv << "'>";
					SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
					stream << "</div>";
				};
			},
			{
				"SCDoc: In %\n"
				"  Unknown SCDocNode id: %".format(currDoc.fullPath, node.id).warn;
				SCDocHTMLRenderer.renderChildrenForLSP(stream, node);
			}
		);
	}
}