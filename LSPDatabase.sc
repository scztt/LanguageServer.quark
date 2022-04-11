// LSPDatabase is a container for pure functions that return metadata about open documents or
// sclang instance details like lists of classes. Functions are expected to be called inside of
// a Routine and should yield during long operations. These should be considered good candidates
// for caching.

LSPDatabase {
	classvar allMethodNames, allMethods, allClasses, allMethodsByName, methodLocations;
	classvar classSymbols, methodSymbols, allSymbolObjects;

	*initClass {
		methodLocations = ();
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

	*methodInsertString {
		|method|
		^"%(%${0})".format(
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
		var methods;

		if (word.isClassName) {
			^[this.renderClassLocation(word.asClass)]
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
			uri: "file://%".format(method.filenameSymbol),
			range: this.renderMethodRange(method)
		)
	}

	*renderClassLocation {
		|class|
		^(
			uri: "file://%".format(class.filenameSymbol),
			range: this.renderClassRange(class)
		)
	}

	*makeMethodCompletion {
		|method, sortByClassHierarchy=false|
		var sortText;

		if (sortByClassHierarchy && method.ownerClass.isMetaClass) {
			sortText = (9 - method.ownerClass.superclasses.size).asString.zeroPad()
		} {
			sortText = "%:%".format(method.ownerClass.name, method.name)
		};

		^(
			label: (
				label: 				method.name.asString,
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
			commitCharacters: 		["("]
		)
	}

	*methodDetails {
		|method|
		^(
			detail: this.methodArgString(method),
			description: "%:%".format(method.ownerClass.name, method.name)
		)
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

		Log('LanguageServer.quark').info("Searching line for a word: '%' at %:%", lineString, line, character);

		while {
			(start >= 0) and: { (lineString[start].isAlphaNum or: { lineString[start] == $_ }) }
		} {
			start = start - 1
		};
		start = start + 1;

		word = lineString.findRegexpAt("[A-Za-z][\\w]*", start);
		if (word.size > 0) {
			^word[0]
		} {
			^nil
		}
	}

	*getDocumentRegions {
		|doc|
		// @TODO Parse properly to account for e.g. comments...
		var lines = doc.string.split($\n);
		var startRe = "^\\(\\W*(\\\\.*)?$", endRe = "^\\)\\s*(//.*)?$";
		var regionStack = [], regions=[], region;

		lines.do {
			|line, lineNum|
			if (startRe.matchRegexp(line)) {
				regionStack = regionStack.add((
					start: (line: lineNum, character: 0)
				));
			} {
				if (endRe.matchRegexp(line)) {
					if (regionStack.size > 0) {
						regionStack.last.put(
							\end,
							(line: lineNum, character: line.size)
						);
						regions = regions.add(regionStack.removeAt(regionStack.size-1));
					}
				}
			}
		};

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
			name: 		method.name,
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
			label: (
				label: 				name,
			),
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

		"start index: %".format(index).postln;

		while { index < limit and: { symbolObjects[index].name.asString.beginsWith(query) }} {
			result = result.add(symbolObjects[index].postln);
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
			if (all[index].name.asString.toLower.postln < query.postln) {
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

