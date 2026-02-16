+Main {
    startup {
        var didWarnOverwrite = false;
        
        // setup the platform first so that class initializers can call platform methods.
        // create the platform, then intialize it so that initPlatform can call methods
        // that depend on thisProcess.platform methods.
        platform = this.platformClass.new;
        platform.initPlatform;
        
        super.startup;
        
        // set the 's' interpreter variable to the default server.
        interpreter.s = Server.default;
        
        // 'langPort' may fail if no UDP port was available
        // this wreaks several manners of havoc, so, inform the user
        // also allow the rest of init to proceed
        try {
            openPorts = Set[NetAddr.langPort];
        } { |error|
            openPorts = Set.new;  // don't crash elsewhere
            "\n\nWARNING: An error occurred related to network initialization.".postln;
            "The error is '%'.\n".postf(error.errorString);
            "There may be an error message earlier in the sclang startup log.".postln;
            "Please look backward in the post window and report the error on the mailing list or user forum.".postln;
            "You may be able to resolve the problem by killing 'sclang%' processes in your system's task manager.\n\n"
                .postf(if(this.platform.name == \windows) { ".exe" } { "" });
        };
        
        Main.overwriteMsg.split(Char.nl).drop(-1).collect(_.split(Char.tab)).do {|x|
            if(x[2].beginsWith(Platform.classLibraryDir) and: {x[1].contains(""+/+"SystemOverwrites"+/+"").not}
            ) {
                warn("Extension in '%' overwrites % in main class library.".format(x[1],x[0]));
                didWarnOverwrite = true;
            }
        };
        if(didWarnOverwrite) {
            postln("Intentional overwrites must be put in a 'SystemOverwrites' subfolder.")
        };
        
        ("\n\n*** Welcome to SuperCollider %. ***".format(Main.version)
            + (Platform.ideName.switch(
                "scvim", {"For help type :SChelp."},
                "scel",  {"For help type C-c C-y."},
                "sced",  {"For help type ctrl-U."},
                "scapp", {"For help type cmd-d."},
                "scqt", {
                    if (Platform.hasQtWebEngine) {
                        "For help press %.".format(if(this.platform.name==\osx,"Cmd-D","Ctrl-D"))
                    } {
                        "For help visit http://doc.sccode.org" // Help browser is not available
                    }
            }) ?? {
                (
                    osx: "For help type cmd-d.",
                    linux: "For help type ctrl-c ctrl-h (Emacs) or :SChelp (vim) or ctrl-U (sced/gedit).",
                    windows: "For help press F1.",
                    iphone: ""
                ).at(platform.name);
                
            })
        ).postln;
    }
}

+Platform {
    startupFiles {
        ^InitializeProvider.startupFiles
    }
}


