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
        var path = params["searchString"].findHelpFile;
        if (path.notNil) {
            ^(
                uri: path,
                rootUri: SCDoc.helpTargetUrl
            )
        } {
            ^nil
        }
    }
}
