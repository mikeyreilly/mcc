package com.quaxt.mcc.tacky;

public sealed interface ExpResult permits BitFieldSubObject,
        BitFieldSubObjectViaPointer,
        DereferencedPointer,
        PlainOperand,
        SubObject {
}
