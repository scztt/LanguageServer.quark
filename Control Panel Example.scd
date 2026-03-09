// ControlPanel example: register each control type, listen for changes,
// and update values from sclang via a Routine.
( // TEST
var muteState = false;

// ─────────────────────────────────────────────────────────────────────
// 1. NUMERIC CONTROL (animated) — sweeps automatically via Routine
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerNumericControl(
    path:            ["demo", "lfo"],
    position:        0,
    displayName:     "LFO",
    displayValue:    "0.0",
    normalizedValue: 0
);

// ─────────────────────────────────────────────────────────────────────
// 2. NUMERIC CONTROL (user-controlled) — drag to change
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerNumericControl(
    path:            ["demo", "gain"],
    position:        1,
    displayName:     "Gain",
    displayValue:    "0.5",
    normalizedValue: 0.5
);

// ─────────────────────────────────────────────────────────────────────
// 3. ACTION CONTROL — a button (non-toggleable)
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerActionControl(
    path:            ["demo", "randomize"],
    position:        2,
    displayName:     "Randomize",
    displayValue:    "Click me",
    normalizedValue: 0,
    enabled:         true,
    toggleable:      false
);

// ─────────────────────────────────────────────────────────────────────
// 4. ACTION CONTROL (toggleable) — an on/off toggle button
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerActionControl(
    path:            ["demo", "mute"],
    position:        3,
    displayName:     "Mute",
    displayValue:    "OFF",
    normalizedValue: 0,
    enabled:         true,
    toggleable:      true
);

// ─────────────────────────────────────────────────────────────────────
// 5. TEXT CONTROL — displays rich markdown content
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerTextControl(
    path:                ["demo", "info"],
    position:            4,
    displayName:         "Status",
    displayValue:        "# System Status\n\nAll systems **operational**.\n\n---\n\n## Details\n\n- CPU: `12%`\n- Memory: `384 MB`\n- Synths: `0`\n\n> Ready for input.",
    displayPropertyName: true
);

// ─────────────────────────────────────────────────────────────────────
// 6. POPUP CONTROL — dropdown selection
// ─────────────────────────────────────────────────────────────────────
ControlPanelNotification.registerPopupControl(
    path:            ["demo", "scale"],
    position:        5,
    displayName:     "Scale",
    items:           ["Chromatic", "Major", "Minor", "Dorian", "Hexatonic"],
    normalizedValue: 0
);

// ─────────────────────────────────────────────────────────────────────
// LISTEN FOR CHANGES — respond to user interaction and echo back
// ─────────────────────────────────────────────────────────────────────
~cpDependant = { |obj, what, value|
    "──── ControlPanel changed: % = %".format(what, value).postln;
    
    // IMPORTANT:
    // Control panel controls are "dumb" - they send changes, but they rely on SuperCollider
    // to be updated. So, without sending the updated values back, controls on the VSCode side
    // will never change.
    
    switch(what,
        'demo_gain', {
            // Echo the new gain value back to the panel
            ControlPanelNotification.updateValue(
                ["demo", "gain"],
                value.round(0.01).asString,
                value
            );
        },
        'demo_mute', {
            // Toggle local state and confirm back to the panel
            muteState = muteState.not;
            ControlPanelNotification.updateValue(
                ["demo", "mute"],
                if(muteState, "ON", "OFF"),
                muteState.asInteger.asFloat
            );
        }
    );
};

ControlPanelChangeProvider.addDependant(~cpDependant);

// ─────────────────────────────────────────────────────────────────────
// ANIMATE LFO — sweep the first slider automatically
// ─────────────────────────────────────────────────────────────────────
~cpRoutine = Routine({
    var phase;
    
    inf.do { |i|
        phase = (i * 0.07 % 1.0).round(0.01);
        ControlPanelNotification.updateValue(
            ["demo", "lfo"],
            phase.asString,
            phase
        );
        0.5.wait;
    };
}).play(AppClock);
)

// ─────────────────────────────────────────────────────────────────────
// CLEANUP
// ─────────────────────────────────────────────────────────────────────
(
~cpRoutine.stop;
ControlPanelChangeProvider.removeDependant(~cpDependant);

["lfo", "gain", "randomize", "mute", "info", "scale"].do { |name|
    ControlPanelNotification.unregisterControl(["demo", name]);
};
)
