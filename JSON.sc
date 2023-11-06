
JSON {
    classvar <tab,<nl;
    
    *initClass {
        tab = [$\\,$t].as(String);
        nl = [$\\,$n].as(String);
    }
    *stringify { arg obj, force=false;
        var out;
        out = try { obj.toJSON } {
            ("No JSON conversion for object" + obj).warn;
        };
        
        ^out
    }
}

JSONEncodeError : Error {
    var <>type;
    
    *new {
        |obj|
        var new = super.new;
        new.type = obj.class;
    }
}

+Object { toJSON { JSONEncodeError(this).throw }}

+Nil { toJSON { ^"null" }}
+True { toJSON { ^"true" }}
+False { toJSON { ^"false" }}

+String {
    toJSON {
        ^this.asCompileString.replace("\n", JSON.nl).replace("\t", JSON.tab);
    }
}

+Symbol {
    toJSON {
        ^JSON.stringify(this.asString)
    }
}

+Dictionary {
    toJSON {
        var out = List.new;
        this.keysValuesDo({ arg key, value;
            out.add( key.asString.asCompileString ++ ":" + JSON.stringify(value) );
        });
        ^("{" ++ (out.join(",")) ++ "}");
    }
}

+Number {
    toJSON {
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
    toJSON {
        ^"[" ++ this.collect({ arg sub;
            JSON.stringify(sub)
        }).join(",")
        ++ "]";
    }
}