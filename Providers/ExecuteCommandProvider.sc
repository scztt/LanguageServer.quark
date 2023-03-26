// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
ExecuteCommandProvider : LSPProvider {
	*methodNames {
		^[
			"workspace/executeCommand",
		]
	}
	*clientCapabilityName { ^"workspace.executeCommand" }
	*serverCapabilityName { ^"executeCommandProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^(
			commands: this.class.commands.keys.asArray
		)
	}

	*commands {
		^(
			'supercollider.internal.bootServer': {
				Server.default.boot;
			},
			'supercollider.internal.rebootServer': {
				Server.default.reboot;
			},
			'supercollider.internal.killAllServers': {
				Server.killAll();
			},
			'supercollider.internal.showServerWindow': {
				Server.default.makeWindow()
			},
			'supercollider.internal.cmdPeriod': {
				CmdPeriod.run();
			}
		)
	}

	onReceived {
		|method, params|
		var command, arguments;

		command = params["command"].asSymbol;
		arguments = params["arguments"];

		this.class.commands[command] !? {
			|func|
			func.value();
			^nil
		} ?? {
			Exception("Command doesn't exist: %".format(command)).throw
		}
	}
}
