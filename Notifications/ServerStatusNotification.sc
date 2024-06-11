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
        var registerServer = {
            |server|
            SimpleController(server)
                .put(\serverRunning, { this.updateState(server) })
                .put(\counts, { this.updateState(server) })
                .put(\didQuit, { this.updateState(server) });
            
            this.updateState(server);
        };
        
        // Whenever a server is added, call registerServer
        SimpleController(Server)
            .put(\serverAdded, { |serverClass, what, server| registerServer.value(server) });
        Server.named.do(registerServer.value(_));
    }
    
    updateState {
        |server|
        // Currently, only the default server is supported.
        if (Server.default === server, {
            var params = (
                name: server.name,
                running: server.serverRunning,
                unresponsive: server.unresponsive,
                avgCPU: (server.avgCPU ? 0.0).round(0.1),
                peakCPU: (server.peakCPU ? 0.0).round(0.1),
                numUGens: server.numUGens ? 0,
                numSynths: server.numSynths ? 0,
                numGroups: server.numGroups ? 0,
                numSynthDefs: server.numSynthDefs ? 0
            );
            
            this.sendNotification(params);
        })
    }
    
    options {
        ^(
        )
    }
}
