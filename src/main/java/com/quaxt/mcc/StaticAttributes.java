package com.quaxt.mcc;

import com.quaxt.mcc.parser.StorageClass;

public record StaticAttributes(InitialValue init, boolean global, StorageClass storageClass) implements IdentifierAttributes {
    @Override
    public boolean defined() {
        return false;
    }
}
