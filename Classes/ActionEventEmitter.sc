/*
ActionEventEmitter
gianluca elia <elgiano@gmail.com>, 2020

Purpose: emit processed events, like tap, doubleTap, longPress
- event processors should be providable by the user without recompiling
- ActionEventEmitter should be attachable to Mktls, but also to anything that can call its action
- 'processors' is a dict of processors (implemented as Events) by name.
- 'action' is a common entry-point: all event processors define a 'process' function, which gets called every time 'action' is called
- events go through the usual dependency system: processors call '.emit' on their parent ActionEventEmitter

- since ctrls are created in mass by MDepMap, processors have to be registered globally
- need functions to get/set processors parameters (difficult to propagate changes from global though, might be better to change global and re-create instances)
*/

ActionEventEmitter {
    // a single 'entry-point' for all event processors
    var <action,
    // a single source
        <source;
    // a dict: see defaultEventProcessors in *initClass
    var <processors;
    classvar <defaultEventProcessors;
    classvar <cancelEvent = 'ActionEventEmitter:cancelEvent';

    // listener and responders used to optionally set callbacks
    var <listener;
    // an MFunc for each event, for SimpleController to call multiple functions
    var <responders;

    var <>debugLevel=2;

    *new {|source, procs,enableAll=true|
        ^super.new.init(source, procs,enableAll);
    }

    init {|src, procs,enableAll|
        // MFunc is nice here, because it is a dictionary of functions, and supports ordered execution. Modes themselves are not used.
        action = MFunc();
        src !? {this.source = src};
        responders = IdentityDictionary[];

        processors = IdentityDictionary[];
        procs = procs ?  ActionEventProc.all;
        this.addProcessors(procs,enableAll);
    }

    source_ {|src|
        source !? {source.action = nil};
        src !? {
            source = src;
            src.action = this.action;
        }
    }

    clear {
        // remove everything
        this.source = nil;
        this.clearProcessors;
        this.clearResponders;
    }

    // run procs hooks for event
    // returns true if hooks want to cancel the event
    runProcessorsHooks{|evName, origArgs, procArgs|
        ^this.processors.select{|proc|
            (proc.hooks ? ()).includesKey(evName)
        }.collect{|proc|
            proc.hooks[evName].(proc, origArgs, procArgs)
        }.includes(this.class.cancelEvent);
    }

    // called by processors
    emit {|evName, origArgs ...procArgs|

        var cancel = this.runProcessorsHooks(evName, origArgs, procArgs);

        if(cancel.not){
            this.dependants do:
            _.update(source, evName, (orig: origArgs, proc: procArgs))
        }

    }

    // processors

    addProcessor {|name, procDef, enable=true|
        var proc = procDef.deepCopy; // deepCopy to avoid unwanted data-sharing
        proc.ctrl = this; // proc should be able to reference this
        processors[name] = proc;
        if(enable){
            action.add(name, {|...args|
                try{proc.process(*args)}
                {|e| "Error in event processor %".format(name).error; e.throw }
            })
        }
    }
    addProcessors {|procsDict, enable=true|
      procsDict.keysValuesDo{|name,proc| this.addProcessor(name,proc,enable)}
    }
    removeProcessor {|name, delete=false|
        action.remove(name);
        if(delete){processors[name] = nil}
    }
    removeProcessors {|names, delete=false|
        names.do{|n| this.removeProcessor(n,delete)}
    }
    clearProcessors {|delete=false|
      processors.keys.do{|name| action.remove(name)};
      if(delete){processors.clear}
    }

    // add actions, e.g. if not using Observer pattern
    addAction {|eventName, func, funcName|
        listener = listener ?? {SimpleController(source)};
        responders[eventName] ?? {
            responders[eventName] = responders[eventName] ?? {MFunc()};
            listener.put(eventName, responders[eventName])
        };
        responders[eventName].add(funcName ?? {func.hash.asString}, {
            |ac, evName ...args| // args = [[original ev args], ...processed ev args]
            func.value(args[0],[evName, args[1..]].flatten)
        })
    }
    removeAction {|eventName, funcOrName|
        responders[eventName] ?? {
            "Event '%' not registered".format(eventName).warn; ^nil
        };
        funcOrName ?? {
            listener.removeAt(eventName);
            responders[eventName] = nil;
            ^this
        };
        responders[eventName].remove(
            if(funcOrName.isFunction){ funcOrName.hash.asString }{ funcOrName }
        );
    }

    clearResponders {
        this.responders.keys do: this.unlisten(_)
    }

    // debug facilities

    debug {|level, message|
        if(level >= debugLevel){
            "[%] action - %".format(Date.getDate().rawSeconds, message).postln;
        }
    }
}
