MIDIDeviceWatcher {
    classvar sources, destinations;
    classvar <>functions;
    classvar <updater, <delta=1;

    *init{
        if(MIDIClient.initialized.not){
            MIDIClient.init
        };
        updater = SkipJack({MIDIDeviceWatcher.checkDevices},delta)
    }

    *delta_{
        updater !? {
            updater.dt = delta;
        }
    }
    *start { if(updater.isNil){this.init}; updater.start }
    *stop { updater !? {updater.stop} }

    *checkDevices{
        var currSources, currDestinations;
        MIDIClient.list;
        currSources = MIDIClient.sources;
        currDestinations = MIDIClient.destinations;

        this.prDoAction(\srcOn, currSources.difference(sources.asArray));
        this.prDoAction(\srcOff, sources.asArray.difference(currSources));
        this.prDoAction(\dstOn, currDestinations.difference(destinations.asArray));
        this.prDoAction(\dstOff, destinations.asArray.difference(currDestinations));

        sources = currSources;
        destinations = currDestinations;
    }

    *prDoAction{|eventType, devices|
        "pass".postln;
    }
}