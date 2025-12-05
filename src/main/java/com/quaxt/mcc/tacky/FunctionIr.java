package com.quaxt.mcc.tacky;

import com.quaxt.mcc.parser.Var;
import com.quaxt.mcc.semantic.FunType;
import com.quaxt.mcc.semantic.Type;

import java.util.List;

public record FunctionIr(String name, boolean global, List<Var> type,
                         List<InstructionIr> instructions,
                         FunType funType,
                         boolean callsVaStart,
                         boolean inline) implements TopLevel {

    public Type returnType() {
        return funType.ret();
    }

}
