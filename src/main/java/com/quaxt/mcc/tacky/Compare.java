package com.quaxt.mcc.tacky;

import com.quaxt.mcc.semantic.Type;

public record Compare(Type type, ValIr subtrahend, ValIr minuend) implements InstructionIr {}
