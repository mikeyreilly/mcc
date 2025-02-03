package com.quaxt.mcc.semantic;

import java.util.List;

public record FunType(List<Type> params, Type ret) implements Type {
}
