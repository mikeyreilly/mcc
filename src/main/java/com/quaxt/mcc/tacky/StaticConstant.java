package com.quaxt.mcc.tacky;

import com.quaxt.mcc.StaticInit;
import com.quaxt.mcc.semantic.Type;

public record StaticConstant(String name, Type t, StaticInit init) implements TopLevel {
}
