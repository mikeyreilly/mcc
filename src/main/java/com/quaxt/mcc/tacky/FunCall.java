package com.quaxt.mcc.tacky;

import java.util.ArrayList;
import java.util.List;

public record FunCall(String name, ArrayList<ValIr> args, ValIr dst) implements InstructionIr {
}
