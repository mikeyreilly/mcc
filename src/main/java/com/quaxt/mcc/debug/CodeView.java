package com.quaxt.mcc.debug;

import com.quaxt.mcc.*;
import com.quaxt.mcc.asm.*;
import com.quaxt.mcc.parser.Constant;
import com.quaxt.mcc.parser.Exp;
import com.quaxt.mcc.semantic.*;

import java.io.PrintWriter;
import java.util.*;

import static com.quaxt.mcc.Mcc.*;
import static com.quaxt.mcc.asm.ProgramAsm.windowsTotalStackBytes;
import static com.quaxt.mcc.semantic.SemanticAnalysis.isComplete;

public final class CodeView {
    private static final int ROOT_SCOPE_ID = 0;
    private static final int FIRST_TYPE_INDEX = 0x1000;

    private static final int S_FRAMEPROC = 0x1012;
    private static final int S_UDT = 0x1108;
    private static final int S_GPROC32_ID = 0x1147;
    private static final int S_LOCAL = 0x113e;
    private static final int S_PROC_ID_END = 0x114f;

    private static final int LF_POINTER = 0x1002;
    private static final int LF_PROCEDURE = 0x1008;
    private static final int LF_ARGLIST = 0x1201;
    private static final int LF_FIELDLIST = 0x1203;
    private static final int LF_BITFIELD = 0x1205;
    private static final int LF_STRUCTURE = 0x1505;
    private static final int LF_UNION = 0x1506;
    private static final int LF_ARRAY = 0x1503;
    private static final int LF_MEMBER = 0x150d;
    private static final int LF_FUNC_ID = 0x1601;

    public record DebugFunction(int id, String symbol, String endLabel,
                                FunctionIr function) {
    }

    private CodeView() {
    }

    public static void emit(PrintWriter out, List<DebugFunction> functions) {
        TypeTable types = TypeTable.build(functions);
        types.emit(out);
        emitSymbols(out, functions, types);
    }

    private static void emitSymbols(PrintWriter out,
                                    List<DebugFunction> functions,
                                    TypeTable types) {
        out.println(".section .debug$S,\"dr\"");
        printIndent(out, ".p2align 2");
        printIndent(out, ".long 4");
        for (DebugFunction function : functions) {
            emitFunctionSymbols(out, function, types);
            printIndent(out, ".cv_linetable " + function.id + ", " +
                    function.symbol + ", " + function.endLabel);
        }
        printIndent(out, ".cv_filechecksums");
        printIndent(out, ".cv_stringtable");
        emitUdts(out, types);
    }

    private static void emitFunctionSymbols(PrintWriter out,
                                            DebugFunction debugFunction,
                                            TypeTable types) {
        String start = makeTemporary(".LcvSubsection.");
        String end = makeTemporary(".LcvSubsectionEnd.");
        printIndent(out, ".long 241");
        printIndent(out, ".long " + end + "-" + start);
        out.println(start + ":");

        FunctionIr function = debugFunction.function;
        emitRecord(out, S_GPROC32_ID, "S_GPROC32_ID", () -> {
            printIndent(out, ".long 0");
            printIndent(out, ".long 0");
            printIndent(out, ".long 0");
            printIndent(out, ".long " + debugFunction.endLabel + "-" +
                    debugFunction.symbol);
            printIndent(out, ".long 0");
            printIndent(out, ".long 0");
            printIndent(out, ".long 0x" +
                    Integer.toHexString(types.funcId(function)));
            printIndent(out, ".secrel32 " + debugFunction.symbol);
            printIndent(out, ".secidx " + debugFunction.symbol);
            printIndent(out, ".byte 0xc0");
            asciz(out, function.name);
        });

        long frameSize = windowsTotalStackBytes(function);
        long calleeSavedBytes = 8L * function.calleeSavedRegs.length;
        emitRecord(out, S_FRAMEPROC, "S_FRAMEPROC", () -> {
            printIndent(out, ".long " + frameSize);
            printIndent(out, ".long 0");
            printIndent(out, ".long 0");
            printIndent(out, ".long " + calleeSavedBytes);
            printIndent(out, ".long 0");
            printIndent(out, ".short 0");
            printIndent(out, ".long 0x28000");
        });

        emitLocals(out, debugFunction, types);

        emitRecord(out, S_PROC_ID_END, "S_PROC_ID_END", () -> {
        });
        out.println(end + ":");
        printIndent(out, ".p2align 2");
    }

    private static void emitLocals(PrintWriter out,
                                   DebugFunction debugFunction,
                                   TypeTable types) {
        FunctionIr function = debugFunction.function;
        if (function.debugLocals == null) return;

        Map<Integer, DebugScope> scopes = new HashMap<>();
        if (function.debugScopes != null) {
            for (DebugScope scope : function.debugScopes) {
                scopes.put(scope.id(), scope);
            }
        }

        for (DebugLocal local : function.debugLocals) {
            SymbolTableEntry entry = SYMBOL_TABLE.get(local.internalName());
            if (entry == null) continue;
            Integer type = types.typeIndex(entry.type());
            if (type == null) continue;
            Reg reg = function.debugRegisterTable == null ? null :
                    function.debugRegisterTable.get(local.internalName());
            Long offset = null;
            if (reg == null && function.varTable != null) {
                offset = function.varTable.get(local.internalName());
            }
            if (reg == null && offset == null) continue;

            DebugScope scope = scopes.get(local.scopeId());
            String start = scope == null ? debugFunction.symbol :
                    scope.startLabel();
            String end = scope == null ? debugFunction.endLabel :
                    scope.endLabel();

            emitRecord(out, S_LOCAL, "S_LOCAL", () -> {
                printIndent(out, ".long 0x" + Integer.toHexString(type));
                printIndent(out, ".short " + (local.parameter() ? 1 : 0));
                asciz(out, local.displayName());
            });
            if (reg != null) {
                Integer cvReg = codeViewRegister(reg, entry.type());
                if (cvReg != null) {
                    printIndent(out, ".cv_def_range " + start + " " + end +
                            ", reg, " + cvReg);
                }
            } else {
                long rbpRelativeOffset =
                        offset - windowsTotalStackBytes(function);
                printIndent(out, ".cv_def_range " + start + " " + end +
                        ", frame_ptr_rel, " + rbpRelativeOffset);
            }
        }
    }

    private static void emitUdts(PrintWriter out, TypeTable types) {
        if (types.udts.isEmpty()) return;
        String start = makeTemporary(".LcvUdtSubsection.");
        String end = makeTemporary(".LcvUdtSubsectionEnd.");
        printIndent(out, ".long 241");
        printIndent(out, ".long " + end + "-" + start);
        out.println(start + ":");
        for (Map.Entry<String, Integer> udt : types.udts.entrySet()) {
            emitRecord(out, S_UDT, "S_UDT", () -> {
                printIndent(out, ".long 0x" +
                        Integer.toHexString(udt.getValue()));
                asciz(out, udt.getKey());
            });
        }
        out.println(end + ":");
        printIndent(out, ".p2align 2");
    }

    private static Integer codeViewRegister(Reg reg, Type type) {
        return switch (reg) {
            case IntegerReg r -> codeViewIntegerRegister(r, Mcc.size(type));
            case DoubleReg r -> codeViewXmmRegister(r);
            case Pseudo _ -> null;
        };
    }

    private static Integer codeViewIntegerRegister(IntegerReg reg, long size) {
        return switch (reg) {
            case AX -> switch ((int) size) {
                case 1 -> 1;
                case 2 -> 9;
                case 4 -> 17;
                default -> 328;
            };
            case CX -> switch ((int) size) {
                case 1 -> 2;
                case 2 -> 10;
                case 4 -> 18;
                default -> 330;
            };
            case DX -> switch ((int) size) {
                case 1 -> 3;
                case 2 -> 11;
                case 4 -> 19;
                default -> 331;
            };
            case BX -> switch ((int) size) {
                case 1 -> 4;
                case 2 -> 12;
                case 4 -> 20;
                default -> 329;
            };
            case SP -> switch ((int) size) {
                case 1 -> 327;
                case 2 -> 13;
                case 4 -> 21;
                default -> 335;
            };
            case BP -> switch ((int) size) {
                case 1 -> 326;
                case 2 -> 14;
                case 4 -> 22;
                default -> 334;
            };
            case SI -> switch ((int) size) {
                case 1 -> 324;
                case 2 -> 15;
                case 4 -> 23;
                default -> 332;
            };
            case DI -> switch ((int) size) {
                case 1 -> 325;
                case 2 -> 16;
                case 4 -> 24;
                default -> 333;
            };
            case R8 -> switch ((int) size) {
                case 1 -> 344;
                case 2 -> 352;
                case 4 -> 360;
                default -> 336;
            };
            case R9 -> switch ((int) size) {
                case 1 -> 345;
                case 2 -> 353;
                case 4 -> 361;
                default -> 337;
            };
            case R10 -> switch ((int) size) {
                case 1 -> 346;
                case 2 -> 354;
                case 4 -> 362;
                default -> 338;
            };
            case R11 -> switch ((int) size) {
                case 1 -> 347;
                case 2 -> 355;
                case 4 -> 363;
                default -> 339;
            };
            case R12 -> switch ((int) size) {
                case 1 -> 348;
                case 2 -> 356;
                case 4 -> 364;
                default -> 340;
            };
            case R13 -> switch ((int) size) {
                case 1 -> 349;
                case 2 -> 357;
                case 4 -> 365;
                default -> 341;
            };
            case R14 -> switch ((int) size) {
                case 1 -> 350;
                case 2 -> 358;
                case 4 -> 366;
                default -> 342;
            };
            case R15 -> switch ((int) size) {
                case 1 -> 351;
                case 2 -> 359;
                case 4 -> 367;
                default -> 343;
            };
        };
    }

    private static int codeViewXmmRegister(DoubleReg reg) {
        int ordinal = reg.ordinal();
        return ordinal < 8 ? 154 + ordinal : 252 + ordinal - 8;
    }

    private static void emitRecord(PrintWriter out, int kind, String comment,
                                   Runnable body) {
        String start = makeTemporary(".LcvRecord.");
        String end = makeTemporary(".LcvRecordEnd.");
        printIndent(out, ".short " + end + "-" + start);
        out.println(start + ":");
        printIndent(out, ".short " + kind + " # " + comment);
        body.run();
        printIndent(out, ".p2align 2");
        out.println(end + ":");
    }

    private static void asciz(PrintWriter out, String value) {
        printIndent(out, ".asciz \"" + escape(value) + "\"");
    }

    private static String escape(String value) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') out.append("\\\\");
            else if (c == '"') out.append("\\\"");
            else if (c == '\n') out.append("\\n");
            else if (c < 32 || c > 126) {
                String octal = Integer.toString(c & 0xff, 8);
                out.append("\\000", 0, 4 - octal.length()).append(octal);
            } else out.append(c);
        }
        return out.toString();
    }

    private static final class TypeTable {
        private final LinkedHashMap<Type, Integer> types = new LinkedHashMap<>();
        private final IdentityHashMap<FunctionIr, Integer> funcIds =
                new IdentityHashMap<>();
        private final LinkedHashMap<String, Integer> udts = new LinkedHashMap<>();
        private final List<Runnable> emitters = new ArrayList<>();
        private int next = FIRST_TYPE_INDEX;

        static TypeTable build(List<DebugFunction> functions) {
            TypeTable table = new TypeTable();
            table.addReachableTypes(functions);
            for (DebugFunction function : functions) {
                FunctionIr f = function.function;
                int argList = table.addArgList(f.funType.params());
                int procedure = table.addProcedure(f.funType, argList);
                int funcId = table.reserve();
                table.funcIds.put(f, funcId);
                table.emitters.add(() -> table.emitTypeRecord(LF_FUNC_ID,
                        "LF_FUNC_ID", () -> {
                            printIndent(table.out, ".long 0");
                            printIndent(table.out, ".long 0x" +
                                    Integer.toHexString(procedure));
                            asciz(table.out, f.name);
                        }));
            }
            return table;
        }

        private PrintWriter out;

        void emit(PrintWriter out) {
            this.out = out;
            out.println(".section .debug$T,\"dr\"");
            printIndent(out, ".p2align 2");
            printIndent(out, ".long 4");
            for (Runnable emitter : emitters) {
                emitter.run();
            }
            this.out = null;
        }

        Integer typeIndex(Type type) {
            return primitiveIndex(type).orElseGet(() -> types.get(type));
        }

        int funcId(FunctionIr function) {
            return funcIds.get(function);
        }

        private void addReachableTypes(List<DebugFunction> functions) {
            for (SymbolTableEntry entry : SYMBOL_TABLE.values()) {
                addType(entry.type());
            }
            for (DebugFunction function : functions) {
                addType(function.function.funType);
                if (function.function.debugLocals != null) {
                    for (DebugLocal local : function.function.debugLocals) {
                        SymbolTableEntry entry =
                                SYMBOL_TABLE.get(local.internalName());
                        if (entry != null) addType(entry.type());
                    }
                }
            }
        }

        private int addType(Type type) {
            Optional<Integer> primitive = primitiveIndex(type);
            if (primitive.isPresent()) return primitive.get();
            if (types.containsKey(type)) return types.get(type);
            return switch (type) {
                case Pointer(Type referenced) -> addPointer(type, referenced);
                case Array(Type element, Constant size) ->
                        addArray(type, element, size);
                case FunType funType -> {
                    int argList = addArgList(funType.params());
                    yield addProcedure(funType, argList);
                }
                case Structure(boolean isUnion, String tag, StructDef _) ->
                        addAggregate(type, isUnion, tag);
                case WidthRestricted(Type integerType, int width) ->
                        addBitField(type, integerType, width);
                case Aligned(Type inner, Exp _) -> addType(inner);
                default -> 0x03;
            };
        }

        private int addPointer(Type type, Type referenced) {
            int referent = addType(referenced);
            int index = reserve(type);
            emitters.add(() -> emitTypeRecord(LF_POINTER, "LF_POINTER", () -> {
                printIndent(out, ".long 0x" + Integer.toHexString(referent));
                printIndent(out, ".long 0x1000c");
            }));
            return index;
        }

        private int addArray(Type type, Type element, Constant size) {
            int elementIndex = addType(element);
            long bytes = size == null ? 0 : Mcc.size(type);
            int index = reserve(type);
            emitters.add(() -> emitTypeRecord(LF_ARRAY, "LF_ARRAY", () -> {
                printIndent(out, ".long 0x" +
                        Integer.toHexString(elementIndex));
                printIndent(out, ".long 0x23");
                numeric(out, bytes);
                asciz(out, "");
            }));
            return index;
        }

        private int addProcedure(FunType funType, int argList) {
            Integer existing = types.get(funType);
            if (existing != null) return existing;
            int ret = addType(funType.ret());
            int index = reserve(funType);
            emitters.add(() -> emitTypeRecord(LF_PROCEDURE, "LF_PROCEDURE", () -> {
                printIndent(out, ".long 0x" + Integer.toHexString(ret));
                printIndent(out, ".byte 0");
                printIndent(out, ".byte 0");
                printIndent(out, ".short " + funType.params().size());
                printIndent(out, ".long 0x" + Integer.toHexString(argList));
            }));
            return index;
        }

        private int addArgList(List<Type> params) {
            ArrayList<Integer> paramTypes = new ArrayList<>();
            for (Type param : params) paramTypes.add(addType(param));
            int index = reserve();
            emitters.add(() -> emitTypeRecord(LF_ARGLIST, "LF_ARGLIST", () -> {
                printIndent(out, ".long " + paramTypes.size());
                for (int param : paramTypes) {
                    printIndent(out, ".long 0x" +
                            Integer.toHexString(param));
                }
            }));
            return index;
        }

        private int addAggregate(Type type, boolean isUnion, String tag) {
            int forward = reserve(type);
            StructDef def = TYPE_TABLE.get(tag);
            emitters.add(() -> emitAggregateRecord(isUnion, tag, 0, 0,
                    true, 0, forward));
            if (def == null || !isComplete(type)) {
                return forward;
            }

            for (MemberEntry member : def.members()) addType(member.internalType());
            ArrayList<MemberInfo> members = new ArrayList<>();
            for (MemberEntry member : def.members()) {
                members.add(new MemberInfo(member.name(),
                        addType(member.internalType()), member.byteOffset(),
                        member instanceof BitFieldMember bitField ?
                                bitField.bitOffset() : -1));
            }
            int fieldList = reserve();
            emitters.add(() -> emitFieldList(members));
            int complete = reserve();
            types.put(type, complete);
            emitters.add(() -> emitAggregateRecord(isUnion, tag, fieldList,
                    def.size(), false, members.size(), complete));
            udts.put(tag, complete);
            return complete;
        }

        private void emitFieldList(List<MemberInfo> members) {
            emitTypeRecord(LF_FIELDLIST, "LF_FIELDLIST", () -> {
                for (MemberInfo member : members) {
                    printIndent(out, ".short " + LF_MEMBER + " # LF_MEMBER");
                    printIndent(out, ".short 3");
                    printIndent(out, ".long 0x" +
                            Integer.toHexString(member.typeIndex));
                    numeric(out, member.offset);
                    asciz(out, member.name == null ? "" : member.name);
                    int subRecordBytes = 2 + 2 + 4 + numericSize(member.offset) +
                            encodedLength(member.name == null ? "" : member.name) + 1;
                    emitLfPadding(out, subRecordBytes);
                }
            });
        }

        private int addBitField(Type type, Type integerType, int width) {
            int underlying = addType(integerType);
            int index = reserve(type);
            emitters.add(() -> emitTypeRecord(LF_BITFIELD, "LF_BITFIELD", () -> {
                printIndent(out, ".long 0x" +
                        Integer.toHexString(underlying));
                printIndent(out, ".byte " + width);
                printIndent(out, ".byte 0");
            }));
            return index;
        }

        private void emitAggregateRecord(boolean isUnion, String tag,
                                         int fieldList, long size,
                                         boolean forward, int memberCount,
                                         int expectedIndex) {
            int kind = isUnion ? LF_UNION : LF_STRUCTURE;
            emitTypeRecord(kind, isUnion ? "LF_UNION" : "LF_STRUCTURE", () -> {
                printIndent(out, ".short " + memberCount);
                printIndent(out, ".short " + (forward ? 0x80 : 0));
                printIndent(out, ".long 0x" + Integer.toHexString(fieldList));
                if (!isUnion) {
                    printIndent(out, ".long 0");
                    printIndent(out, ".long 0");
                }
                numeric(out, size);
                asciz(out, tag);
            });
        }

        private int reserve(Type type) {
            int index = reserve();
            types.put(type, index);
            return index;
        }

        private int reserve() {
            return next++;
        }

        private void emitTypeRecord(int kind, String comment, Runnable body) {
            String start = makeTemporary(".LcvType.");
            String end = makeTemporary(".LcvTypeEnd.");
            printIndent(out, ".short " + end + "-" + start);
            out.println(start + ":");
            printIndent(out, ".short " + kind + " # " + comment);
            body.run();
            printIndent(out, ".p2align 2");
            out.println(end + ":");
        }

        private record MemberInfo(String name, int typeIndex, int offset,
                                  int bitOffset) {
        }
    }

    private static Optional<Integer> primitiveIndex(Type type) {
        if (type == Primitive.VOID) return Optional.of(0x03);
        if (type == Primitive.INT) return Optional.of(0x74);
        if (type == Primitive.UINT) return Optional.of(0x75);
        if (type == Primitive.LONG) return Optional.of(0x12);
        if (type == Primitive.ULONG) return Optional.of(0x22);
        if (type == Primitive.LONGLONG) return Optional.of(0x13);
        if (type == Primitive.ULONGLONG) return Optional.of(0x23);
        if (type == Primitive.FLOAT) return Optional.of(0x40);
        if (type == Primitive.DOUBLE) return Optional.of(0x41);
        if (type == Primitive.BOOL) return Optional.of(0x30);
        if (type == Primitive.CHAR) return Optional.of(0x70);
        if (type == Primitive.SCHAR) return Optional.of(0x68);
        if (type == Primitive.UCHAR) return Optional.of(0x69);
        if (type == Primitive.SHORT) return Optional.of(0x11);
        if (type == Primitive.USHORT) return Optional.of(0x21);
        return Optional.empty();
    }

    private static void numeric(PrintWriter out, long value) {
        if (value >= 0 && value < 0x8000) {
            printIndent(out, ".short " + value);
        } else {
            printIndent(out, ".short 0x800a");
            printIndent(out, ".quad " + value);
        }
    }

    private static int numericSize(long value) {
        return value >= 0 && value < 0x8000 ? 2 : 10;
    }

    private static int encodedLength(String value) {
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            length += c < 128 ? 1 : 1;
        }
        return length;
    }

    private static void emitLfPadding(PrintWriter out, int bytesBeforePadding) {
        int padding = (4 - (bytesBeforePadding & 3)) & 3;
        for (int i = padding; i > 0; i--) {
            printIndent(out, ".byte 0x" + Integer.toHexString(0xf0 + i));
        }
    }
}
