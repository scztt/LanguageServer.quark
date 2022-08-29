// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
EvaluateCommandProvider : LSPProvider {
	*methodNames {
		^[
			"workspace/evaluateCommand",
		]
	}
	*clientCapabilityName { ^"workspace.evaluateCommand" }
	*serverCapabilityName { ^"evaluateCommandProvider" }

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
			'supercollider.bootServer': {
				Server.default.boot;
			},
			'supercollider.rebootServer': {
				Server.default.reboot;
			},
			'supercollider.killAllServers': {
				Server.killAll();
			},
			'supercollider.showServerWindow': {
				Server.default.makeWindow()
			},
			'supercollider.cmdPeriod': {
				CmdPeriod.run();
			}
		)
	}

	handleRequest {
		|method, params|
		var command, arguments;

		command = params["command"].asSymbol;
		arguments = params["arguments"];

		this.class.commands[command] !? {
			|func|
			^func.value();
		} ?? {
			Exception("Command doesn't exist: %".format(command)).throw
		}
	}
}
