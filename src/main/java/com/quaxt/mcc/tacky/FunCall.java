package com.quaxt.mcc.tacky;

import java.util.List;

public record FunCall(String name, List<ValIr> args, ValIr dst) implements InstructionIr {
}
