package com.quaxt.mcc.parser;

import com.quaxt.mcc.semantic.Primitive;
import com.quaxt.mcc.semantic.Type;

import java.util.ArrayList;

public record EnumSpecifier(Type declaredType,
                            String tag,
                            ArrayList<Enumerator> enumerators) implements Declaration, TypeSpecifier {

    public Type type() {
        if (enumerators == null) return Primitive.INT;
        return enumerators.getFirst().value().type();
    }
}
