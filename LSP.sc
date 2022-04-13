LSPConnection {
	classvar <lspConnection;
	classvar <providers, <>preprocessor;
	classvar readyMsg = "***LSP READY***";

	var <>inPort, <>outPort;
	var socket;
	var messageLengthExpected, messageBuffer;

	*initClass {
		var settings;

		Class.initClassTree(Log);

		// Initialization
		providers = ();

		// All params objects are passed through preprocessor.
		// This can normalize common param fields.
		// @TODO each LSPProvider should have it's own preprocessor?
		preprocessor = {
			|params|

			params["params"] !? _["position"] !? {
				|position|
				position["line"] = position["line"].asInteger;
				position["character"] = position["character"].asInteger;
			}
		};

		settings = this.envirSettings();

		if (settings[\enabled].asBoolean) {
			StartUp.add({
				lspConnection = LSPConnection().start;
			})
		}
	}

	*new {
		|settings|
		^super.new.init(this.envirSettings.copy.addAll(settings));
	}

	*envirSettings {
		^(
			enabled: "SCLANG_LSP_ENABLE".getenv().notNil,
			inPort: "SCLANG_LSP_CLIENTPORT".getenv() ?? { 57210 } !? _.asInteger,
			outPort: "SCLANG_LSP_SERVERPORT".getenv() ?? { 57211 } !? _.asInteger
		)
	}

	init {
		|settings|
		inPort = settings[\inPort];
		outPort = settings[\outPort];
	}

	start {
		// @TODO: What do we do before start / after stop? Errors?
		Log('LanguageServer.quark').info("Starting language server, inPort: % outPort:%", inPort, outPort);

		socket = NetAddr("127.0.0.1", outPort);
		thisProcess.openUDPPort(inPort, \raw);

		thisProcess.recvRawfunc = {
			|time, replyAddr, msg|
			this.prOnReceived(time, replyAddr, msg)
		};

		// @TODO Is this the only "default" provider we want?
		this.addProvider(InitializeProvider(this, {}));

		readyMsg.postln;
	}

	stop {
		// @TODO Unregister and close ports?
	}

	serverInfo {
		// @TODO What should go here?
		^(
			"name": "sclang:LSPConnection",
			"version": "0.1"
		)
	}

	addProvider {
		|provider|
		provider.methodNames.do {
			|methodName|
			methodName = methodName.asSymbol;

			if (providers[methodName].isNil) {
				Log('LanguageServer.quark').info("Adding provider for method '%'", methodName);
			} {
				Log('LanguageServer.quark').warning("Overwriting provider for method '%'", methodName);
			};

			providers[methodName] = provider;
		}
	}

	prOnReceived {
		|time, replyAddr, message|

		Log('LanguageServer.quark').info("Message received: %, %, %", time, replyAddr, message);

		this.prParseMessage(message) !? this.prHandleMessage(_)
	}

	prParseMessage {
		|message|
		var object, found, endOfHeader;

		messageBuffer = messageBuffer ++ message;

		if (messageLengthExpected.isNil) {
			found = messageBuffer.findRegexp("Content-Length: ([0-9]+)\r\n\r(\n)");
			if (found.size > 0) {
				messageLengthExpected = found[1][1].asInteger;
				endOfHeader = found[2][0] + 1;
				messageBuffer = messageBuffer[endOfHeader..];
			}
		};

		if (messageLengthExpected.notNil and: {
			messageLengthExpected <= messageBuffer.size
		}) {
			try {
				object = messageBuffer[0..(messageLengthExpected-1)].parseJSON;
				messageBuffer = messageBuffer[messageLengthExpected..];
				messageLengthExpected = nil;
			} {
				|e|
				// @TODO: Improve error messaging and behavior.
				"Problem parsing message (%)".format(e).error;
			};

			^object
		} {
			messageBuffer = messageBuffer ++ message
			^nil
		}
	}

	prHandleMessage {
		|object|
		var id, method, params, provider, deferredResult;

		id 		= object["id"];
		method 	= object["method"].asSymbol;
		params 	= object["params"];

		provider = providers[method];

		if (provider.isNil) {
			Log('LanguageServer.quark').info("No provider found for method: %", method)
		} {
			Log('LanguageServer.quark').info("Found method provider: %", provider);

			// Preprocess param values into a usable state
			preprocessor.value(params);

			Deferred().using({
				provider.handleRequest(method, params);
			}, AppClock).then({
				|result|

				if (result == provider) {
					"Provider % is returning *itself* from handleRequest instead of providing an explicit nil or non-nil return value!".format(provider.class).warn;
				};

				this.prHandleResponse(id, result);
			}, {
				|error|
				// @TODO handle error
				error.reportError;
				this.prHandleErrorResponse(
					id: id,
					code: error.class.identityHash,
					message: error.what,
					// data: error.getBacktrace // @TODO Render backtrace as JSON?
				);
			});
		}
	}

	prHandleErrorResponse {
		|id, code, message, data|
		var response = (
			id: id,
			error: (
				code: code,
				message: message,
				data: data
			)
		);


		this.prSendMessage(response);
	}

	prHandleResponse {
		|id, result|
			
		var response = (
			id: id,
			result: result ?? { NilResponse() }
		);

		this.prSendMessage(response);
	}

	prEncodeMessage {
		|dict|
		var message;

		try {
			message = dict.toJSON();
		} {
			|e|
			// Since JSON encoding JUST failed, lets avoid doing it again...
			"{ \"code\": -1, \"message\": \"Failed to encode JSON response: %\" }".format(
				e.what.escapeChar($")
			)
		};

		message = "Content-Length: %\r\n\r\n%\n".format(
			message.size + 1,
			message
		);

		^message
	}

	prSendMessage {
		|dict|
		var message = this.prEncodeMessage(dict);

		Log('LanguageServer.quark').info("Responding with: %", message);

		socket.sendRaw(message);
	}
}

NilResponse {
	asJSON {
		toJSON { ^"null" }
	}
	// Placeholder for nil responses, since nil signifies an empty slot in a dictionary.
}