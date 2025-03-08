package com.quaxt.mcc;

import java.util.List;

public record Initial(List<StaticInit> initList) implements InitialValue {
}
