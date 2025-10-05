package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public sealed interface Initializer permits CompoundInit, SingleInit {
    Type type();
}
