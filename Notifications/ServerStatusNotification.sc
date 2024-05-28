ServerStatusNotification : LSPNotification {
    *methodNames {
		^[
			"supercollider/serverStatus",
		]
	}

    *clientCapabilityName { ^"supercollider.serverStatus" }
	*serverCapabilityName { ^"serverStatus" }

    init {
        // Register the updateState callbacks on a server
        var registerServer = { | server update |
            SimpleController(server)
                .put(\serverRunning, { this.updateState(server.name) })
                .put(\counts, { this.updateState(server.name) });
                //server.startAliveThread;
            if (update) { this.updateState };
        };

        // Whenever a server is added, call registerServer
        SimpleController(Server)
				.put(\serverAdded, { | serverClass what server | registerServer.value(server, true) });
		Server.named.do(registerServer.value(_, false));
        this.updateState;
    }

    updateState { |serverName|
        // Currently, only the default server is supported.
        if (Server.default.name == serverName, {
            var params = (
                \running: Server.default.serverRunning,
                \unresponsive: Server.default.unresponsive,
                \avgCPU: (Server.default.avgCPU ? 0.0).round(0.1),
                \peakCPU: (Server.default.peakCPU ? 0.0).round(0.1),
                \numUGens: Server.default.numUGens ? 0,
                \numSynths: Server.default.numSynths ? 0,
                \numGroups: Server.default.numGroups ? 0,
                \numSynthDefs: Server.default.numSynthDefs ? 0
            );

            this.sendNotification(params);
        })
    }

    options {
		^(
		)
	}
}