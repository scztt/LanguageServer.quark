// For now, we messily copy-paste the contents of the existing Document to
// remind ourselves of the interface and borrow implementations where it's
// useful. Later, common code should be merged back into the base Document class.
// Consider this entire class a big @TODO - any uncommented implementations
// are currently being used by the LSP implementation.
//
BaseDocument {
    classvar <dir="", <allDocuments, <>current;
    classvar <globalKeyDownAction, <globalKeyUpAction, <>initAction;
    classvar <>autoRun = true;
    classvar <asyncActions;
    classvar <>implementingClass;
    
    var <>quuid, <title, <isEdited = false;
    var <>toFrontAction, <>endFrontAction, <>onClose, <>textChangedAction;
    
    var <envir, <savedEnvir;
    // var <editable = true, <promptToSave = true;
    
    path            { ^this.subclassResponsibility(thisMethod) }
    keyDownAction   { ^this.subclassResponsibility(thisMethod) }
    keyDownAction_  { ^this.subclassResponsibility(thisMethod) }
    keyUpAction     { ^this.subclassResponsibility(thisMethod) }
    keyUpAction_    { ^this.subclassResponsibility(thisMethod) }
    mouseUpAction   { ^this.subclassResponsibility(thisMethod) }
    mouseUpAction_  { ^this.subclassResponsibility(thisMethod) }
    mouseDownAction { ^this.subclassResponsibility(thisMethod) }
    mouseDownAction_{ ^this.subclassResponsibility(thisMethod) }
    
    *open { 
        |path, selectionStart=0, selectionLength=0, envir| 
        ^implementingClass.open(path, selectionStart=0, selectionLength=0, envir) 
    }

    open { 
        |path, selectionStart=0, selectionLength=0, envir| 
        ^implementingClass.open(path, selectionStart=0, selectionLength=0, envir) 
    }

    *implementationClass { ^LSPDocument }
}

