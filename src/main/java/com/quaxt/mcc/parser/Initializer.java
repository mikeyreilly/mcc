package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Type;

public sealed interface Initializer permits SingleInit, CompoundInit{
    Type type();
}
