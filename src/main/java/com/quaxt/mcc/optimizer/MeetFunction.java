package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.AbstractValue;

import java.util.HashMap;
import java.util.Set;

public interface MeetFunction<V extends AbstractValue> {
    Set<V> meet(BasicBlock block, HashMap<Integer, Set<V>> blockAnnotations,
                Object otherArg);
}
