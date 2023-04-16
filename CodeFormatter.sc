CodeFormatter {
	classvar <>formatCmd = "/Users/Shared/_code/sclang-format/cmake-build-RelWithDebInfo/src/sclang-format";
	var <tabSize=4, <insertSpaces=true;
	var <inPipe, <outPipe, pid;

	*new {
		|tabSize=4, insertSpaces=true|
		^super.newCopyArgs(tabSize, insertSpaces).init
	}

	isRunning {
		^inPipe.isOpen
	}

	init {
		this.start();

		ShutDown.add({
			this.free()
		});
	}

	start {
		inPipe !? _.close;
		outPipe !? _.close;

		try {

			#inPipe, outPipe = Pipe.argvReadWrite([
				formatCmd,
				"-i",
				tabSize.asString,
				insertSpaces.if("", "-t"),
				"-w",
			]);

			1000.do {
				if (inPipe.isOpen) { ^true };
				0.01.wait;
			};
		};

		^false;
	}

	format {
		|code|
		var read, result;

		outPipe.putString(code);
		outPipe.putChar(0.asAscii);
		outPipe.flush();

		while { read = inPipe.getChar(); read != 0.asAscii } {
			result = result.add(read);
		};

		^result.join("")
	}

	free {
		try { outPipe !? _.close };
		try { inPipe !? _.close };
	}
}

