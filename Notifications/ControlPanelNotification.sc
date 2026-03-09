ControlPanelNotification : LSPNotification {
    classvar <instances;
    var <controls, <selected;
    
    *methodNames {
        ^[
            "supercollider/controlPanelSpec",
            "supercollider/controlPanelValue",
            "supercollider/controlPanelSelect",
            "supercollider/controlPanelData",
        ]
    }
    
    *clientCapabilityName { ^"supercollider.controlPanel" }
    *serverCapabilityName { ^"controlPanel" }
    
    init {
        // Register the updateState callbacks on a server
        instances = instances.add(this);
        controls = [];   
    }
    
    *registerControl {
        |path, spec, displayValue, normalizedValue, position|
        instances.do { |inst| inst.registerControl(path, spec, displayValue, normalizedValue, position) };
    }
    
    *unregisterControl {
        |path|
        instances.do { |inst| inst.unregisterControl(path) };
    }
    
    *updateValue {
        |path, displayValue, normalizedValue|
        instances.do { |inst| inst.updateValue(path, displayValue, normalizedValue) };
    }
    
    *registerActionControl { 
        |
            path, position, displayName,
            displayValue, normalizedValue=0,
            enabled=true, toggleable=false
        |
        instances.do { |inst| inst.registerActionControl(path, position,  displayName, displayValue, normalizedValue, enabled, toggleable) }
    }
    
    *registerNumericControl {
        |
            path, position, displayName,
            displayValue, normalizedValue
        |
        instances.do { |inst| inst.registerNumericControl(path, position,  displayName, displayValue, normalizedValue) }
    }
    
    *registerTextControl {
        |
            path, position, displayName,
            displayValue, displayPropertyName=false
        |
        instances.do { |inst| inst.registerTextControl(path, position,  displayName, displayValue, displayPropertyName) }
    }
    
    *registerPopupControl {
        |
            path, position, displayName,
            items, normalizedValue
        |
        instances.do { |inst| inst.registerPopupControl(path, position,  displayName, items, normalizedValue) }
    }
    
    
    selected_{
        |path|
        if (selected != path) {
            selected = path;
            this.sendNotificationName("supercollider/controlPanelSelect", (
                path: path
            ));
        }
    }
    
    registerActionControl { 
        |
            path, position, displayName,
            displayValue, normalizedValue=0,
            enabled=true, toggleable=false
        |
        
        var spec = (
            spec: (
                type: "action",
                enabled: enabled,
                toggleable: toggleable,
                displayName: displayName
            )
        );
        this.registerControl(path, spec, displayValue, normalizedValue, position); 
    }
    
    registerNumericControl {
        |
            path, position, displayName,
            displayValue, normalizedValue
        |
        var spec = (
            spec: ( 
                type: "numeric",
                displayName: displayName
            )
        );
        this.registerControl(path, spec, displayValue, normalizedValue, position);
    }
    
    registerTextControl {
        |
            path, position, displayName,
            displayValue, displayPropertyName=false
        |
        var spec = (
            spec: (
                type: "text",
                displayName: displayName,
                displayPropertyName: displayPropertyName
            )
        );
        this.registerControl(path, spec, displayValue, nil, position);
    }
    
    registerPopupControl {
        |
            path, position, displayName,
            items, normalizedValue
        |
        var spec = (
            spec: (
                type: "popup",
                items: items,
                displayName: displayName,
            )
        );
        this.registerControl(path, spec, normalizedValue, normalizedValue ?? { items.first }, position);
    }
    
    registerControl {
        |path, spec, displayValue, normalizedValue, position|
        var index, controlList;
        
        path = path.collect(_.asSymbol);
        spec = spec.copy;
        
        spec[\path] = path;
        spec[\value] = displayValue;
        spec[\displayValue] = displayValue;
        spec[\normalizedValue] = normalizedValue;
        
        index = controls.detectIndex {
            |e|
            e[\path] == path;
        };
        
        if (index.isNil) {
            if (position.notNil) {
                index = position;
                controls = controls.insert(position, nil);
            } {
                index = controls.size;
                controls = controls.add(nil);
            };
        };
        
        controls[index] = spec.copy.put(\path, path);
        
        this.sendNotificationName("supercollider/controlPanelSpec", (
            specification: (controls: controls),
        ));
        
        this.updateValue(path, displayValue, normalizedValue);
    }
    
    unregisterControl {
        |path|
        var index, controlList;
        
        path = path.collect(_.asSymbol);
        
        index = controls.detectIndex {
            |e|
            e[\path] == path;
        };
        
        if (index.notNil) {
            controls.removeAt(index);
        };
        
        this.sendNotificationName("supercollider/controlPanelSpec", (
            specification: (controls: controls),
        ));
    }
    
    updateValue {
        |path, displayValue, normalizedValue|
        path = path.collect(_.asSymbol);
        
        this.sendNotificationName("supercollider/controlPanelValue", (
            path: path,
            value: displayValue,
            normalizedValue: normalizedValue,
            displayValue: displayValue
        ));
    }
    
    options {
        ^(
        )
    }
}
