LSPTransport {
    start {
        |lspConnection|
        this.subclassResponsibility(thisMethod);
    }

    stop { this.subclassResponsibility(thisMethod); }

    send {
        |message|
        this.subclassResponsibility(thisMethod);
    }
}

LSPUDPTransport : LSPTransport {
    var <inPort, <outPort;
    var <socket;
    var <rawRecvFunc;

    *new {
        |inPort, outPort|
        ^super.newCopyArgs(inPort, outPort)
    }

    start {
        |lspConnection|

        var numAttempts = 3;

        // @TODO: What do we do before start / after stop? Errors?
        Log('LanguageServer.quark').info("Starting language server, inPort: % outPort:%", inPort, outPort);

        numAttempts.do {
            |attempt|

            try {
                socket = socket ?? { NetAddr("127.0.0.1", outPort) };
                thisProcess.openUDPPort(inPort, \raw);

                rawRecvFunc = {
                    |msg, time, replyAddr, recvPort|
                    Log('LanguageServer.quark').info("Message received: %, %, %", time, replyAddr, msg);
                    lspConnection.onReceived(msg);
                };

                thisProcess.addRawRecvFunc(rawRecvFunc);
                Log('LanguageServer.quark').info("Successfully opened UDP port % on attempt %", inPort, attempt + 1);
                ^this;
            } {
                |error|
                if (attempt < (numAttempts - 1)) {
                    Log('LanguageServer.quark').warning("LSP attempt % failed. Killing servers and retrying...", attempt + 1);
                    Server.killAll();
                    0.5.wait();
                } {
                    Log('LanguageServer.quark').warning("FATAL: LSP failed after % attempts. Last error: %", numAttempts, error.what);
                }
            }
        };
    }

    stop {
        rawRecvFunc !? {
            thisProcess.removeRawRecvFunc(rawRecvFunc);
            rawRecvFunc = nil;
        };
        socket = nil;
    }

    send {
        |message|
        var maxSize = 6000;
        var offset = 0;
        var packetSize;
        var messageSize = message.size;

        if (socket.isNil) {
            Log('LanguageServer.quark').error("Cannot send message: socket not initialized");
            ^this;
        };

        try {
            if (messageSize < maxSize) {
                socket.sendRaw(message);
            } {
                while { offset < messageSize } {
                    packetSize = min(messageSize, maxSize);
                    socket.sendRaw(message[offset..(offset + packetSize - 1)]);
                    offset = offset + packetSize;
                }
            }
        } {
            |error|
            Log('LanguageServer.quark').error("Failed to send message: %", error.what);
        }

    }
}
