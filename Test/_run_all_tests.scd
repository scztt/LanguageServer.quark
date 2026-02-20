// Automated test runner - loads and runs all test files
(
var testFiles = [
    "LSP-test.scd",
    "LSP-test-method-handler.scd",
    "LSP-test-classmethod-handler.scd",
    "LSP-test-classname-handler.scd",
    "LSP-test-singleton-handler.scd",
    "LSP-test-envvar-handler.scd",
    "LSP-test-completion-parsing.scd",
    "LSP-test-validatePrefix-edge-cases.scd",
    "LSP-test-handleCompletion.scd",
];
var basePath = thisProcess.nowExecutingPath.dirname ++ "/";

"========================================".postln;
"Running all LSP tests".postln;
"========================================".postln;

testFiles.do { |f|
    var path = basePath ++ f;
    "\n=== Loading: % ===".format(f).postln;
    try {
        UnitTestScript("LSP", path).runScript;
    } { |e|
        "ERROR loading %: %".format(f, e.errorString).postln;
    };
};

"\n========================================".postln;
"All tests submitted".postln;
"========================================".postln;

// Give async tests time to complete, then quit
fork {
    2.wait;
    "\n========================================".postln;
    "Tests complete, exiting".postln;
    "========================================".postln;
    0.exit;
};
)
