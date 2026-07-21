// LSPDatabase is a container for pure functions that return metadata about open documents or
// sclang instance details like lists of classes. Functions are expected to be called inside of
// a Routine and should yield during long operations. These should be considered good candidates
// for caching.

LSPDatabase {
    classvar allMethodNames, allMethods, allClasses, allMethodsByName, methodLocations;
    classvar classSymbols, methodSymbols, allSymbolObjects, docRegionsCache;
    classvar <>renderClassHoverFunc, <>renderMethodHoverFunc, <>renderEnvVarHoverFunc;
    classvar <>methodHoverCount = 10;
    
    *initClass {
        methodLocations = ();
        docRegionsCache = Dictionary();
        
        renderClassHoverFunc = { |class| LSPDatabase.prDefaultClassHover(class) };
        renderMethodHoverFunc = { |methodName| LSPDatabase.prDefaultMethodHover(methodName) };
        renderEnvVarHoverFunc = { |name| LSPDatabase.prDefaultEnvVarHover(name) };
    }
    
    *methodSortFunc {
        ^{
            |a, b|
            if (a.name < b.name) {
                true
            } {
                if (a.name > b.name) {
                    false
                } {
                    a.ownerClass.name < b.ownerClass.name
                }
            }
        }
    }
    
    *rangeForItems {
        |list, function, newObjectA, newObjectB|
        var a, b;
        a = this.indexForItem(list, function, newObjectA);
        b = this.indexForItem(list, function, newObjectB, a);
        ^Range(a, b-a-1);
    }
    
    *indexForItem {
        |list, function, newObject, low=0|
        
        var index;
        var high = list.size-1;
        
        while ({
            index = high + low div: 2;
            low <= high;
        }, {
            if (function.value(list.at(index), newObject), {
                low = index + 1;
            },{
                high = index - 1;
            });
        });
        
        ^low
    }
    
    *uniqueMethodsForClass {
        |class|
        var methods = class.methods;
        methods = Array.newFrom(methods).sort({
            |a, b|
            a.name < b.name
        });
        ^methods;
    }
    
    *methodsForClass {
        |class|
        var result, methodNames = IdentitySet(), methods = [];
        
        (class.superclasses.reverse ++ [class]).reverse.do {
            |class|
            this.uniqueMethodsForClass(class).do {
                |method|
                if (methodNames.includes(method.name).not) {
                    methodNames.add(method.name);
                    methods = methods.add(method);
                }
            }
        };
        
        ^[
            methods.collect(_.name),
            methods
        ]
    }
    
    *allMethodNames {
        if (allMethodNames.notNil) { ^allMethodNames };
        
        allMethodNames = this.allMethods.collect(_.name);
        allMethodNames.freeze;
        
        ^allMethodNames;
    }
    
    *allClasses {
        ^allClasses ?? {
            allClasses = Class.allClasses.sort({ |a, b| a.name.asString.toLower < b.name.asString.toLower })
        }
    }
    
    *allMethods {
        if (allMethods.notNil) { ^allMethods };
        
        Class.allClasses.do {
            |class|
            allMethods = allMethods.addAll(LSPDatabase.uniqueMethodsForClass(class));
        };
        
        allMethods = allMethods.sort(this.methodSortFunc);
        allMethods.freeze;
        
        ^allMethods
    }
    
    *allMethodsByName {
        if (allMethodsByName.notNil) { ^allMethodsByName };
        
        allMethodsByName = ();
        this.allMethods.do {
            |method|
            allMethodsByName[method.name] = allMethodsByName[method.name].add(method);
        };
        
        ^allMethodsByName;
    }
    
    *matchMethods {
        |startsWith|
        var range, allMethodNames, endString;
        
        allMethodNames = this.allMethodNames;
        
        if (startsWith.size == 0) {
            ^Range(0, allMethodNames.size)
        };
        
        endString = startsWith.copy;
        endString[endString.size-1] = (endString[endString.size-1].asUnicode + 1).asAscii;
        
        range = this.rangeForItems(
            allMethodNames, { |a, b| a < b },
            startsWith.asSymbol, endString.asSymbol
        );
        
        ^range
    }
    
    *methodsForName {
        |methodName|
        ^this.allMethodsByName[methodName.asSymbol]
    }
    
    *methodArgString {
        |method|
        ^"(%)".format(
            (method.argNames !? _[1..] ?? []).join(", ")
        )
    }
    
    *methodArgDefaultString {
        |method|
        ^"(%)".format(
            ((method.argNames !? _[1..]) !? _.collect({
                |argName, i|
                "%%".format(
                    argName,
                    method.prototypeFrame[i + 1] !? format(" = %", _) ?? ""
                )
            }) ?? []).join(", "),
        )
    }
    
    *methodInsertString {
        |method|
        ^"%(%)${0}".format(
            method.name,
            (method.argNames !? _[1..] ?? []).collect({
                |a, i|
                "${%:%}".format(i+1, a)
            }).join(", ")
        )
    }
    
    *methodDocumentationString {
        |method|
    }
    
    *findDefinitions {
        |word|
        var methods, asClass;
        
        if (word.isClassName and: { (asClass = word.asClass).notNil }) {
            ^[this.renderClassLocation(asClass)]
        } {
            methods = this.methodsForName(word);
            
            ^methods.collect {
                |method|
                this.renderMethodLocation(method)
            }
        }
    }
    
    *renderMethodRange {
        |method|
        var file = File(method.filenameSymbol.asString, "r");
        var methodFileSource = file.readAllString();
        var lineChar = methodFileSource.charToLineChar(method.charPos);
        
        file.close();
        
        ^(
            start: (
                line: lineChar[0],
                character: lineChar[1]
            ),
            end: (
                line: lineChar[0],
                character: lineChar[1]
            )
        )
    }
    
    *renderClassRange {
        |class|
        // Lucky us, these implementations are identical for now.
        ^this.renderMethodRange(class)
    }
    
    *renderMethodLocation {
        |method|
        ^(
            uri: method.filenameSymbol.asString.pathToFileURI,
            range: this.renderMethodRange(method)
        )
    }
    
    *renderClassLocation {
        |class|
        ^(
            uri: class.filenameSymbol.asString.pathToFileURI,
            range: this.renderClassRange(class)
        )
    }
    
    *makeMethodCompletion {
        |method, sortByClassHierarchy=false|
        var sortText;
        
        method ?? {
            ^nil  
        };
        
        if (sortByClassHierarchy) {
            sortText = (9 - method.ownerClass.superclasses.size).asString.zeroPad()
        } {
            sortText = "%:%".format(method.ownerClass.name, method.name)
        };
        
        ^(
            label: method.name.asString,
            labelDetails: (
                detail: 			LSPDatabase.methodArgString(method),
                description: 		method.ownerClass.name.asString
            ),
            kind: 1, 				// CompletionItemKind.Method
            // deprecated: false,	// mark this as deprecated - no way to use this?
            // detail:				// @TODO: additional detail
            // documentation: 		// @TODO: method documentation
            // detail:			    "detail", // @TODO: additional detail
            // documentation: 	    ( // @TODO: doc string
            // 	kind: 				"markdown",
            // 	value: 				" *Documentation* **goes** here",
            // 	isTrusted: 			true,
            // 	supportThemeIcons: 	true
            // ),
            sortText:				sortText,
            filterText: 			method.name.asString,
            // preselect: 			false,
            insertText:				LSPDatabase.methodInsertString(method),
            insertTextFormat: 		2, // Snippet,
            // range: (),
            // commitCharacters: 		["("]
        )
    }
    
    *methodDetails {
        |method|
        ^(
            detail: this.methodArgString(method),
            description: "%:%".format(method.ownerClass.name, method.name)
        )
    }
    
    *methodDocString {
        |method|
        var node, doc, stream, methodType;
        node = method.docNode;
        if (node.isNil) {
            ^""
        };
        
        try {
            stream = CollStream("");
            SCDocHTMLRenderer.renderMethod(stream, node, \genericMethod, method.ownerClass);
            ^stream.collection;
        } {
            |e|
            e.reportError;
            ^""
        }
    }
    
    *methodSignature {
        |method|
        var args, argDocs, methodDoc;
        args = method.argNames !? _[1..] ?? [];
        // methodDoc = LSPDatabase.methodDocString(method);
        
        ^(
            label: "%:%%".format(
                method.ownerClass.name, 
                method.name,
                this.methodArgDefaultString(method)
            ),
            documentation: (
                kind: "markdown",
            ),
            parameters: args.collect {
                |argument, i|
                (
                    label: argument
                )
            }
        )
    }
    
    *constructorSignature {
        |method|
        var args, argDocs, methodDoc;
        args = method.argNames !? _[1..] ?? [];
        // methodDoc = LSPDatabase.methodDocString(method);
        
        ^(
            label: "%%".format(
                method.ownerClass.name.asString.replace("Meta_", ""), 
                this.methodArgDefaultString(method)
            ),
            documentation: (
                kind: "markdown",
            ),
            parameters: args.collect {
                |argument, i|
                (
                    label: argument
                )
            }
        )
    }
    
    *classHoverInfo {
        |class|
        ^renderClassHoverFunc.value(class)
    }
    
    *methodHoverInfo {
        |methodName|
        ^renderMethodHoverFunc.value(methodName)
    }
    
    *envVarHoverInfo {
        |name|
        ^renderEnvVarHoverFunc.value(name)
    }
    
    *prDefaultClassHover {
        |class|
        var stream, doc, root, descStream
            ;
        
        // Resolve metaclasses to their real class
        if (class.isMetaClass) {
            class = class.name.asString.replace("Meta_", "").asSymbol.asClass;
            class ?? { ^nil };
        };
        
        doc = SCDoc.documents["Classes/" ++ class.name];
        
        if (doc.notNil) {
            try {
                root = doc.fullPath !? { SCDoc.parseFileFull(doc.fullPath) };
                root !? {
                    descStream = CollStream("");
                    SCDocMarkdownRenderer.renderSection(descStream, doc, root, \DESCRIPTION);
                };
            } { };
        };
        
        stream = CollStream("");
        
        // Header
        stream << "**" << class.name;
        class.superclass !? { stream << " : " << class.superclass.name };
        stream << "** <sup>ᴄʟᴀꜱꜱ</sup>\n\n---\n\n";
        
        // Description or fallback
        if (descStream.notNil and: { descStream.collection.size > 0 }) {
            stream << descStream.collection;
        } {
            // Fallback: class hierarchy + method list
            {
                var allMethods = (class.class.methods ?? []) ++ (class.methods ?? []);
                var sorted = allMethods.sort { |a, b| a.name < b.name };
                var classMethods = sorted.select { |m| m.ownerClass.isMetaClass };
                var instanceMethods = sorted.reject { |m| m.ownerClass.isMetaClass };
                
                stream << "```supercollider\n";
                if (classMethods.notEmpty) {
                    classMethods.do { |m|
                        stream << "*" << m.name << this.methodArgDefaultString(m) << "\n";
                    };
                };
                if (instanceMethods.notEmpty) {
                    if (classMethods.notEmpty) { stream << "\n" };
                    instanceMethods.do { |m|
                        stream << m.name << this.methodArgDefaultString(m) << "\n";
                    };
                };
                stream << "```\n";
            }.value;
        };
        
        // Per-class hover content (method help etc.)
        class.prClassHoverInfo !? { |extra|
            stream << "\n---\n\n" << extra;
        };
        
        ^stream.collection
    }
    
    *renderMethodHelp {
        |class ...methodNames|
        var doc, root, stream, rendered
            ;
        
        doc = SCDoc.documents["Classes/" ++ class.name];
        doc ?? { ^nil };
        
        try {
            root = doc.fullPath !? { SCDoc.parseFileFull(doc.fullPath) };
        } { };
        root ?? { ^nil };
        
        stream = CollStream("");
        rendered = IdentitySet();
        
        methodNames.do { |methodName|
            var result = this.prFindMethodNode(doc, root, methodName.asString);
            result !? { |r|
                var node = r[0], secId = r[1];
                if (rendered.includes(node).not) {
                    rendered.add(node);
                    SCDocMarkdownRenderer.renderSection(CollStream(""), doc, root, secId);
                    if (rendered.size > 1) { stream << "\n---\n\n" };
                    SCDocMarkdownRenderer.renderSubTree(stream, node);
                };
            };
        };
        
        if (stream.collection.size > 0) {
            ^stream.collection
        };
        
        ^nil
    }
    
    *prDefaultMethodHover {
        |methodName|
        var methods, stream, limit;
        
        limit = methodHoverCount;
        methods = this.methodsForName(methodName.asSymbol);
        if (methods.isNil or: { methods.isEmpty }) { ^nil };
        
        stream = CollStream("");
        stream << "**" << methodName << "** <sup>ᴍᴇᴛʜᴏᴅ</sup>\n\n---\n\n";
        
        methods[0 .. (limit - 1)].do { |method|
            var className, summary
                ;
            
            className = method.ownerClass.name.asString;
            
            // Signature line
            stream << "`";
            if (method.ownerClass.isMetaClass) {
                stream << className.replace("Meta_", "") << ":\\*" << method.name;
            } {
                stream << className << ":" << method.name;
            };
            stream << this.methodArgDefaultString(method);
            stream << "`";
            
            // First prose line from help if available
            summary = this.prMethodSummaryLine(method);
            summary !? { stream << " — " << summary };
            
            stream << "\n\n";
        };
        
        if (methods.size > limit) {
            stream << "*... and " << (methods.size - limit) << " more implementations*\n\n";
        };
        
        
        ^stream.collection
    }
    
    *prMethodSummaryLine {
        |method|
        var className, doc, root, result, node, body, prose, textNode
            ;
        
        className = method.ownerClass.name.asString.replace("Meta_", "");
        doc = SCDoc.documents["Classes/" ++ className];
        doc ?? { ^nil };
        
        try {
            root = doc.fullPath !? { SCDoc.parseFileFull(doc.fullPath) };
            root ?? { ^nil };
            
            result = this.prFindMethodNode(doc, root, method.name.asString);
            result ?? { ^nil };
            
            node = result[0];
            body = node.children[1]; // METHODBODY
            prose = body.children.detect { |c| c.id == \PROSE };
            prose ?? { ^nil };
            
            textNode = prose.children.detect { |c| c.id == \TEXT };
            textNode !? { ^textNode.text };
        } { };
        
        ^nil
    }
    
    *prDefaultEnvVarHover {
        |name|
        var sym, val, stream;
        
        sym = name.asSymbol;
        val = currentEnvironment[sym];
        if (val.isNil) { ^nil };
        
        stream = CollStream("");
        stream << "**~" << name << "** <sup>ᴇɴᴠ ᴠᴀʀ</sup>\n\n---\n\n";
        stream << "```supercollider\n" << val.asCompileString << "\n```\n\n";
        
        ^stream.collection
    }
    
    *prFindMethodNode {
        |doc, root, methodName|
        var body, found, foundSecId,
            methodIds, searchNodes
            ;
        
        body = root.children[1];
        methodIds = [\CMETHOD, \IMETHOD, \METHOD];
        
        searchNodes = { |nodes|
            nodes.do { |node|
                if (methodIds.indexOfEqual(node.id).notNil) {
                    var names = node.children[0].children.collect(_.text);
                    if (names.indexOfEqual(methodName).notNil) {
                        found = node;
                    };
                } {
                    if (node.id == \SUBSECTION) {
                        searchNodes.(node.children);
                    };
                };
            };
        };
        
        [\CLASSMETHODS, \INSTANCEMETHODS].do { |secId|
            body.children.do { |section|
                if (section.id == secId) {
                    searchNodes.(section.children);
                    if (found.notNil and: { foundSecId.isNil }) {
                        foundSecId = secId;
                    };
                };
            };
        };
        
        found ?? { ^nil };
        ^[found, foundSecId]
    }
    
    *prRenderMethodHelp {
        |doc, root, methodName|
        var result, stream;
        
        result = this.prFindMethodNode(doc, root, methodName);
        result ?? { ^nil };
        
        // Initialize renderer state, then render method node
        SCDocMarkdownRenderer.renderSection(CollStream(""), doc, root, result[1]);
        stream = CollStream("");
        SCDocMarkdownRenderer.renderSubTree(stream, result[0]);
        ^stream
    }
    
    *defClassHoverInfo {
        |class|
        var names, stream;
        
        if (class.respondsTo(\isDefClass).not or: { class.isDefClass.not }) { ^nil };
        
        names = class.prGetNames.asArray.sort;
        if (names.isEmpty) { ^nil };
        
        stream = CollStream("");
        stream << "## " << class.name << "\n\n";
        stream << names.size << " registered:\n\n";
        stream << "```\n";
        
        names[0 .. 19].do { 
            |name|
            var obj = class.prAtName(name.asSymbol);
            stream 
                << ("\\" ++ name ++ " = ").padLeft(24)
                << (
                    (obj.tryPerform(\source) ? obj)
                    .asCompileString.replace("\n", " ")[0..32]
                )
                << "\n";
        };
        
        stream << "```\n";
        
        if (names.size > 20) {
            stream << "\n*... and " << (names.size - 20) << " more*\n";
        };
        
        ^stream.collection.postln
    }
    
    *getReferences {
        |word|
        var references = Class.findAllReferences(word.asSymbol);
        
        ^references.collect {
            |method|
            this.renderMethodLocation(method)
        }
    }
    
    *getDefinitionsForWord {
        |word|
        var references = Class.findAllReferences(word.asSymbol);
        
        ^references.collect {
            |method|
            this.renderMethodLocation(method)
        }
    }
    
    *getDocumentLine {
        |doc, line|
        ^doc.getLine(line)
    }
    
    *getDocumentWordAt {
        |doc, line, character|
        var lineString = this.getDocumentLine(doc, line);
        var start = character;
        var word;
        var isWord = {
            |ch|
            ch !? { ch.isAlphaNum or: { ch == $_ } or: { ch == $~ } } ?? { false }
        };
        
        Log('LanguageServer.quark').info("Searching line for a word: '%' at %:%", lineString, line, character);
        
        if (not(isWord.(lineString[start])) and: {
            isWord.(lineString[(start - 1).max(0)])
        }) {
            start = start - 1;
        };
        
        while {
            (start >= 0) and: { isWord.(lineString[start]) }
        } {
            start = start - 1
        };
        start = start + 1;
        word = lineString.findRegexpAt("~?[A-Za-z][\\w]*", start);
        if (word.size > 0) {
            ^word[0]
        } {
            ^nil
        }
    }
    
    *getDocumentRegions {
        |doc|
        var cached = docRegionsCache[doc.path];
        // Heuristic scan, not a real parse — see Test/LSP-regions-test.scd for covered cases.
        var lines = doc.string.split($\n);
        // A region starts with "(" at column 0, followed only by whitespace and/or an
        // optional comment (whose text becomes the region name). Code after "(" is not a region.
        var startRe = "^\\(\\s*(?:|//\\s*(.*?)|/\\*\\s*(.*?)\\s*\\*/)\\s*$";
        var regionStack=[], nameStack=[], regions=[];
        var regionDepth = 0;
        var inString = false;
        var inSymbol = false;
        var commentDepth = 0;
        var inLineComment = false;
        var escaped = false;

        if (cached.notNil) {
            if (cached[\version] == doc.version) {
                ^cached[\value]
            }
        };

        lines.do {
            |line, lineNum|
            var start, name;
            var lastCharacter;

            if (inString.not && (commentDepth == 0)
                and: { (start = line.findRegexp(startRe)).notEmpty }) {

                regionStack = regionStack.add((
                    start: (line: lineNum, character: 0, depth: regionDepth)
                ));

                name = if (start[1][1].size > 0) { start[1][1] } { start[2][1] };
                if (name.size > 0) {
                    nameStack = nameStack.add(name);
                } {
                    nameStack = nameStack.add("[block %]".format(regions.size + nameStack.size));
                }
            };

            line.do {
                |character, i|
                var consumed = false;

                case
                { escaped } {
                    escaped = false;
                }
                { inLineComment } {
                    // rest of line is a comment
                }
                { commentDepth > 0 } {
                    if ((character == $*) && (lastCharacter == $/)) {
                        commentDepth = commentDepth + 1;
                        consumed = true;
                    };
                    if ((character == $/) && (lastCharacter == $*)) {
                        commentDepth = commentDepth - 1;
                        consumed = true;
                    };
                }
                { inString } {
                    if (character == $\\) { escaped = true };
                    if (character == $") { inString = false };
                }
                { inSymbol } {
                    if (character == $\\) { escaped = true };
                    if (character == $') { inSymbol = false };
                }
                { lastCharacter == $$ } {
                    // character literal like $( $) $" — skip it
                    consumed = true;
                }
                {
                    if ((character == $/) && (lastCharacter == $/)) {
                        inLineComment = true;
                    };
                    if ((character == $*) && (lastCharacter == $/)) {
                        commentDepth = 1;
                        consumed = true;
                    };
                    if (character == $") { inString = true };
                    if (character == $') { inSymbol = true };

                    if (inLineComment.not && (commentDepth == 0)) {
                        if (character == $() {
                            regionDepth = regionDepth + 1;
                        };

                        if (character == $)) {
                            regionDepth = regionDepth - 1;
                        };

                        if (regionStack.isEmpty.not and:{ regionStack.last[\start][\depth] == regionDepth }) {
                            regionStack.last.put(
                                \end,
                                (line: lineNum, character: i + 1)
                            );
                            regions = regions.add((
                                range: regionStack.removeAt(regionStack.size-1),
                                text: nameStack.removeAt(nameStack.size-1)
                            ));
                        }
                    };
                };

                lastCharacter = if (consumed) { nil } { character };
            };

            inLineComment = false;
            inSymbol = false;   // quoted symbols cannot span lines
            escaped = false;    // an escaped newline never escapes the next line's first char
        };

        docRegionsCache[doc.path] = (
            version: doc.version,
            value: regions
        );

        ^regions
    }
    
    *renderClassWorkspaceSymbol {
        |class|
        ^(
            name: 		class.name,
            kind: 		5, // class
            location: 	this.renderClassLocation(class)
        )
    }
    
    *renderMethodWorkspaceSymbol {
        |method|
        ^(
            name: 		"%:%".format(method.ownerClass.name, method.name),
            kind: 		6, // method
            location: 	this.renderMethodLocation(method),
            containerName: method.ownerClass.name
        )
    }
    
    *renderSymbolObject {
        |obj|
        if (obj.isKindOf(Method)) {
            ^LSPDatabase.renderMethodWorkspaceSymbol(obj)
        } {
            ^LSPDatabase.renderClassWorkspaceSymbol(obj)
        }
    }
    
    *renderClassNameCompletion {
        |class|
        var name = class.name.asString;
        ^(
            label: name,
            kind: 7, 				// CompletionItemKind.Class
            // deprecated: false,	// mark this as deprecated - no way to use this?
            // detail:				// @TODO: additional detail
            // documentation: 		// @TODO: method documentation
            // detail:			    "detail", // @TODO: additional detail
            // documentation: 	    ( // @TODO: doc string
            // 	kind: 				"markdown",
            // 	value: 				" *Documentation* **goes** here",
            // 	isTrusted: 			true,
            // 	supportThemeIcons: 	true
            // ),
            sortText:				name,
            filterText: 			name,
            // preselect: 			false,
            insertText:				name,
            insertTextFormat: 		2, // Snippet,
        )
    }
    
    *allSymbolObjects {
        ^allSymbolObjects ?? {
            allSymbolObjects = LSPDatabase.allMethods ++ LSPDatabase.allClasses;
            allSymbolObjects = allSymbolObjects.collect {
                |o|
                [o.name.asString.toLower, o]
            };
            
            allSymbolObjects.sort {
                |a, b|
                a[0] < b[0]
            };
            
            allSymbolObjects = allSymbolObjects.collect(_[1]);
        }
    }
    
    *findClasses {
        |query, limit=20|
        var symbolObjects = LSPDatabase.allClasses;
        var result = Array(limit);
        var index;
        
        index = LSPDatabase.findSymbolStartIndex(query, symbolObjects);
        limit = index + limit;
        
        while { index < limit and: { 
            symbolObjects[index] !? {
                |cls|
                cls.name.asString.beginsWith(query)
            } ?? false 
        }} {
            result = result.add(symbolObjects[index]);
            index = index + 1;
        };
        
        ^result.collect {
            |symbolObj|
            LSPDatabase.renderClassNameCompletion(symbolObj)
        }
    }
    
    *findSymbols {
        |query, limit=20|
        var symbolObjects = LSPDatabase.allSymbolObjects;
        var result = Array(limit);
        var index;
        
        index = LSPDatabase.findSymbolStartIndex(query, symbolObjects);
        limit = index + limit;
        
        query = query.toLower;
        while { index < limit and: { symbolObjects[index].name.asString.toLower.beginsWith(query) }} {
            result = result.add(symbolObjects[index]);
            index = index + 1;
        };
        
        ^result.collect {
            |symbolObj|
            LSPDatabase.renderSymbolObject(symbolObj)
        }
    }
    
    *findSymbolStartIndex {
        |query, all|
        var index;
        var low = 0;
        var high = all.size-1;
        
        query = query.toLower;
        
        while {
            index = high + low div: 2;
            low <= high;
        } {
            if (all[index].name.asString.toLower < query) {
                low = index + 1;
            } {
                high = index - 1;
            };
        };
        
        ^low
    }
}

+String {
    charToLineChar {
        |absoluteChar|
        var char = 0, line = 0, lineStartChar = 0;
        absoluteChar = min(absoluteChar, this.size);
        
        while { char < absoluteChar } {
            if (this[char] == Char.nl) {
                lineStartChar = char + 1;
                line = line + 1
            };
            char = char + 1
        };
        
        ^[line, char - lineStartChar]
    }
}

+Object {
    *prClassHoverInfo { ^LSPDatabase.renderMethodHelp(this, \new) }
}

+UGen {
    *prClassHoverInfo { ^LSPDatabase.renderMethodHelp(this, \ar, \kr) }
}

+Method {
    docNode {
        var owner = this.ownerClass;
        var name = this.name.asString;
        if (owner.isMetaClass) {
            ^SCDoc.getMethodDoc(
                owner.name.asString.replace("Meta_", ""), 
                "*" ++ name
            )
        } {
            ^SCDoc.getMethodDoc(
                owner.name.asString, 
                "-" ++ name
            )
        };
    }
}
