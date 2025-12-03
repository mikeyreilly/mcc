package com.quaxt.mcc;

public record ZeroInit(long bytes) implements StaticInit {
    public ZeroInit(long bytes){
        this.bytes=bytes;
    }
}
