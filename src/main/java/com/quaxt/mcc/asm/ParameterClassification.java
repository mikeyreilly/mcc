package com.quaxt.mcc.asm;

import java.util.ArrayList;

public record ParameterClassification(ArrayList<TypedOperand> integerArguments,
                               ArrayList<Operand> doubleArguments,
                               ArrayList<TypedOperand> stackArguments) {}
