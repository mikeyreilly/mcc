package com.quaxt.mcc.optimizer;

import com.quaxt.mcc.AbstractValue;
import com.quaxt.mcc.tacky.VarIr;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public interface TransferFunction<V extends AbstractValue> {
    void transfer(BasicBlock block, Set<V> endLiveVars,
                  HashMap<Integer, Set<V>[]> instructionAnnotations,
                  HashMap<Integer, Set<V>> blockAnnotations,
                  Object otherArgs);
}
