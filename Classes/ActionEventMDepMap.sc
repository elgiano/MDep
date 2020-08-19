// automatically adds ActionEventEmitters to sources
ActionEventMDepMap : MDepMap {

    // overrides

    startListening {
        this.currentMap ?? {
            ^Error("MDep: can't start listening without a mode map").throw
        };
        if(isListening) { this.stopListening };

        activeSources = this.currentSources.collect{|src|
          ActionEventEmitter(src).addDependant(this)
        };
        isListening = true;
    }
    stopListening {
        activeSources.do{|src|
            if(src.class==ActionEventEmitter)
            {src.clear}{src}
            .removeDependant(this)
        };
        isListening = false;
    }
    prDoPollingUpdate {
        this.currentMap ?? {^this};
        this.currentMap[\poll] ??  {^this};

        this.currentMap[\poll].asArray.do{|funcName|
            functions[funcName].valueArray(this.activeSources.collect{|src|
                if(src.class==ActionEventEmitter){src.source}{src}
            }, this)
        }
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



// sources can be specified as Mktl element names arrays
MKtlDepMap : ActionEventMDepMap {
    var <ktl;
    var <elementActionMap;

    *new {|ktl, funcDict, modesMap, initMode, modeless=false|
        var obj;
        modesMap = modesMap ?? {IdentityDictionary[]};
        obj = super.new(funcDict, modesMap, nil, modeless).init(nil, modeless);
        ktl !? {obj.ktl = ktl};
        initMode !? {obj.mode = initMode};
        ^obj
    }

    ktl_{|newKtl|
        ktl = newKtl;
        // reload current mode on ktl change
        mode !? {this.mode = mode};
    }

    startListening {
        this.currentMap ?? {
            ^Error("MDep: can't start listening without a mode map").throw
        };
        if(isListening) { this.stopListening };
        elementActionMap = IdentityDictionary[];
        activeSources = this.currentSources.collectAs({|src|
            if(this.isMktlPath(src)){
                this.prListenToMktlPath(src)
            }{
                this.prListenToObject(src)
            }

        },Array).flat;
        isListening = true;
    }

    isMktlPath {|obj|
        if(obj.isArray){
            ^obj.every{|t| t.isString or: t.isSymbol or: t.isNumber or: t.isArray}
        }{
            ^(obj.isString or: obj.isSymbol or: obj.isNumber)
        }
    }

    prListenToMktlPath {|src|
        var elPath = src.asArray;
        src = ktl.elAt(*elPath);
        //^this.prMapMktlElement(src).asArray.flat;
        if(src.size > 0){
            ^src.collect{|el|
                elementActionMap[el] = elementActionMap[el].asArray.add(elPath);
                ActionEventEmitter(el).addDependant(this)
            }
        }{
            elementActionMap[src] = elementActionMap[src].asArray.add(elPath);
            ^ActionEventEmitter(src).addDependant(this)
        }
    }

    prMapMktlElement {|src, path|
        if(src.isArray){
            ^src.collect{|nested| this.prMapMktlElement(nested,path)}
        }{
            if(src.size > 0){
                ^src.collect{|el|
                    elementActionMap[el] = elementActionMap[el].asArray.add(path);
                    ActionEventEmitter(el).addDependant(this)
                }
            }{
                elementActionMap[src] = elementActionMap[src].asArray.add(path);
                ^ActionEventEmitter(src).addDependant(this)
            }
        }
    }


    prListenToObject {|src|
        elementActionMap[src] = elementActionMap[src].asArray.add(src);
        ^src.addDependant(this)
    }

    getActionsFromElement {|el,evName|
        ^elementActionMap[el].collect{|name| this.currentMap[name][evName] }.reject(_.isNil)
    }

    update { arg srcObj, evName ... evArgs;
        this.currentMap ?? {^nil};
        this.functions ?? {"MktlDepMap.functions is nil".warn; ^nil};
        this.getActionsFromElement(srcObj, evName).flatten.do{|funcName|
            var func = functions[funcName];
            if(func.isNil){
                "Function '%' not registered".format(funcName).warn;
            }{
                func.valueArray( srcObj, evArgs, evName, this)
            }
        }
	}

}
