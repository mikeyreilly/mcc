package com.quaxt.mcc.tacky;

import com.quaxt.mcc.StaticInit;

public record PointerInit(String str, long offset) implements StaticInit {
    public PointerInit(String str) {
        this(str, 0L);
    }
}
