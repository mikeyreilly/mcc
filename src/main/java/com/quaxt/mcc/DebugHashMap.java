package com.quaxt.mcc;

import java.util.HashMap;

public class DebugHashMap<K,V> extends HashMap<K,V> {
    public DebugHashMap(int size) {
        super(size);
    }

    public DebugHashMap() {
        super();
    }

    // swapping hashmap for this class and putting a breakpoint on its put method is more usable than putting the breakpoint on HashMap.put
    public V put(K k, V v){
        return super.put(k,v);
    }
}
