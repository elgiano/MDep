/*
ActionEventProc
gianluca elia <elgiano@gmail.com>, 2020

Used by ActionEventEmitter. This class is only a global register, ActionEventProcs are implemented as Events for on-the-fly development.

Event template:
(
property1: ..., property2: ...,

process: {|self ...args| self.ctrl.emit(\eventName, *eventArgs)}
)

Input:
'process' is the only requirement: the function called when an event needs processing.
args are as provided by the source action. Typically args[0] is the sender.

Output:
'process' can call self.ctrl.emit to emit any number of events

When ActionEventEmitter instantiate an ActionEventProc:
- it does a .deepCopy, to ensure properties are private across different instances of the same processor
- it sets thisProcessor.ctrl as a reference to itself, that the processor can use:
- to emit events: ctrl.emit(\eventName, *eventArgs)
- print debug messages: ctrl.debug(debugLevel, message)
*/

ActionEventProc {

    classvar <all;

    *new {|name, procDef|
        procDef !? { all.put(name, procDef) }
        ^all[name]
    }

    *initClass{
        all = IdentityDictionary[];

        ActionEventProc(\change,(
            minThr:0.1,
            lastVal: (),

            process: {|self ...sourceArgs|
                var el = sourceArgs[0];
                if(el.value != self.lastVal[el.name]){
                    self.lastVal[el.name] = el.value;
                    self.ctrl.emit(\change, sourceArgs, el.value)
                }
            }

        ));

        ActionEventProc(\tap, (
            minThr: 0.1,
            tapping: false,
            tapName: '',
            toggle: false,

            process: {|self ...sourceArgs|
                var el = sourceArgs[0];
                if(self.tapping.not){
                    if(el.value >= self.minThr){
                        self.ctrl.debug(1, "tap on");
                        self.ctrl.emit(\tapOn, sourceArgs);
                        self.tapName = el.name;
                        self.tapping = true;
                        self.toggle = self.toggle.not;
                        self.ctrl.emit(\toggle, sourceArgs, self.toggle)
                    }
                }{
                    if((el.value < self.minThr) and: (el.name == self.tapName)){
                        self.ctrl.debug(0, "tap off");
                        self.ctrl.emit(\tapOff, sourceArgs);
                        self.tapping = false
                    }
                }
            }

        ));

        ActionEventProc(\doubleTap, (

            doubleTapDelta: 0.25,
            lastTap: -1,

            hooks: (
                tap: {|self, sourceArgs|
                    var now = Date.getDate().rawSeconds;
                    if((now - self.lastTap) <= self.doubleTapDelta){
                        self.ctrl.emit(\doubleTap, sourceArgs);
                        self.ctrl.debug(1, "DOUBLETAP");
                    };
                    self.lastTap = now;
                },
            )

        ));

        ActionEventProc(\press, (
            minThr: 0.1,
            pressing: false,
            pressStart: -1,
            longPressCounter: nil,
            tapDelta: 0.15, // emit tap if pressed for less than tapDelta
            longPressDelta: 1.5, // emit longPress if pressed for more than longPressDelta
            pressNames: Set[],

            cancelCounter: {|self|
                self.longPressCounter !? {
                    self.longPressCounter.stop;
                    self.longPressCounter = nil
                };
            },

            restartCounter: {|self, sourceArgs|
                self.cancelCounter();
                self.longPressCounter = {
                    self.tapDelta.wait;
                    self.ctrl.debug(1, "long press start");
                    self.ctrl.emit(\longPressStart, sourceArgs);
                    (self.longPressDelta-self.tapDelta).wait;
                    self.ctrl.debug(1, "long press");
                    self.ctrl.emit(\longPress, sourceArgs)
                }.fork;
            },

            process: {|self ...sourceArgs|
                var el = sourceArgs[0];
                if(el.value >= self.minThr){
                    if(self.pressing.not){
                        self.pressStart = Date.getDate().rawSeconds;
                        self.pressing = true;
                        self.restartCounter(sourceArgs);
                        self.ctrl.debug(0, "press on");
                        self.ctrl.emit(\pressOn, sourceArgs);
                    };
                    self.pressNames.add(el.name)
                }{
                    self.pressNames.remove(el.name);
                    if(self.pressing and: self.pressNames.isEmpty){
                        self.cancelCounter();
                        if(self.pressStart <= 0){
                            "[Press] invalid press start".warn;
                        }{
                            var pressDur = Date.getDate().rawSeconds - self.pressStart;
                            self.ctrl.debug(1, "press off (% s)".format(pressDur));
                            self.ctrl.emit(\pressOff, sourceArgs, pressDur);
                            if(pressDur < self.longPressDelta){
                                if(pressDur < self.tapDelta){
                                    self.ctrl.emit(\tap, sourceArgs);
                                }{
                                    self.ctrl.emit(\shortPress, sourceArgs);
                                }
                            };
                            self.pressing = false;
                            self.pressStart = -1;
                        };

                    }
                }
            }

        ));

    }
}
