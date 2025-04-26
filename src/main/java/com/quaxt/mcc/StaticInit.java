package com.quaxt.mcc;

import com.quaxt.mcc.tacky.CharInit;
import com.quaxt.mcc.tacky.PointerInit;
import com.quaxt.mcc.tacky.StringInit;
import com.quaxt.mcc.tacky.UCharInit;

public sealed interface StaticInit permits LongInit, DoubleInit, IntInit, UIntInit, ULongInit, ZeroInit, CharInit, PointerInit, StringInit, UCharInit {
}
