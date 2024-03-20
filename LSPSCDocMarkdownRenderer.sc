LSPSCDocMarkdownRenderer {
	classvar <binaryOperatorCharacters = "!@%&*-+=|<>?/";
	classvar currentClass, currentImplClass, currentMethod, currArg;
	classvar currentNArgs;
	classvar footNotes;
	classvar noParBreak;
	classvar currDoc;
	classvar minArgs;
	classvar baseDir;
	classvar links = false;
	classvar indent = "";

	*escapeSpecialChars {|str|
		var x = "";
		var beg = -1, end = 0;
		str.do {|chr, i|
			switch(chr,
				$&, { x = x ++ str.copyRange(beg, i-1) ++ "&amp;"; beg = i+1; },
				$<, { x = x ++ str.copyRange(beg, i-1) ++ "&lt;"; beg = i+1; },
				$>, { x = x ++ str.copyRange(beg, i-1) ++ "&gt;"; beg = i+1; },
				{ end = i }
			);
		};
		if(beg<=end) {
			x = x ++ str[beg..end];
		};
		^x;
	}
	*escapeSpacesInAnchor { |str|
		^str.replace(" ", "%20")
	}

	// Find the target (what goes after href=) for a link that stays inside the hlp system
	*prLinkTargetForInternalLink { |linkBase, linkAnchor, originalLink|
		var doc, result;

		if(linkBase.isEmpty) {
			result = "";
		} {
			doc = SCDoc.documents[linkBase];
			//result = (baseDir ?! "") +/+ linkBase;
			result = linkBase;

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

		if(linkAnchor.isEmpty) {
			^result
		} {
			^result ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor);
		}
	}

	// Creates a link target for a link that points outside of the help system
	*prLinkTargetForExternalLink { |linkBase, linkAnchor|
		if(linkAnchor.isEmpty) {
			^linkBase
		} {
			^linkBase ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor);
		}
	}

	// Find the text label for the given link, which points inside the help system.
	*prLinkTextForInternalLink { |linkBase, linkAnchor, linkText|
		var doc, result;
		// Immediately return link text if available
		if(linkText.isEmpty.not) {
			^linkText
		};

		// If the base was non-empty, generate it by combining the filename and the anchor.
		// Otherwise, if there was an anchor, use that. Otherwise, use "(empty link)"
		if(linkBase.isEmpty) {
			if(linkAnchor.isEmpty) {
				^"(empty link)"
			} {
				^linkAnchor
			}
		} {
			doc = SCDoc.documents[linkBase];
			result = doc !? _.title ? linkBase.basename;
			if(linkAnchor.isEmpty) {
				^result
			} {
				^result ++ ": " ++ linkAnchor
			}
		}
	}

	*mdLink { |link, text|
		text = text ? link;
		if (links) { ^"[" ++ text ++ "](" ++ link ++ ")" } { ^"**"++text++"**" };
	}

	// argument link: the raw link text from the schelp document
	// argument escape: whether or not to escape special characters in the link text itself
	// returns: the <a> tag HTML representation of the original `link`
	// Possible, non-exhaustive input types for `link`:
	//   "#-decorator#decorator"
	//   "#-addAction"
	//   "Classes/View#-front#shown"
	//   "Guides/GUI-Introduction#view"
	//   "Classes/FlowLayout"
	//   "#*currentDrag#drag&drop data"
	//   "#Key actions"
	//   "http://qt-project.org/doc/qt-4.8/qt.html#Key-enum"
	*mdForLink { |link, escape = true|
		var linkBase, linkAnchor, linkText, linkTarget;
		// FIXME: how slow is this? can we optimize

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
			linkTarget = this.prLinkTargetForExternalLink(linkBase, linkAnchor);
		} {
		    // Process a link that goes to a URL within the help system
			linkText = this.prLinkTextForInternalLink(linkBase, linkAnchor, linkText);
			linkTarget = this.prLinkTargetForInternalLink(linkBase, linkAnchor, link);
		};

		// Escape special characters in the link text if requested
		if(escape) { linkText = this.escapeSpecialChars(linkText) };

		// Return a well-formatted <a> tag using the target and link text
		^this.mdLink(linkTarget, linkText);
	}

	*makeArgString {|m, par=true|
		var res = "";
		var value;
		var l = m.argNames;
		var last = l.size-1;
		l.do {|a,i|
			if (i>0) { //skip 'this' (first arg)
				if(i==last and: {m.varArgs}) {
					res = res ++ " " ++ "... " ++ a;
				} {
					if (i>1) { res = res ++ ", " };
					res = res ++ "" ++ a;
					(value = m.prototypeFrame[i]) !? {
						value = if(value.class===Float) { value.asString } { value.cs };
						res = res ++ ": " ++ value;
					};
				};
			};
		};
		if (res.notEmpty and: par) {
			^("("++res++")");
		};
		^res;
	}

	*renderHeader {|stream, doc, body|
		var x, m, z;
		var thisIsTheMainHelpFile;
		var folder = doc.path.dirname;
		var undocumented = false;
		var displayedTitle;
		if(folder==".",{folder=""});

		// FIXME: use SCDoc.helpTargetDir relative to baseDir
		baseDir = ".";
		doc.path.occurrencesOf($/).do {
			baseDir = baseDir ++ "/..";
		};

		thisIsTheMainHelpFile = (doc.title == "Help") and: {
			(folder == "") or:
			{ (thisProcess.platform.name === \windows) and: { folder == "Help" } }
		};

		displayedTitle = if(
			thisIsTheMainHelpFile,
			{ "SuperCollider " ++ Main.version },
			{ doc.title }
		);

		// stream
		// << "<div id='toc'>\n"
		// << "<div id='toctitle'>" << displayedTitle << ":</div>\n"
		// << "<span class='toc_search'>Filter: <input id='toc_search'></span>";
		// this.renderTOC(stream, body);
		// stream << "</div>";

		stream << "# " << displayedTitle << "\n\n";

		if(doc.isClassDoc and: { currentClass.notNil } and: { currentClass != Object }) {
			stream << "> Superclasses: "
			<< (currentClass.superclasses.collect {|c|
				this.mdLink("../Classes/"++c.name++".html", c.name)
			}.join(" : "))
			<< "\n";
		};

		if(doc.isClassDoc) {
			if(currentClass.notNil) {
				m = currentClass.filenameSymbol.asString;
				stream << "> Source: " << this.mdLink(URI.fromLocalPath(m).asString, m.basename) << "\n";
				if(currentClass.subclasses.notNil) {
					z = false;
					stream
					<< "> Subclasses: "
					<< (currentClass.subclasses.collect(_.name).sort.collect {|c,i|
						this.mdLink("../Classes/"++c++".html", c)
					}.join(", "));
					stream << "\n";
				};
				if(currentImplClass.notNil) {
					stream << "Implementing class: " << this.mdLink("../Classes/"++currentImplClass.name++".html", currentImplClass.name) << "\n"
				};
			} {
				stream << "**NOT INSTALLED!**\n";
			};
		};

		if (links && doc.related)  {
			stream << "> See also: "
			<< (doc.related.collect {|r| this.mdForLink(r)}.join(", "))
			<< "\n";
		};

		if(doc.isExtension) {
			stream << "> This help file originates from a third-party quark or plugin for SuperCollider.\n"
		};

		stream << "\n" << this.escapeSpecialChars(doc.summary) << "\n\n";
	}

	*renderChildren {|stream, node, indentingWith=""|
		var oldIndent;
		oldIndent = indent;
		indent = indent ++ indentingWith;
		node.children.do {|child| this.renderSubTree(stream, child) };
		indent = oldIndent;
	}

	*renderMethod {|stream, node, methodType, cls, icls|
		var methodTypeIndicator;
		var methodCodePrefix;
		var args = node.text ?? ""; // only outside class/instance methods
		var names = node.children[0].children.collect(_.text);
		var mstat, sym, m, m2, mname2;
		var lastargs, args2;
		var x, maxargs = -1;
		var methArgsMismatch = false;

		methodTypeIndicator = switch(
			methodType,
			\classMethod, { "*" },
			\instanceMethod, { "-" },
			\genericMethod, { "" }
		);

		minArgs = inf;
		currentMethod = nil;

		names.do {|mname|
			methodCodePrefix = switch(
				methodType,
				\classMethod, { if(cls.notNil) { cls.name.asString[5..] } { "" } ++ "." },
				\instanceMethod, {
					// If the method name contains any valid binary operator character, remove the
					// "." to reduce confusion.
					if(mname.asString.any(this.binaryOperatorCharacters.contains(_)), { "" }, { "." })
				},
				\genericMethod, { "" }
			);

			stream << "**`";

			mname2 = this.escapeSpecialChars(mname);
			if(cls.notNil) {
				mstat = 0;
				sym = mname.asSymbol;
				//check for normal method or getter
				m = icls !? {icls.findRespondingMethodFor(sym.asGetter)};
				m = m ?? {cls.findRespondingMethodFor(sym.asGetter)};
				m !? {
					mstat = mstat | 1;
					args = this.makeArgString(m);
					args2 = m.argNames !? {m.argNames[1..]};
				};
				//check for setter
				m2 = icls !? {icls.findRespondingMethodFor(sym.asSetter)};
				m2 = m2 ?? {cls.findRespondingMethodFor(sym.asSetter)};
				m2 !? {
					mstat = mstat | 2;
					args = m2.argNames !? {this.makeArgString(m2,false)} ?? {"value"};
					args2 = m2.argNames !? {m2.argNames[1..]};
				};
				maxargs.do {|i|
					var a = args2 !? args2[i];
					var b = lastargs[i];
					if(a!=b and: {a!=nil} and: {b!=nil}) {
						methArgsMismatch = true;
					}
				};
				lastargs = args2;
				case
					{args2.size>maxargs} {
						maxargs = args2.size;
						currentMethod = m2 ?? m;
					}
					{args2.size<minArgs} {
						minArgs = args2.size;
					};
			} {
				m = nil;
				m2 = nil;
				mstat = 1;
			};

			x = {
				stream << methodCodePrefix << mname2;
			};

			switch (mstat,
				// getter only
				1, { x.value; stream << args; },
				// getter and setter
				3, { x.value; },
				// method not found
				0, {
					"SCDoc: In %\n"
					"  Method %% not found.".format(currDoc.fullPath, methodTypeIndicator, mname2).warn;
					x.value;
					stream << ": METHOD NOT FOUND!";
				}
			);

			stream << "`**";

			// has setter
			if(mstat & 2 > 0) {
				stream << "\n**`";
				x.value;
				if(args2.size<2) {
					stream << " = " << args << "";
				} {
					stream << "_(" << args << ")";
				};
				stream << "`**";
			};


			m = m ?? m2;
			m !? {
				if(m.isExtensionOf(cls) and: {icls.isNil or: {m.isExtensionOf(icls)}}) {
					stream << " From extension in " << this.mdLink(m.filenameSymbol);
				} {
					if(m.ownerClass == icls) {
						stream << "From implementing class";
					} {
						if(m.ownerClass != cls) {
							m = m.ownerClass.name;
							m = if(m.isMetaClassName) {m.asString.drop(5)} {m};
							stream << "From superclass: " << this.mdLink(baseDir++"/Classes/"++m++".md", m);
						}
					}
				};
			};
			stream << "\n";
		};


		if(methArgsMismatch) {
			"SCDoc: In %\n"
			"  Grouped methods % do not have the same argument signature."
			.format(currDoc.fullPath, names).warn;
		};

		// ignore trailing mul add arguments
		if(currentMethod.notNil) {
			currentNArgs = currentMethod.argNames.size;
			if(currentNArgs > 2
			and: {currentMethod.argNames[currentNArgs-1] == \add}
			and: {currentMethod.argNames[currentNArgs-2] == \mul}) {
				currentNArgs = currentNArgs - 2;
			}
		} {
			currentNArgs = 0;
		};

		if(node.children.size > 1) {
			noParBreak = true;
			this.renderChildren(stream, node.children[1]);
			stream << "\n";
		};

		stream << "\n";

		currentMethod = nil;
	}

	*renderSubTree {|stream, node|
		var f, z, img;
		switch(node.id,
			\PROSE, {
				if(noParBreak.notNil and: {noParBreak}) {
				} {
					stream << "\n" << indent << "\n" << indent;
				};
				this.renderChildren(stream, node);
				noParBreak = false;
			},
			\NL, { }, // these shouldn't be here..
// Plain text and modal tags
			\TEXT, {
				stream << this.escapeSpecialChars(node.text);
			},
			\LINK, {
				stream << this.mdForLink(node.text);
			},
			\CODEBLOCK, {
				stream << "\n" << indent << "```supercollider\n"
				<< this.escapeSpecialChars(node.text)
				<< "\n```\n";
			},
			\CODE, {
				stream << "`"
				<< this.escapeSpecialChars(node.text)
				<< "`";
			},
			\EMPHASIS, {
				stream << "*" << this.escapeSpecialChars(node.text) << "*";
			},
			\TELETYPEBLOCK, {
				stream << "`" << this.escapeSpecialChars(node.text) << "`";
			},
			\TELETYPE, {
				stream << "`" << this.escapeSpecialChars(node.text) << "`";
			},
			\STRONG, {
				stream << "**" << this.escapeSpecialChars(node.text) << "**";
			},
			\SOFT, {
				stream << this.escapeSpecialChars(node.text);
			},
			\ANCHOR, {
				//stream << "<a class='anchor' name='" << this.escapeSpacesInAnchor(node.text) << "'>&nbsp;</a>";
			},
			\KEYWORD, {
				node.children.do {|child|
					stream << "<a class='anchor' name='kw_" << this.escapeSpacesInAnchor(child.text) << "'>&nbsp;</a>";
				}
			},
			\IMAGE, {
				f = node.text.split($#);
				stream << "<div class='image'>";
				img = "<img src='" ++ f[0] ++ "'/>";
				if(f[2].isNil) {
					stream << img;
				} {
					stream << this.mdForLink(f[2]++"#"++(f[3]?"")++"#"++img,false);
				};
				f[1] !? { stream << "\n\n**" << f[1] << "**" }; // ugly..
				stream << "\n\n";
			},
// Other stuff
			\NOTE, {
				stream << "\n" << indent << "\n" << indent << "> **NOTE:** ";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\WARNING, {
				stream << "\n\n" << indent << "> **WARNING:**\n\n> ";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\FOOTNOTE, {
				footNotes = footNotes.add(node);
				stream << "[^" << footNotes.size << "]"
			},
			\CLASSTREE, {
				stream << "\n";
				this.renderClassTree(stream, node.text.asSymbol.asClass, "");
				stream << "\n";
			},
// Lists and tree
			\LIST, {
				stream << "\n" << indent;
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\TREE, {
				stream << "\n";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\NUMBEREDLIST, {
				stream << "\n";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\ITEM, { // for LIST, TREE and NUMBEREDLIST
				stream << "\n" << indent << "- ";
				noParBreak = true;
				this.renderChildren(stream, node);
			},
// Definitionlist
			\DEFINITIONLIST, {
				stream << "\n";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\DEFLISTITEM, {
				this.renderChildren(stream, node);
			},
			\TERM, {
				var idt = indent;
				if (idt.isEmpty) {idt = "  "};
				stream << "\n" << idt << "**";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "**";
			},
			\DEFINITION, {
				stream << ": *";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << "*\n";
			},
// Tables
			\TABLE, {
				stream << "|------|\n";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\TABROW, {
				stream << "|";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\TABCOL, {
				stream << " ";
				noParBreak = true;
				this.renderChildren(stream, node);
				stream << " |";
			},
// Methods
			\CMETHOD, {
				this.renderMethod(
					stream, node,
					\classMethod,
					currentClass !? {currentClass.class},
					currentImplClass !? {currentImplClass.class}
				);
			},
			\IMETHOD, {
				this.renderMethod(
					stream, node,
					\instanceMethod,
					currentClass,
					currentImplClass
				);
			},
			\METHOD, {
				this.renderMethod(
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
				stream << "\n\n### Arguments\n\n";
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
				this.renderChildren(stream, node, indentingWith: "  ");
				stream << "\n" << indent;
			},
			\ARGUMENT, {
				currArg = currArg + 1;
				stream << "\n" << indent;
				if(node.text.isNil) {
					currentMethod !? {
						if(currentMethod.varArgs and: {currArg==(currentMethod.argNames.size-1)}) {
							stream << "`...` ";
						};
						stream << if(currArg < currentMethod.argNames.size) {
							if(currArg > minArgs) {
								"(`"++currentMethod.argNames[currArg]++"`)";
							} {
								currentMethod.argNames[currArg];
							}
						} {
							"(`arg"++currArg++"`)" // excessive arg
						};
					};
				} {
					stream << "**" << if(currentMethod.isNil or: {currArg < currentMethod.argNames.size}) {
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
							"(`"++node.text++"`)"
						} {
							"`" ++ node.text ++ "`";
						};
					} {
						"(`"++node.text++"`)" // excessive arg
					};
					stream << "**";
				};
				if (node.children.size > 0) {
					stream << ": ";
					noParBreak = true;
					this.renderChildren(stream, node);
				}
			},
			\RETURNS, {
				stream << "\n### Returns\n\n";
				this.renderChildren(stream, node);
				stream << "\n";

			},
			\DISCUSSION, {
				stream << "\n\n#### Discussion\n\n";
				this.renderChildren(stream, node);
			},
// Sections
			\CLASSMETHODS, {
				if(node.notPrivOnly) {
					stream << "\n## Class Methods\n\n";
				};
				this.renderChildren(stream, node);
			},
			\INSTANCEMETHODS, {
				if(node.notPrivOnly) {
					stream << "\n## Instance Methods\n\n";
				};
				this.renderChildren(stream, node);
			},
			\DESCRIPTION, {
				stream << "\n## Description\n\n";
				this.renderChildren(stream, node);
				stream << "\n";
			},
			\EXAMPLES, {
				stream << "\n## Examples\n";
				this.renderChildren(stream, node);
			},
			\SECTION, {
				stream << "\n## " << this.escapeSpecialChars(node.text) << "\n\n";
				if(node.makeDiv.isNil) {
					this.renderChildren(stream, node);
				} {
					stream << "\n\n";
					this.renderChildren(stream, node);
					stream << "\n\n";
				};
			},
			\SUBSECTION, {
				stream << "\n\n### " << this.escapeSpecialChars(node.text) << "\n\n";
				if(node.makeDiv.isNil) {
					this.renderChildren(stream, node);
				} {
					stream << "\n\n";
					this.renderChildren(stream, node);
					stream << "\n\n";
				};
			},
			{
				this.renderChildren(stream, node);
			}
		);
	}

	*renderTOC {|stream, node|
		node.children !? {
			stream << "<ul class='toc'>";
			node.children.do {|n|
				switch(n.id,
					\DESCRIPTION, {
						stream << "<li class='toc1'><a href='#description'>Description</a></li>\n";
						this.renderTOC(stream, n);
					},
					\EXAMPLES, {
						stream << "<li class='toc1'><a href='#examples'>Examples</a></li>\n";
						this.renderTOC(stream, n);
					},
					\CLASSMETHODS, {
						if(n.notPrivOnly) {
							stream << "<li class='toc1'><a href='#classmethods'>Class methods</a></li>\n";
							this.renderTOC(stream, n);
						};
					},
					\INSTANCEMETHODS, {
						if(n.notPrivOnly) {
							stream << "<li class='toc1'><a href='#instancemethods'>Instance methods</a></li>\n";
							this.renderTOC(stream, n);
						};
					},
					\CMETHOD, {
						stream << "<li class='toc3'>"
						<< (n.children[0].children.collect{|m|
							"<a href='#*"++m.text++"'>"++this.escapeSpecialChars(m.text)++"</a> ";
						}.join(" "))
						<< "</li>\n";
					},
					\IMETHOD, {
						stream << "<li class='toc3'>"
						<< (n.children[0].children.collect{|m|
							"<a href='#-"++m.text++"'>"++this.escapeSpecialChars(m.text)++"</a> ";
						}.join(" "))
						<< "</li>\n";
					},
					\METHOD, {
						stream << "<li class='toc3'>"
						<< (n.children[0].children.collect{|m|
							"<a href='#."++m.text++"'>"++this.escapeSpecialChars(m.text)++"</a> ";
						}.join(" "))
						<< "</li>\n";
					},

					\SECTION, {
						stream << "<li class='toc1'><a href='#" << this.escapeSpacesInAnchor(n.text) << "'>"
						<< this.escapeSpecialChars(n.text) << "</a></li>\n";
						this.renderTOC(stream, n);
					},
					\SUBSECTION, {
						stream << "<li class='toc2'><a href='#" << this.escapeSpacesInAnchor(n.text) << "'>"
						<< this.escapeSpecialChars(n.text) << "</a></li>\n";
						this.renderTOC(stream, n);
					}
				);
			};
			stream << "</ul>";
		};
	}

	*addUndocumentedMethods {|list, body, id2, id, title|
		var l;
		if(list.size>0) {
			l = list.collectAs(_.asString,Array).sort.collect {|name|
				SCDocNode()
				.id_(id2)
				.children_([
					SCDocNode()
					.id_(\METHODNAMES)
					.children_([
						SCDocNode()
						.id_(\STRING)
						.text_(name.asString)
					])
				]);
			};
			body.addDivAfter(id, nil, title, l);
		}
	}

	*renderClassTree {|stream, cls, indent|
		var name, doc, desc = "";
		name = cls.name.asString;
		doc = SCDoc.documents["Classes/"++name];
		doc !? { desc = " - "++doc.summary };
		if(cls.name.isMetaClassName, {^this});
		stream << "\n- " << this.mdLink(baseDir++"/Classes/"++name++".html", name)
		<< name << " " << desc << "\n";

		cls.subclasses !? {
			cls.subclasses.copy.sort {|a,b| a.name < b.name}.do {|x|
				this.renderClassTree(stream, x, indent ++ "  ");
			};
		};
	}

	*renderFootNotes {|stream|
		if(footNotes.notNil) {
			stream << "<\n";
			footNotes.do {|n,i|
				stream << "[^" << (i+1) << "]: ";
				noParBreak = true;
				this.renderChildren(stream, n);
				stream << "\n";
			};
			stream << "\n";
		};
	}

	*renderFooter {|stream, doc|
		doc.fullPath !? {
			stream << "\n> helpfile source: " << this.mdLink(doc.fullPath, URI.fromLocalPath(doc.fullPath).asString) << "\n"
		};
	}

	*renderOnStream {|stream, doc, root, addLinks=false|
		var body = root.children[1];
		var redirect;
		currDoc = doc;
		footNotes = nil;
		noParBreak = false;
		links = addLinks;

		if(doc.isClassDoc) {
			currentClass = doc.klass;
			currentImplClass = doc.implKlass;
			// These lists could be large without folding (sevral hundered)
			// if(currentClass != Object) {
			// 	body.addDivAfter(\CLASSMETHODS,"inheritedclassmets","Inherited class methods");
			// 	body.addDivAfter(\INSTANCEMETHODS,"inheritedinstmets","Inherited instance methods");
			// };
			this.addUndocumentedMethods(doc.undoccmethods, body, \CMETHOD, \CLASSMETHODS, "Undocumented class methods");
			this.addUndocumentedMethods(doc.undocimethods, body, \IMETHOD, \INSTANCEMETHODS, "Undocumented instance methods");
			body.sortClassDoc;
		} {
			currentClass = nil;
			currentImplClass = nil;
		};

		this.renderHeader(stream, doc, body);
		this.renderChildren(stream, body);
		this.renderFootNotes(stream);
		this.renderFooter(stream, doc);
		currDoc = nil;
	}

	*renderToFile {|filename, doc, root|
		var stream;
		File.mkdir(filename.dirname);
		stream = File(filename, "w");
		if(stream.isOpen) {
			this.renderOnStream(stream, doc, root);
			stream.close;
		} {
			warn("SCDoc: Could not open file % for writing".format(filename));
		}
	}
}
