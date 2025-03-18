+String {
    urlDecode {
        var str = this;
        
        str.findRegexp("%[A-Fa-f0-9]{2}").collect({
            |found|
            var ch = found[1][1..];
            ch = (ch[0].digit << 4) | ch[1].digit;
            [found[0], ch]
        }).reverseDo {
            |replace|
            str = str[0..(replace[0]-1)] 
                ++ replace[1].asAscii 
                ++ str[(replace[0] + 3)..]
        };        
        
        ^str
    }
    
    lineCharToIndex {
        |line, character|
        var currentIndex = 0;
        
        line.do {
            currentIndex = this.find("\n", false, currentIndex);
            
            if (currentIndex.isNil) {
                ^this.size
            } {
                currentIndex = currentIndex + 1
            }
        };
        
        ^(currentIndex + character)
    }

    /*
    Given an absolute file path in OSX, Windows, or Linux format, return a file URI
    that will be understood by the LSP client.
    */
    pathToFileURI {
        ^Platform.case(
            \osx, { "file://" ++ this },
            \windows, { "file:///" ++ this.replace("\\", "/") },
            \linux, { "file://" ++ this },
            { "file://" ++ this }
        )
    }

    /*
    Reverse of pathToFileURI - given a file URI, returns a path in the format of the 
    current OS.
    */
    fileURIToPath {
        ^(Platform.case(
            \osx, { this.replace("file://", "") },
            \windows, { this.replace("file:///", "").replace("/", "\\") },
            \linux, { this.replace("file://", "") },
            { this.replace("file://", "") }
        ).urlDecode)
    }
}
