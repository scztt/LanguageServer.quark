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
}
