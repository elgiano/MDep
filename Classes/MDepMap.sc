/*
a modal source-event-action map
modes are defined as
(
mode:(
    src->(
        event: [functions,...],
        ...
    ),
    ...
),
...
)

MDep modes are (mode: (event:[func],...))


a = ();
b = ();
~actions = ();
~modes = (
    ada: [
        a->(tap: [\print, \blinkLeds], longPress: [\hello])
    ],
    bob: [
        b->(tap: \hello)
    ]
);

r = MDepMap(~actions, ~modes, \ada);

// actions can be modified freely
~actions.putAll((
    print: {|...args| args.postln},
    blinkLeds: {"Pretend leds are blinking".postln},
    hello: {"Hello".postln},
    test: {|...args| args.postln}
));

// modes too, but if you add/remove sources from the current mode you have to reload it
~modes.putAll((
    bob: [
        a->(tap: [\print, \blinkLeds], longPress: [\hello])
    ],
    ada: [
        a->(tap:\print),
        b->(tap: \hello)
    ]
));
// r.mode = r.mode

a.changed(\tap)

// polling updates
~modes.putAll((
    poll: [
        a->(tap: [\print, \blinkLeds], longPress: [\hello]),
        \poll->[\print]
    ],
    pollAlt: [
        a->(tap: [\print, \blinkLeds], longPress: [\hello]),
        \poll->[\hello]
    ],
    noPoll: [
        a->(tap: [\print, \blinkLeds], longPress: [\hello]),
    ],
));
r.mode = \poll
r.mode = \pollAlt
r.mode = \noPoll
*/


MDepMap {
    var <>functions, <modes, <activeSources, <mode;
    var <isListening = false, <modeless = false, <>modelessMap;
    var <pollingUpdater, <pollDelta = 0.2, modeHasPolling = false;

    *new {|funcDict, modesMap, initMode, modeless=false|
        modesMap = modesMap ?? {IdentityDictionary[]};
        ^super.newCopyArgs(funcDict, modesMap).init(initMode, modeless)
    }
    init { | initMode, setModeless=false|
        modeless = setModeless;
        this.initPollingUpdater;
        initMode !? { this.mode = initMode };
    }

    initPollingUpdater {
        pollingUpdater = SkipJack(
            {this.prDoPollingUpdate}, this.pollDelta,
            {modeHasPolling.not}, autostart: false
        );
    }
    pollDelta_ { arg newDelta;
        if(newDelta <= 0){"SkipJack delta must be >= 0".warn; ^this};
        this.pollDelta = newDelta;
        pollingUpdater.dt = newDelta;
    }

    modeless_{|setModeless=true|
        modeless = setModeless;
        this.modelessMap = this.modelessMap ? IdentityDictionary[];
    }

    modes_ {|newModesMap|
        modes = newModesMap;
        mode !? {this.mode = mode}
    }

    mode_ {|modeName|
        modes[modeName] ?? {
            "Mode % not registered. Current mode: %".format(modeName, mode).warn
            ^this
        };
        mode = modeName;
        this.startListening;
        this.updatePolling;
    }

    updatePolling {
        var wasPolling = modeHasPolling;
        modeHasPolling = if(this.currentMap.isNil){false}{
            this.currentMap.includesKey(\poll)
        };
        if(wasPolling.not and: modeHasPolling){ pollingUpdater.start }
    }

    startListening {
        this.currentMap ?? {
            ^Error("MDep: can't start listening without a mode map").throw
        };
        if(isListening) { this.stopListening };

        this.currentSources do: _.addDependant(this);
        activeSources = this.currentSources;
        isListening = true;
    }
    stopListening {
        activeSources do: _.removeDependant(this);
        isListening = false;
    }

    update { arg srcObj, evName ... evArgs;
        var sourceEvents;
        this.currentMap ?? {^nil};
        this.functions ?? {"MktlDepMap.functions is nil".warn; ^nil};
        sourceEvents = this.currentMap[srcObj] ?? {^nil};
        this.currentMap[srcObj][evName].asArray.do{|funcName|
            functions[funcName].valueArray( srcObj, evArgs, evName, this)
        }
	}

    prDoPollingUpdate {
        this.currentMap ?? {^this};
        this.currentMap[\poll] ??  {^this};

        this.currentMap[\poll].asArray.do{|funcName|
            functions[funcName].valueArray( this.activeSources, this )
        }
    }

    currentMap {
        if(modeless){ ^this.modelessMap };
        this.mode ?? {^nil};
        ^this.modes[this.mode].asDict
    }

    currentSources{
        this.currentMap ?? {^nil};
        ^this.currentMap.keys.reject{|v|v===\poll};
    }


    /*addSource{|newSource|
        this.source = source.asSet.copy.add(newSource)
    }
    removeSource{|removingSource|
        this.source = source.asSet.copy.remove(removingSource)
    }
    replaceSource{|find, replace|
        this.source = source.asSet.copy.remove(find).add(replace)
    }*/


}
