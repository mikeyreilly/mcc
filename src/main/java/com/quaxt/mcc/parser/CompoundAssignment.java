package com.quaxt.mcc.parser;

import com.quaxt.mcc.CompoundAssignmentOperator;
import com.quaxt.mcc.semantic.Type;

public record CompoundAssignment(CompoundAssignmentOperator compOp, Exp left,
                                 Exp right,
                                 Type tempType,              // tempType is a suitable common type for left and right
                                 Type type) implements Exp { // type will be determined by left type

}
