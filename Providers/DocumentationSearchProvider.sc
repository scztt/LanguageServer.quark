DocumentationSearchProvider : LSPProvider {
    *methodNames {
        ^[
            "documentation/search",
        ]
    }
    *clientCapabilityName { ^nil }
    *serverCapabilityName { ^nil }
    
    init {
        |clientCapabilities|
        try {
            SCDoc.indexAllDocuments
        } {}
    }
    
    options {
        ^(
        )
    }
    
    onReceived {
        |method, params|
        var brokenAction, urlString, url;
        
        brokenAction = brokenAction ? { |fragment|
			var brokenUrl = URI.fromLocalPath( SCDoc.helpTargetDir++"/BrokenLink.html" );
			brokenUrl.fragment = fragment;
			brokenUrl;
		};

        urlString = params["searchString"].findHelpFile;
        if (urlString.notNil) {
		    url = URI(urlString);
            url = SCDoc.prepareHelpForURL(url) ?? { brokenAction.(urlString) };

            ^(
                uri: url.asString,
                rootUri: SCDoc.helpTargetUrl
            )
        } {
            ^nil
        }
    }
}
