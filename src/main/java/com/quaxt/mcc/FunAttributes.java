package com.quaxt.mcc;

public record FunAttributes(boolean defined, boolean global,
                            boolean inline) implements IdentifierAttributes {
}
