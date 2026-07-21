SCDocMarkdownRenderer {
    classvar currentClass, currentImplClass, currentMethod, currArg;
    classvar currentNArgs;
    classvar footNotes;
    classvar noParBreak;
    classvar currDoc;
    classvar minArgs;
    classvar baseDir;
    classvar <extension = ".md";
    
    // Escape special markdown characters (*, _, [, ], etc.)
    *escapeSpecialChars {|str|
        var x = "";
        var beg = -1, end = 0;
        str.do {|chr, i|
            switch(chr,
                $*, { x = x ++ str.copyRange(beg, i-1) ++ "\\*"; beg = i+1; },
                $_, { x = x ++ str.copyRange(beg, i-1) ++ "\\_"; beg = i+1; },
                $[, { x = x ++ str.copyRange(beg, i-1) ++ "\\["; beg = i+1; },
                $], { x = x ++ str.copyRange(beg, i-1) ++ "\\]"; beg = i+1; },
                $`, { x = x ++ str.copyRange(beg, i-1) ++ "\\`"; beg = i+1; },
                $#, { x = x ++ str.copyRange(beg, i-1) ++ "\\#"; beg = i+1; },
                $\\, { x = x ++ str.copyRange(beg, i-1) ++ "\\\\"; beg = i+1; },
                { end = i }
            );
        };
        if(beg<=end) {
            x = x ++ str[beg..end];
        };
        ^x;
    }
    
    // Convert spaces to %20 for anchor links (keep same as HTML)
    *escapeSpacesInAnchor { |str|
        // TODO: Handle anchor escaping for markdown links
        ^str.replace(" ", "%20")
    }
    
    // Find target for internal links - convert to markdown link format
    *prLinkTargetForInternalLink { |linkBase, linkAnchor, originalLink|
        var doc, result;
        
        if(linkBase.isEmpty) {
            result = "";
        } {
            doc = SCDoc.documents[linkBase];
            result = (baseDir ? "") +/+ linkBase;
            
            // If this is an existing document, add .md extension instead of .html
            if(doc.notNil) {
                result = result ++ ".md"
            } {
                // Check if the link target exists as a markdown file
                if(File.exists(SCDoc.helpTargetDir +/+ linkBase ++ ".md")) {
                    result = result ++ ".md"
                } {
                    // If the raw filepath exists, use it as-is
                    if(File.exists(SCDoc.helpTargetDir +/+ linkBase).not) {
                        "SCDocMarkdown: In %\n"
                            "  Broken link: '%'"
                            .format(currDoc !? _.fullPath ? "(unknown)", originalLink).warn;
                    };
                };
            };
        };
        
        // Use forward slash as path separator
        result = result.replace(Platform.pathSeparator, "/");
        
        if(linkAnchor.isEmpty) {
            ^result
        } {
            // For now, just append anchor with # - markdown heading links
            ^result ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor.toLower.replace(" ", "-"));
        }
    }
    
    // Create external link targets - simpler for markdown
    *prLinkTargetForExternalLink { |linkBase, linkAnchor|
        if(linkAnchor.isEmpty) {
            ^linkBase
        } {
            ^linkBase ++ "#" ++ this.escapeSpacesInAnchor(linkAnchor);
        }
    }
    
    // Generate link text for internal links
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
    
    // Convert link to markdown format [text](url)
    *markdownForLink { |link, escape = true|
        var linkBase, linkAnchor, linkText, linkTarget;
        
        // Get the link base, anchor, and text from the original string
        #linkBase, linkAnchor, linkText = link.split($#);
        linkBase = linkBase ? "";
        linkAnchor = linkAnchor ? "";
        linkText = linkText ? "";
        
        // Check if it's an external URL
        if("^[a-zA-Z]+://.+".matchRegexp(link) or: (link.first == $/)) {
            // External link
            linkText = if(linkText.isEmpty) { link } { linkText };
            linkTarget = this.prLinkTargetForExternalLink(linkBase, linkAnchor);
        } {
            // Internal link
            linkText = this.prLinkTextForInternalLink(linkBase, linkAnchor, linkText);
            linkTarget = this.prLinkTargetForInternalLink(linkBase, linkAnchor, link);
        };
        
        // Escape markdown characters in link text if requested
        if(escape) { linkText = this.escapeSpecialChars(linkText) };
        
        // Return markdown link format
        ^"[" ++ linkText ++ "](" ++ linkTarget ++ ")";
    }
    
    // Create argument string for methods (similar to HTML but markdown formatted)
    *makeArgString {|m, par=true|
        var res = "";
        var value;
        var l = m.argNames;
        var last = l.size - m.varArgsValue;
        l.do {|a,i|
            if (i > 0) { //skip 'this' (first arg)
                if(i >= last and: {m.hasVarArgs}) {
                    if(i == last){
                        if(i != 0){
                            res = res ++ " "
                        };
                        res = res ++ "`... " ++ a ++ "`"
                    } {
                        res = res ++ ", `... " ++ a ++ "`"
                    }
                } {
                    if (i>1) { res = res ++ ", " };
                    res = res ++ "`" ++ a ++ "`";
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
    
    // Render markdown header (title, frontmatter, etc.)
    *renderHeader {|stream, doc, body|
        // TODO: Create markdown frontmatter and title
        // Instead of HTML head/body, use markdown frontmatter
        stream << "# " << doc.title << "\n\n";
    }
    
    // Render child nodes recursively
    *renderChildren {|stream, node|
        // Same as HTML - iterate through children
        node.children.do {|child| this.renderSubTree(stream, child) };
    }
    
    // Render method documentation in markdown
    *renderMethod {|stream, node, methodType, cls, icls|
        var methodTypeIndicator;
        var args = node.text ?? "";
        var names = node.children[0].children.collect(_.text);
        var mstat, sym, m, m2, mname2;
        var lastargs, args2;
        var x, maxargs = -1;
        
        methodTypeIndicator = switch(
            methodType,
            \classMethod, { "*" },
            \instanceMethod, { "-" },
            \genericMethod, { "." }
        );
        
        minArgs = inf;
        currentMethod = nil;
        names.do {|mname|
            mname2 = this.escapeSpecialChars(mname);
            
            if(cls.notNil) {
                mstat = 0;
                sym = mname.asSymbol;
                
                // Check for normal method or getter
                m = icls !? {icls.findRespondingMethodFor(sym.asGetter)};
                m = m ?? {cls.findRespondingMethodFor(sym.asGetter)};
                m !? {
                    mstat = mstat | 1;
                    args = this.makeArgString(m);
                    args2 = m.argNames !? {m.argNames[1..]};
                };
                
                // Check for setter
                m2 = icls !? {icls.findRespondingMethodFor(sym.asSetter)};
                m2 = m2 ?? {cls.findRespondingMethodFor(sym.asSetter)};
                m2 !? {
                    mstat = mstat | 2;
                    args = m2.argNames !? {this.makeArgString(m2,false)} ?? {"value"};
                    args2 = m2.argNames !? {m2.argNames[1..]};
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
            
            stream << "\n### " << methodTypeIndicator << mname2;
            
            switch (mstat,
                // getter only
                1, { stream << args; },
                // getter and setter
                3, { },
                // method not found
                0, {
                    "SCDocMarkdown: In %\n"
                        "  Method %% not found.".format(currDoc.fullPath, methodTypeIndicator, mname2).warn;
                    stream << ": METHOD NOT FOUND!";
                }
            );
            
            stream << "\n";
            
            // has setter
            if(mstat & 2 > 0) {
                stream << "### " << methodTypeIndicator << mname2;
                if(args2.size<2) {
                    stream << " = " << args << "\n";
                } {
                    stream << "_(" << args << ")\n";
                }
            };
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
            this.renderChildren(stream, node.children[1]);
        };
        stream << "\n---\n";
        currentMethod = nil;
    }
    
    // Main rendering dispatcher - converts nodes to markdown
    *renderSubTree {|stream, node|
        switch(node.id,
            \PROSE, {
                if(noParBreak) {
                    noParBreak = false;
                } {
                    stream << "\n";
                };
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \NL, { }, // ignore newlines
            \TEXT, {
                stream << this.escapeSpecialChars(node.text);
            },
            \LINK, {
                stream << this.markdownForLink(node.text);
            },
            \CODEBLOCK, {
                stream << "\n```supercollider\n"
                    << node.text
                    << "\n```\n\n";
            },
            \CODE, {
                stream << "`" << this.escapeSpecialChars(node.text) << "`";
            },
            \EMPHASIS, {
                stream << "*" << this.escapeSpecialChars(node.text) << "*";
            },
            \TELETYPEBLOCK, {
                stream << "\n```\n" << node.text << "\n```\n\n";
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
                // Skip anchors for now - markdown heading links should handle most cases
            },
            \KEYWORD, {
                // Skip keyword anchors for now
            },
            \MATHBLOCK, {
                stream << "\n$$" << node.text << "$$\n\n";
            },
            \MATH, {
                stream << "$" << node.text << "$";
            },
            \IMAGE, {
                var f = node.text.split($#);
                stream << "\n![" << (f[1] ? "Image") << "](" << f[0] << ")";
                f[1] !? { stream << "\n\n*" << f[1] << "*" };
                stream << "\n\n";
            },
            \NOTE, {
                stream << "\n> **NOTE:** ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n\n";
            },
            \WARNING, {
                stream << "\n> **WARNING:** ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n\n";
            },
            \FOOTNOTE, {
                footNotes = footNotes.add(node);
                stream << "[^" << footNotes.size << "] ";
            },
            \CLASSTREE, {
                this.renderClassTree(stream, node.text.asSymbol.asClass);
            },
            \LIST, {
                stream << "\n";
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
            \ITEM, {
                stream << "- ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \DEFINITIONLIST, {
                stream << "\n";
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \DEFLISTITEM, {
                this.renderChildren(stream, node);
            },
            \TERM, {
                stream << "**";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "**\n";
            },
            \DEFINITION, {
                stream << ": ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n\n";
            },
            \TABLE, {
                stream << "\n";
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \TABROW, {
                stream << "| ";
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \TABCOL, {
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << " | ";
            },
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
                stream << "\n#### Arguments:\n";
                currArg = 0;
                this.renderChildren(stream, node);
            },
            \ARGUMENT, {
                currArg = currArg + 1;
                stream << "⇒ `";
                if(node.text.isNil) {
                    currentMethod !? {
                        stream << if(currArg < currentMethod.argNames.size) {
                            currentMethod.argNames[currArg];
                        } {
                            "arg" ++ currArg
                        };
                    };
                } {
                    stream << node.text;
                };
                stream << "`: ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \RETURNS, {
                stream << "\n#### Returns:\n⇐ ";
                noParBreak = true;
                this.renderChildren(stream, node);
                stream << "\n";
            },
            \DISCUSSION, {
                stream << "\n#### Discussion:\n";
                this.renderChildren(stream, node);
            },
            \CLASSMETHODS, {
                if(node.notPrivOnly) {
                    stream << "\n---\n\n## Class Methods\n";
                };
                this.renderChildren(stream, node);
            },
            \INSTANCEMETHODS, {
                if(node.notPrivOnly) {
                    stream << "\n---\n\n## Instance Methods\n";
                };
                this.renderChildren(stream, node);
            },
            \DESCRIPTION, {
                stream << "\n---\n\n## Description\n";
                this.renderChildren(stream, node);
            },
            \EXAMPLES, {
                stream << "\n---\n\n## Examples\n";
                this.renderChildren(stream, node);
            },
            \SECTION, {
                stream << "\n---\n\n## " << this.escapeSpecialChars(node.text) << "\n";
                this.renderChildren(stream, node);
            },
            \SUBSECTION, {
                stream << "\n### " << this.escapeSpecialChars(node.text) << "\n";
                this.renderChildren(stream, node);
            },
            \SUBSUBSECTION, {
                stream << "\n#### " << this.escapeSpecialChars(node.text) << "\n";
                this.renderChildren(stream, node);
            },
            {
                "SCDocMarkdown: In %\n"
                    "  Unknown SCDocNode id: %".format(currDoc.fullPath, node.id).warn;
                this.renderChildren(stream, node);
            }
        );
    }
    
    // Render table of contents as markdown
    *renderTOC {|stream, node|
        // TODO: Create markdown TOC
        // Could use a simple list or actual TOC syntax
    }
    
    // Add undocumented methods section
    *addUndocumentedMethods {|list, body, id2, id, title|
        // TODO: Same logic as HTML but markdown formatting
    }
    
    // Render class inheritance tree in markdown  
    *renderClassTree {|stream, cls|
        var name, doc, desc = "";
        name = cls.name.asString;
        doc = SCDoc.documents["Classes/"++name];
        doc !? { desc = " - "++doc.summary };
        if(cls.name.isMetaClassName, {^this});
        
        stream << "- [" << name << "](Classes/" << name << ".md)" << desc << "\n";
        
        cls.subclasses !? {
            cls.subclasses.copy.sort {|a,b| a.name < b.name}.do {|x|
                stream << "  ";  // indent for nested list
                this.renderClassTree(stream, x);
            };
        };
    }
    
    // Render footnotes in markdown
    *renderFootNotes {|stream|
        if(footNotes.notNil) {
            stream << "\n---\n\n";
            footNotes.do {|n,i|
                stream << "[^" << (i+1) << "]: ";
                noParBreak = true;
                this.renderChildren(stream, n);
                stream << "\n\n";
            };
        };
    }
    
    // Render footer with document info
    *renderFooter {|stream, doc|
        // TODO: Simple markdown footer
        stream << "\n---\n";
        stream << "*Generated from: " << doc.fullPath << "*\n";
    }
    
    // Render just method overview for IDE autocomplete/tooltips
    *renderMethodOverview {|stream, methodNode|
        var descNode;
        
        // Find the description/discussion part (usually the second child after method names)
        if(methodNode.children.size > 1) {
            descNode = methodNode.children[1];
            
            // Render child nodes but skip formal sections like ARGUMENTS, RETURNS
            descNode.children.do {|child|
                switch(child.id,
                    // Skip formal documentation sections
                    \ARGUMENTS, {},
                    \RETURNS, {},
                    \DISCUSSION, {
                        // Just render the content, not the header
                        this.renderChildren(stream, child);
                    },
                    // Render everything else (prose, examples, etc.)
                    {
                        this.renderSubTree(stream, child);
                    }
                );
            };
        };
    }
    
    // Render method signature with arguments for IDE display
    *renderMethodSignature {|stream, methodNode, methodType, cls, icls|
        var names = methodNode.children[0].children.collect(_.text);
        var methodTypeIndicator = switch(
            methodType,
            \classMethod, { "*" },
            \instanceMethod, { "-" },
            \genericMethod, { "." }
        );
        
        names.do {|mname, i|
            if(i > 0) { stream << ", " };
            stream << methodTypeIndicator << mname;
            
            // Add arguments if available
            if(cls.notNil) {
                var sym = mname.asSymbol;
                var m = icls !? {icls.findRespondingMethodFor(sym.asGetter)};
                m = m ?? {cls.findRespondingMethodFor(sym.asGetter)};
                m !? {
                    stream << this.makeArgString(m);
                };
            };
        };
    }
    
    // Render complete method info for IDE tooltip (signature + brief description)
    *renderMethodTooltip {|methodNode, methodType, cls, icls|
        var stream = CollStream.new;
        
        // Render signature
        this.renderMethodSignature(stream, methodNode, methodType, cls, icls);
        stream << "\n\n";
        
        // Render brief overview
        this.renderMethodOverview(stream, methodNode);
        
        ^stream.contents;
    }
    
    // Extract just the first paragraph/sentence of method description
    *renderMethodBrief {|methodNode|
        var stream = CollStream.new;
        var descNode, firstProse;
        
        if(methodNode.children.size > 1) {
            descNode = methodNode.children[1];
            
            // Find the first PROSE node (paragraph)
            firstProse = descNode.children.detect {|child| child.id == \PROSE };
            firstProse !? {
                this.renderSubTree(stream, firstProse);
            };
        };
        
        ^stream.contents.trim;
    }
    
    // Extract argument documentation as a dictionary
    *renderMethodArguments {|methodNode, method|
        var argDict = IdentityDictionary.new;
        var descNode, argsNode;
        var argIndex = 0;
        
        if(methodNode.children.size > 1) {
            descNode = methodNode.children[1];
            
            // Find the ARGUMENTS section
            argsNode = descNode.children.detect {|child| child.id == \ARGUMENTS };
            argsNode !? {
                argsNode.children.do {|argNode|
                    if(argNode.id == \ARGUMENT) {
                        var argName, argDesc;
                        var stream = CollStream.new;
                        
                        argIndex = argIndex + 1;
                        
                        // Get argument name
                        if(argNode.text.notNil) {
                            // Explicit argument name from doc
                            argName = argNode.text.asSymbol;
                        } {
                            // Infer from method signature
                            method !? {
                                argName = if(argIndex < method.argNames.size) {
                                    method.argNames[argIndex];
                                } {
                                    ("arg" ++ argIndex).asSymbol
                                };
                            };
                        };
                        
                        // Render argument description
                        currArg = argIndex;
                        noParBreak = true;
                        this.renderChildren(stream, argNode);
                        argDesc = stream.contents.trim;
                        
                        // Store in dictionary
                        argName !? {
                            argDict[argName] = argDesc;
                        };
                    };
                };
            };
        };
        
        ^argDict;
    }
    
    // Get return value documentation
    *renderMethodReturns {|methodNode|
        var descNode, returnsNode;
        var stream = CollStream.new;
        
        if(methodNode.children.size > 1) {
            descNode = methodNode.children[1];
            
            // Find the RETURNS section
            returnsNode = descNode.children.detect {|child| child.id == \RETURNS };
            returnsNode !? {
                noParBreak = true;
                this.renderChildren(stream, returnsNode);
            };
        };
        
        ^stream.contents.trim;
    }
    
    // Get complete method documentation as structured data
    *parseMethodDocumentation {|methodNode, methodType, cls, icls|
        var methodNames = methodNode.children[0].children.collect(_.text);
        var method, methodInfo;
        
        // Try to find the actual method object
        if(cls.notNil and: methodNames.notEmpty) {
            var sym = methodNames[0].asSymbol;
            method = icls !? {icls.findRespondingMethodFor(sym.asGetter)};
            method = method ?? {cls.findRespondingMethodFor(sym.asGetter)};
        };
        
        methodInfo = (
            names: methodNames,
            type: methodType,
            brief: this.renderMethodBrief(methodNode),
            arguments: this.renderMethodArguments(methodNode, method),
            returns: this.renderMethodReturns(methodNode),
            signature: {
                var stream = CollStream.new;
                this.renderMethodSignature(stream, methodNode, methodType, cls, icls);
                stream.contents;
            }.value
        );
        
        ^methodInfo;
    }
    
    // Render a specific section node with proper state initialization
    *renderSection {|stream, doc, root, sectionId|
        var body = root.children[1];
        var node;

        currDoc = doc;
        footNotes = nil;
        noParBreak = false;

        if(doc.isClassDoc) {
            currentClass = doc.klass;
            currentImplClass = doc.implKlass;
        } {
            currentClass = nil;
            currentImplClass = nil;
        };

        node = body.children.detect { |n| n.id == sectionId };
        node !? { this.renderChildren(stream, node) };
        currDoc = nil;
    }

    // Main rendering method - orchestrates the whole process
    *renderOnStream {|stream, doc, root|
        // TODO: Same orchestration as HTML but for markdown
        var body = root.children[1];
        currDoc = doc;
        footNotes = nil;
        noParBreak = false;
        
        if(doc.isClassDoc) {
            currentClass = doc.klass;
            currentImplClass = doc.implKlass;
            // TODO: Handle class-specific processing
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
    
    // Render to file (same as HTML version)
    *renderToFile {|filename, doc, root|
        var stream;
        File.mkdir(filename.dirname);
        stream = File(filename, "w");
        if(stream.isOpen) {
            this.renderOnStream(stream, doc, root);
            stream.close;
        } {
            warn("SCDocMarkdown: Could not open file % for writing".format(filename));
        }
    }
}