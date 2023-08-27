HoverProvider : LSPProvider {
  classvar <allUserExtensionDocs;
  var streams, metadata, wordAtCursor;

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
    var result, docNode, helpSourceCategoryChildPath;
    var isDocumentedClass, isDocumentedGuides, isDocumentedOverviews, isDocumentedReference;
    var doc = LSPDocument.findByQUuid(params["textDocument"]["uri"]);
    var position = (
      line: params["position"]["line"].asInteger,
      character: params["position"]["character"].asInteger,
    );

    wordAtCursor = this.getDocumentWordAndParentAt(
      doc,
      position[\line],
      position[\character]
    );
    wordAtCursor.remove(nil);
    streams = (
      class: CollStream(""),
      cMethod: CollStream("")
    );
    metadata = (
      title: "",
      summary: "",
      categories: "",
      example_code: nil,
      cMethodSignature: "" 
    );

    // check if wordAtCursor is a documented class.
    // `SCDoc.documents` include both "system documents" and "user extension documents".
    // ? how can we figure out category folder without hard-coding ("Classes" or "Guides" or "Overviews", etc)
    isDocumentedClass = if (SCDoc.documents["Classes" +/+ wordAtCursor[0]].isNil,{ false },{ SCDoc.documents["Classes" +/+ wordAtCursor[0]].isUndocumentedClass.not });
    isDocumentedGuides = if (SCDoc.documents["Guides" +/+ wordAtCursor[0]].isNil,{ false },{ SCDoc.documents["Guides" +/+ wordAtCursor[0]].isUndocumentedClass.not });
    isDocumentedOverviews = if (SCDoc.documents["Overviews" +/+ wordAtCursor[0]].isNil,{ false },{ SCDoc.documents["Overviews" +/+ wordAtCursor[0]].isUndocumentedClass.not });
    isDocumentedReference = if (SCDoc.documents["Reference" +/+ wordAtCursor[0]].isNil,{ false },{ SCDoc.documents["Reference" +/+ wordAtCursor[0]].isUndocumentedClass.not });
    
    // return nothing if wordAtCursor is not a ClassName, otherwise return "undocumented".
    if ([isDocumentedClass, isDocumentedGuides, isDocumentedOverviews, isDocumentedReference ].every(_.not) , { 
      if ( wordAtCursor[0].asSymbol.isClassName, { ^( contents: ( kind: "markdown", value: "undocumented" )) },{ ^nil } ) 
     });

    // extract helpSource child path.
    if (isDocumentedClass, { helpSourceCategoryChildPath = "Classes" }); 
    if (isDocumentedGuides, { helpSourceCategoryChildPath = "Guides" }); 
    if (isDocumentedOverviews, { helpSourceCategoryChildPath = "Overviews" }); 
    if (isDocumentedReference, { helpSourceCategoryChildPath = "Reference" }); 

    // check if `wordAtCursor` is a member of `allUserExtensionDocs`
    // if so use different path from system extension
    if (allUserExtensionDocs.includesKey(wordAtCursor[0].asSymbol), { 
      var userExtPath = allUserExtensionDocs[wordAtCursor[0].asSymbol] +/+ "HelpSource" +/+ helpSourceCategoryChildPath;
      docNode = SCDoc.parseFileFull(userExtPath +/+ wordAtCursor[0]  ++ ".schelp");
    }, {
      docNode = SCDoc.parseFileFull(SCDoc.helpSourceDir +/+ helpSourceCategoryChildPath +/+ wordAtCursor[0] ++ ".schelp");
    }); 

    docNode ?? { ^nil };

    docNode.children.do{ |t|
      switch(t.id, 
        \HEADER, { t.children.do{ |tc| 
          switch(tc.id, 
            \TITLE, { metadata[\title] = tc.text; },
            \SUMMARY, { metadata[\summary] = tc.text; },
            \CATEGORIES, { metadata[\categories] = tc.children.collect(_.text) },
          );
        }},
        \BODY, { t.children.do { |tc|
          switch(tc.id,
            \EXAMPLES, { tc.children.do { |exampleChild|
              switch(exampleChild.id, 
                \CODEBLOCK, { metadata[\example_code] = exampleChild.text; },
                \PROSE, {},
              )
            }},
            \DESCRIPTION, { 
              if (wordAtCursor.size == 1, { 
                SCDocRendererForLSP.renderSubTreeForLSP(streams[\class], tc);
              }); 
            },
            \CLASSMETHODS, {  
              if (wordAtCursor.size == 2, { 
                metadata[\cMethodSignature] = SCDocRendererForLSP.getClassMethodSignatureString(wordAtCursor);
                SCDocRendererForLSP.renderMethodDoc(streams[\cMethod], tc, wordAtCursor[1], \CMETHOD);             
              }); 
            },
            \INSTANCEMETHODS, { 
                // TODO: 
            },
          );
        }};
      );};

    ^(contents: ( kind: "markdown", value: this.getHoverDescString ))
	}

  // this grabbed from `LSPDatabase.getDocumentWordAt` with modifications, instead of return only `wordAtCursor`
  // it'll return `parent` up to cursor position as well. 
  // eg. when hover at "someting" in "LSPDocument.findMethod.something.anotherthing"
  // it will return [LSPDocument, findMethod, something]
  // useful for rendering Class method documents.
  // it might be replaced/removed in the future when implementing an AST
  getDocumentWordAndParentAt {
    |doc, line, character|
    var lineString = LSPDatabase.getDocumentLine(doc, line);
    var start = character;
    var word;
    
    Log('LanguageServer.quark').info("Searching line for a word: '%' at %:%", lineString, line, character);
    
    while {
        (start >= 0) and: { ((lineString[start] !? _.isAlphaNum ?? true)  or: { lineString[start] == $_ }) }
    } {
        start = start - 1
    };
    start = start + 1;
    
    word = lineString.findRegexpAt("[A-Za-z][\\w]*", start);
    if (word.size > 0) {
        // return flatten array of parents(token)
        // eg. when hovering "includeKeys" in "SCDoc.documents.includeKeys"
        // it'll be [ [[nil, SCDoc ], documents], includeKeys ]
        // and will be flatten to [nil, SCDoc, documents, includeKeys]
        ^[ if (lineString[start - 1] == $., {this.getDocumentWordAndParentAt(doc, line, start - 2)}), word[0].asSymbol ].flat
    } {
        ^nil
    }
  }

  getHoverDescString {
    var desc;

    // CLASS METHOD DOC
    // render "undocumented" if stream is empty.
    // size = 2, eg. [SCDoc, documents]
    if (wordAtCursor.size == 2, {
        desc = streams[\cMethod].collection !? { 
          format("(class method) \n```supercollider \n%\n\n```\n\n --- \n\n%\n",
              metadata[\cMethodSignature], 
              if (streams[\cMethod].collection == "", { format("*undocumented*") }, { streams[\cMethod].collection } 
          )) 
        };
      });
  
    // CLASS DOC
    // size = 1, eg. [SCDoc] 
    if (wordAtCursor.size == 1, {
      desc = format("## %\n*%*\n \n% \n\n % \n \n\n --- \n",metadata[\title],metadata[\categories], metadata[\summary], streams[\class].collection);
      metadata[\example_code] !? { desc = format("% ### Examples \n ```supercollider %  \n```\n\n",desc, metadata[\example_code]); };
    });

    ^desc
  }
}

// mostly from official `SCDocRenderer.sc` with slighly modifications (notated by suffix eg. `ForLSP` or `lsp`).
SCDocRendererForLSP : SCDocHTMLRenderer {
  classvar lspBaseDir;

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
			linkTarget = this.prLinkTargetForInternalLinkForLSP(linkBase, linkAnchor, link);
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
		var doc, result;

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

    // ^result
		if(linkAnchor.isEmpty) {
			^result
		} {
			^result ++ "#" ++ SCDocHTMLRenderer.escapeSpacesInAnchor(linkAnchor);
		}
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
				this.renderChildrenForLSP(stream, node);
			},
			\NL, { }, 
			\TEXT, {
				stream << SCDocHTMLRenderer.escapeSpecialChars(node.text);
			},
			\STRING, {
				stream << SCDocHTMLRenderer.escapeSpecialChars(node.text);
			},
			\LINK, {
				stream << this.htmlForLinkForLSP(node.text);
			},
			\CODEBLOCK, {
				stream << format("\n\n```supercollider \n%\n\n```\n\n", SCDocHTMLRenderer.escapeSpecialChars(node.text));
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
                stream << "\n<u>NOTE: ";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
				stream << "</u>";
			},
			\WARNING, {
				stream << "<div class='warning'><span class='warninglabel'>WARNING:</span> ";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
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
				this.renderChildrenForLSP(stream, node);
				stream << "</ul>\n";
			},
			\TREE, {
				stream << "<ul class='tree'>\n";
				this.renderChildrenForLSP(stream, node);
				stream << "</ul>\n";
			},
			\NUMBEREDLIST, {
				stream << "<ol>\n";
				this.renderChildrenForLSP(stream, node);
				stream << "</ol>\n";
			},
			\ITEM, { // for LIST, TREE and NUMBEREDLIST
				stream << "<li>";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
			},
// Definitionlist
			\DEFINITIONLIST, {
				stream << "<dl>\n";
				this.renderChildrenForLSP(stream, node);
				stream << "</dl>\n";
			},
			\DEFLISTITEM, {
				this.renderChildrenForLSP(stream, node);
			},
			\TERM, {
				stream << "<dt><u>";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
        stream << "</u>"; 
			},
			\DEFINITION, {
				stream << "<dd>";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
			},
// Tables
			\TABLE, {
				stream << "<table>\n";
				this.renderChildrenForLSP(stream, node);
				stream << "</table>\n";
			},
			\TABROW, {
				stream << "<tr>";
				this.renderChildrenForLSP(stream, node);
			},
			\TABCOL, {
				stream << "<td>";
				noParBreak = true;
				this.renderChildrenForLSP(stream, node);
			},
// Methods
			\CMETHOD, {
				// this.renderMethodForLSP(
				// 	stream, node,
				// 	\classMethod,
				// 	currentClass !? {currentClass.class},
				// 	currentImplClass !? {currentImplClass.class}
				// );
			},
			\IMETHOD, {
				// this.renderMethodForLSP(
				// 	stream, node,
				// 	\instanceMethod,
				// 	currentClass,
				// 	currentImplClass
				// );
			},
			\METHOD, {
				// this.renderMethodForLSP(
				// 	stream, node,
				// 	\genericMethod,
				// 	nil, nil
				// );
			},
      \METHODBODY, {
				// this.renderChildrenForLSP(stream, node);
			},
      \METHODNAMES, {
				// this.renderChildrenForLSP(stream, node);
			},
			\CPRIVATE, {},
			\IPRIVATE, {},
			\COPYMETHOD, {},
			\CCOPYMETHOD, {},
			\ICOPYMETHOD, {},
			\ARGUMENTS, {
        stream << "<h4>Arguments:</h4>\n\n<tbody><table>";
        stream << "\n\n";
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
        
				this.renderChildrenForLSP(stream, node);
        stream << "</tbody></table>";
			},
			\ARGUMENT, {
				currArg = currArg + 1;

                // style: vertical-align: top; not working
                // since any styles except for `color` and `background-color` will be sanitized. 
                // see: https://github.com/microsoft/vscode/blob/6d2920473c6f13759c978dd89104c4270a83422d/src/vs/base/browser/markdownRenderer.ts#L309
				stream << "<tr><td class='argumentname'>";
				if(node.text.isNil) {
					currentMethod !? {
						if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)}) {
							stream << "... ";
						};
						stream << if(currArg < currentMethod.argNames.size) {
							// if(currArg > minArgs) {
							// 	"("++currentMethod.argNames[currArg]++")";
							// } {
							currentMethod.argNames[currArg];
							// }
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
						// if(currArg > minArgs) {
						// 	"("++node.text++")";
						// } {
              "\n\n`" ++	node.text ++ "`\n\n";
              // };
            } {
              "("++node.text++")" // excessive arg
            };
          };
				stream << "<td class='argumentdesc'>";
				this.renderChildrenForLSP(stream, node);
			},
			\RETURNS, {
				stream << "<h4>Returns:</h4>\n<div class='returnvalue'>";
				this.renderChildrenForLSP(stream, node);
				stream << "</div>";

			},
			\DISCUSSION, {
				stream << "<h4>Discussion:</h4>\n";
				this.renderChildrenForLSP(stream, node);
			},
// Sections
			\CLASSMETHODS, {
				if(node.notPrivOnly) {
					stream << "<h2><a class='anchor' name='classmethods'>Class Methods</a></h2>\n";
				};
				this.renderChildrenForLSP(stream, node);
			},
			\INSTANCEMETHODS, {
				if(node.notPrivOnly) {
					stream << "<h2><a class='anchor' name='instancemethods'>Instance Methods</a></h2>\n";
				};
				this.renderChildrenForLSP(stream, node);
			},
			\DESCRIPTION, {
				stream << "<h2><a class='anchor' name='description'>Description</a></h2>\n";
				this.renderChildrenForLSP(stream, node);
			},
			\EXAMPLES, {
				stream << "<h2><a class='anchor' name='examples'>Examples</a></h2>\n";
				this.renderChildrenForLSP(stream, node);
			},
			\SECTION, {
				stream << "<h2><a class='anchor' name='" << SCDocHTMLRenderer.escapeSpacesInAnchor(node.text)
				<< "'>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</a></h2>\n";
				if(node.makeDiv.isNil) {
					this.renderChildrenForLSP(stream, node);
				} {
					stream << "<div id='" << node.makeDiv << "'>";
					this.renderChildrenForLSP(stream, node);
					stream << "</div>";
				};
			},
			\SUBSECTION, {
				stream << "<h3><a class='anchor' name='" << SCDocHTMLRenderer.escapeSpacesInAnchor(node.text)
				<< "'>" << SCDocHTMLRenderer.escapeSpecialChars(node.text) << "</a></h3>\n";
				if(node.makeDiv.isNil) {
					this.renderChildrenForLSP(stream, node);
				} {
					stream << "<div id='" << node.makeDiv << "'>";
					this.renderChildrenForLSP(stream, node);
					stream << "</div>";
				};
			},
			{
				"SCDoc: In %\n"
				"  Unknown SCDocNode id: %".format(currDoc.fullPath, node.id).warn;
				this.renderChildrenForLSP(stream, node);
			}
		);
	}

  // handle streaming matched method's informations.
  *renderMethodDoc{ | methodStream, node, hoverMethodName, methodType |
    node.children.do { |methodChild|  
      switch(methodChild.id, 
        \SUBSECTION, { this.renderMethodDoc(methodStream, methodChild, hoverMethodName, methodType) },
        { methodChild.children.do({ |methodChildChild| 
            var method = node.findChild(methodType);
            var methodName = methodChild.findChild("METHODNAMES".asSymbol);
            var methodBody = methodChild.findChild("METHODBODY".asSymbol);
      
            method !? methodName !? { 
              var meth = methodChildChild.children.detect({ |mc| mc.text == hoverMethodName.asString; });
              meth !? { this.renderChildrenForLSP(methodStream, methodBody); };
            }
          });
        }
      );
    }
  }

  // get class signature string with default args values.
  // eg. `SinOsc.ar(freq: 440.0, phase: 0.0, mul: 1.0, add: 0.0)`
  *getClassMethodSignatureString { | wordAtCursor |
    var m, klass, sig;
    klass = wordAtCursor[0].asClass;
    m = klass !? klass.class.findRespondingMethodFor(wordAtCursor[1]);
    m !? { sig = m.argNames !? {  this.makeArgStringForLSP(m)} ?? {"value"}; };
    sig = klass.asString ++ "." ++ wordAtCursor[1].asString ++ sig;
    ^sig
  }

  *makeArgStringForLSP {|m, par=true|
    var res = "";
    var value;
    var l = m.argNames;
    var last = l.size-1;
    l.do {|a,i|
        if (i>0) { //skip 'this' (first arg)
            if(i==last and: {m.varArgs}) {
                res = res ++ "... " ++ a;
            } {
                if (i>1) { res = res ++ ", " };
                res = res ++ a;
                (value = m.prototypeFrame[i]) !? {
                    value = if(value.class===Float) { value.asString } { value.cs };
                    res = res ++ ": " ++ value;
                };
            };
            res = res;
        };
    };
    if (res.notEmpty and: par) {
        ^("("++res++")");
    };
    ^res;
  }
}