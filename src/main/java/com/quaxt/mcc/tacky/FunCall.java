package com.quaxt.mcc.tacky;

import java.util.ArrayList;

public record FunCall(String name, ArrayList<ValIr> args, boolean varargs, boolean indirect, VarIr dst) implements InstructionIr {
}
