package com.quaxt.mcc.tacky;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.StaticInit;

public record StaticVariable(String name, boolean global, Type t, StaticInit init) implements TopLevel {
}
