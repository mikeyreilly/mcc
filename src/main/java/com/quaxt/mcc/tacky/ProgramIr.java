package com.quaxt.mcc.tacky;

import java.util.List;

public record ProgramIr(List<TopLevel> topLevels,
                        java.util.ArrayList<com.quaxt.mcc.parser.Position> positions) {
}

