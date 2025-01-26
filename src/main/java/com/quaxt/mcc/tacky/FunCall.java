package com.quaxt.mcc.tacky;

import java.util.ArrayList;

public record FunCall(String name, ArrayList<ValIr> args,
                      ValIr dst) implements InstructionIr {
}
