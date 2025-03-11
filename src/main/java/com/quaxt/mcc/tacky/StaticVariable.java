package com.quaxt.mcc.tacky;
import com.quaxt.mcc.semantic.Type;
import com.quaxt.mcc.StaticInit;
import java.util.List;

public record StaticVariable(String name, boolean global, Type t, List<StaticInit> init) implements TopLevel {
}
