package com.quaxt.mcc.tacky;

import com.quaxt.mcc.StaticInit;

public record StringInit(String str, boolean nullTerminated) implements StaticInit {
}
