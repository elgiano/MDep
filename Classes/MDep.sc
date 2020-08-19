/*
MDep: a modal event listener (dependant)
gianluca elia <elgiano@gmail.com>, 2020

define a list of modes, each specifying a sequence of callbacks for a number of events:

~source = ();
~actions = ();
~modes = ();

r = MDep(~actions, ~source, ~modes;

(
// actions and modes can be modified freely
~actions.putAll((
    print: {|...args| args.postln},
    blinkLeds: {"Pretend leds are blinking".postln},
    hello: {"Hello".postln},
    test: {|...args| args.postln}
));

~modes.putAll((
    ada: (tap: [\print, \blinkLeds], longPress: [\hello]),
    bob: (tap: \hello)
));
)


r.mode = \ada
~source.changed(\tap)
~source.changed(\longPress)
r.mode = \bob
~source.changed(\tap)
~source.changed(\longPress)

// usage without modes: modeless has an extra event map, out of modes-system

r.modeless = true
r.currentMap = (
  doubleTap: [\print]
);

*/


MDep {
    var <>functions, <>modes, <source, <mode;
    var <isListening = false, <modeless = false, <>modelessMap;

    *new {|funcDict, source, modesMap, initMode, modeless=false|
        modesMap = modesMap ?? {IdentityDictionary[]};
        ^super.newCopyArgs(funcDict, modesMap).init(source, initMode, modeless)
    }

    init { |source, initMode, setModeless|
        source !? { this.source = source };
        initMode !? { this.mode = initMode };
        this.modeless = setModeless;
    }


    // modes

    addModes{|modesMap, merge=true|
        // add/replace one or more modes
        modes = (modes ?? {IdentityDictionary[]}).putAll(modesMap);
    }
    addMode{|name,map| modes[name] = map }

    removeMode{|...names|
        names do: modes.removeAt(_);
        this.modes = modes
    }

    modeless_{|setModeless=true|
        modeless = setModeless;
        this.modelessMap = this.modelessMap ? IdentityDictionary[];
    }


    currentMap_{|eventMap|
        if(this.modeless){ this.modelessMap = eventMap; ^this };
        this.mode ?? {
            ^Error("MDep: no current mode to set").throw
        };
        this.modes[this.mode] = eventMap;
    }

    currentMap {
        if(this.modeless){ ^this.modelessMap };
        this.mode ?? {^nil};
        ^this.modes[this.mode]
    }

    mode_ {|modeName|
        modes[modeName] ?? {
            ^Error("Mode % not registered".format(modeName)).throw
        };
        mode = modeName;
    }

    // dependant

    source_{|newSource, startListening=true|
        // replace source
        if(isListening) { this.stopListening };
        source = newSource;
        if(startListening) { this.startListening() };
    }

    startListening {
        source ?? {
            ^Error("MDep: can't start listening without a source").throw
        };
        if(isListening) { this.stopListening };

        source.addDependant(this);
        isListening = true;
    }
    stopListening {
        source.removeDependant(this);
        isListening = false;
    }

    update { arg srcObj, evName ... evArgs;
        this.currentMap ?? {^nil};
        this.currentMap[evName].asArray.do{|funcName|
            functions[funcName].valueArray( srcObj, evArgs, evName, this)
        }
	}
}
