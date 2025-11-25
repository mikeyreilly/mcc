package com.quaxt.mcc;

import com.quaxt.mcc.tacky.PointerInit;
import com.quaxt.mcc.tacky.StringInit;

public sealed interface StaticInit permits BoolInit,
        CharInit,
        DoubleInit,
        FloatInit,
        IntInit,
        LongInit,
        LongLongInit,
        ShortInit,
        UCharInit,
        UIntInit,
        ULongInit,
        ULongLongInit,
        UShortInit,
        ZeroInit,
        PointerInit,
        StringInit {
}
