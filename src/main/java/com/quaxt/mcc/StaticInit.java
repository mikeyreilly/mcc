package com.quaxt.mcc;

import com.quaxt.mcc.tacky.PointerInit;
import com.quaxt.mcc.tacky.StringInit;

public sealed interface StaticInit permits BoolInit,
        CharInit,
        DoubleInit,
        FloatInit,
        IntInit,
        LongInit,
        ShortInit,
        UCharInit,
        UIntInit,
        ULongInit,
        UShortInit,
        ZeroInit,
        PointerInit,
        StringInit {
}
