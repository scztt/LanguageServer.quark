
LSP_JSON {
    classvar <tab,<nl;
    
    *initClass {
        tab = [$\\,$t].as(String);
        nl = [$\\,$n].as(String);
    }
    *stringify { arg obj, force=false;
        var out;
        out = try { obj.lsp_toJSON } {
            ("No JSON conversion for object" + obj).warn;
        };
        
        ^out
    }
}

LSP_JSONEncodeError : Error {
    var <>type;
    
    *new {
        |obj|
        var new = super.new;
        new.type = obj.class;
    }
}

+Object { lsp_toJSON { LSP_JSONEncodeError(this).throw }}

+Nil { lsp_toJSON { ^"null" }}
+True { lsp_toJSON { ^"true" }}
+False { lsp_toJSON { ^"false" }}

+String {
    lsp_toJSON {
        ^this.asCompileString.replace("\n", LSP_JSON.nl).replace("\t", LSP_JSON.tab);
    }
}

+Symbol {
    lsp_toJSON {
        ^LSP_JSON.stringify(this.asString)
    }
}

+Dictionary {
    lsp_toJSON {
        var out = List.new;
        this.keysValuesDo({ arg key, value;
            out.add( key.asString.asCompileString ++ ":" + LSP_JSON.stringify(value) );
        });
        ^("{" ++ (out.join(",")) ++ "}");
    }
}

+Number {
    lsp_toJSON {
        if(this.isNaN, {
            ^"NaN"
        });
        if(this === inf, {
            ^"Infinity"
        });
        if(this === (-inf), {
            ^"-Infinity"
        });
        ^this.asString
    }
}

+SequenceableCollection {
    lsp_toJSON {
        ^"[" ++ this.collect({ arg sub;
            LSP_JSON.stringify(sub)
        }).join(",")
        ++ "]";
    }
}