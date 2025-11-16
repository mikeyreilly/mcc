package com.quaxt.mcc.tacky;

import com.quaxt.mcc.atomics.MemoryOrder;

public record AtomicStore(ValIr val, VarIr ptr, MemoryOrder memoryOrder) implements InstructionIr {}
