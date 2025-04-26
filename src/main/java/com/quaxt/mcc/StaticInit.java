package com.quaxt.mcc;

import com.quaxt.mcc.tacky.PointerInit;
import com.quaxt.mcc.tacky.StringInit;

public sealed interface StaticInit  permits LongInit, DoubleInit, IntInit, UIntInit, ULongInit, ZeroInit, CharInit, PointerInit, StringInit, UCharInit {
}
