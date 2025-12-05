package com.quaxt.mcc;

import com.quaxt.mcc.asm.Codegen;
import com.quaxt.mcc.asm.ProgramAsm;
import com.quaxt.mcc.asm.Todo;
import com.quaxt.mcc.optimizer.Optimizer;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.semantic.*;
import com.quaxt.mcc.tacky.IrGen;
import com.quaxt.mcc.tacky.ProgramIr;
import com.quaxt.mcc.tacky.ValIr;
import com.quaxt.mcc.tacky.VarIr;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.quaxt.mcc.parser.Nullptr.NULLPTR;
import static com.quaxt.mcc.semantic.Primitive.*;

import java.util.logging.*;

public class Mcc {
    private static final Logger LOGGER = Logger.getLogger(Mcc.class.getName());

    public static final HashMap<String, SymbolTableEntry> SYMBOL_TABLE = new HashMap<>(){
        public SymbolTableEntry put(String key, SymbolTableEntry value) {
            return super.put(key, value);
        }
    };
    public static final HashMap<String, StructDef> TYPE_TABLE = new HashMap<>(){
        public StructDef put(String key, StructDef value) {
            return super.put(key, value);
        }
    };
    public static final HashMap<String, EnumSpecifier> ENUM_MAP = new HashMap<>();

    public static final AtomicLong TEMP_COUNT = new AtomicLong(0L);

    public static void setAliased(String identifier) {
        SYMBOL_TABLE.get(identifier).aliased = true;
    }
    public static String makeTemporary(String prefix) {
        return prefix + TEMP_COUNT.getAndIncrement();
    }

    public static int variableAlignment(Type type) {
        return switch (type) {
            case Array(Type element, Constant _) -> {
                long size = Mcc.size(type);
                yield size < 16L && element.isScalar() ?
                        (int) Mcc.size(element) : 16;
            }
            case FunType _ -> 0;
            case Pointer _, NullptrT _ -> 8;
            case CHAR -> 1;
            case UCHAR -> 1;
            case SCHAR -> 1;
            case SHORT -> 2;
            case USHORT -> 2;
            case INT -> 4;
            case FLOAT -> 4;
            case UINT -> 4;
            case LONG -> 8;
            case ULONG -> 8;
            case ULONGLONG -> 8;
            case LONGLONG -> 8;
            case DOUBLE -> 8;
            case VOID -> 1;
            case Typeof(Exp e) when e == NULLPTR-> 8;
            case Structure(boolean isUnion, String tag, StructDef _) -> {
                var st = TYPE_TABLE.get(tag);
                yield st == null ? 1 : st.alignment();
            }
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    public static int typeAlignment(Type type) {
        return switch (type) {
            case Array(Type element, Constant _) -> Mcc.typeAlignment(element);
            case FunType _ -> 0;
            case Pointer _, NullptrT _ -> 8;
            case CHAR -> 1;
            case UCHAR -> 1;
            case SCHAR -> 1;
            case SHORT -> 2;
            case USHORT -> 2;
            case INT -> 4;
            case UINT -> 4;
            case FLOAT -> 4;
            case LONG -> 8;
            case ULONG -> 8;
            case LONGLONG -> 8;
            case ULONGLONG -> 8;
            case DOUBLE -> 8;
            case VOID -> 1;
            case Structure(boolean isUnion, String tag, StructDef _) -> TYPE_TABLE.get(tag).alignment();
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    public static long size(Type type) {
        return switch (type) {

            case Array(Type element, Constant arraySize) -> arraySize == null ? 0L: // arraySize will be null for
                    size(element) * arraySize.toLong();
            case FunType _ -> 0;
            case Pointer _, NullptrT _ -> 8;
            case BOOL->1;
            case CHAR -> 1;
            case UCHAR -> 1;
            case SCHAR -> 1;
            case SHORT -> 2;
            case USHORT -> 2;
            case INT -> 4;
            case UINT -> 4;
            case FLOAT -> 4;
            case LONG -> 8;
            case ULONG -> 8;
            case LONGLONG -> 8;
            case ULONGLONG -> 8;
            case DOUBLE -> 8;
            case VOID -> 1;

            case Structure(boolean isUnion, String tag, StructDef _) -> {
                var st = TYPE_TABLE.get(tag);
                yield st == null ? -1 : st.size();
            }
            case Typeof typeof -> 0L;
            case TypeofT typeofT -> 0L;
        };
    }

    public static Type valToType(ValIr val) {
        return switch (val) {
            case Constant constant -> constant.type();
            case VarIr(String identifier) ->
                    SYMBOL_TABLE.get(identifier).type();
        };
    }

    public static boolean isSigned(Type srcT) {
        return switch (srcT) {
            case CHAR, SCHAR, INT, LONG, DOUBLE -> true;
            default -> false;
        };
    }

    public static ArrayList<MemberEntry> members(Structure s) {
        StructDef structDef = Mcc.TYPE_TABLE.get(s.tag());
        return structDef.members();
    }

    public static Constant lookupEnumConstant(String name) {
        for (EnumSpecifier es : Mcc.ENUM_MAP.values()){
            for (var e : es.enumerators()){
                if (e.name().equals(name)){
                    return e.value();
                }
            }
        }
        return null;
    }

    public static Type type(VarIr v) {
        return Mcc.SYMBOL_TABLE.get(v.identifier()).type();
    }

    public static FunType funType(VarIr v) {
        Type t=type(v);
        if (t instanceof FunType ft) return ft;
        if (t instanceof Pointer(FunType ft)) return ft;
        throw new IllegalArgumentException("Required function or pointer to function. Found "+t);
    }

    enum Mode {LEX, PARSE, VALIDATE, CODEGEN, COMPILE, TACKY, ASSEMBLE, DUMMY}



    public static int preprocess(Path cFile,
                                 Path iFile) throws IOException,
            InterruptedException {
        return startProcess("gcc", "-E", "-std=c23", "-D_POSIX_C_SOURCE=200809",
                cFile.toString(), "-o", iFile.toString());
    }

    public static int startProcess(String... args) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(args).inheritIO();
        return pb.start().waitFor();
    }

    private static int assembleAndLink(Path asmFile, String bareFileName,
                                       boolean doNotCompile,
                                       List<String> libs) throws InterruptedException, IOException {
        List<String> gccArgs = new ArrayList<>(Arrays.asList("gcc",
                asmFile.toString()));
        if (doNotCompile) {
            gccArgs.add("-c");
        }
        gccArgs.addAll(Arrays.asList("-o",
                asmFile.resolveSibling(bareFileName + (doNotCompile ? ".o" :
                        "")).toString()));
        gccArgs.addAll(libs);
        return startProcess(gccArgs.toArray(new String[0]));
    }

    public static void main(String[] args) {
        try {
            System.exit(mcc(args));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static int mcc(String... args0) throws Exception {
        LOGGER.info("started with args " + String.join(" ", args0));

        SYMBOL_TABLE.clear();
        TYPE_TABLE.clear();
        TEMP_COUNT.set(0L);

        ArrayList<String> args =
                Arrays.stream(args0).collect(Collectors.toCollection(ArrayList::new));

        Mode mode = Mode.ASSEMBLE;
        boolean doNotCompile = false;
        List<String> libs = new ArrayList<>();
        EnumSet<Optimization> optimizations =
                EnumSet.noneOf(Optimization.class);
        for (int i = args.size() - 1; i >= 0; i--) {
            String arg = args.get(i);
            Mode newMode = switch (arg) {
                case "--lex" -> Mode.LEX;
                case "--parse" -> Mode.PARSE;
                case "--validate" -> Mode.VALIDATE;
                case "--codegen" -> Mode.CODEGEN;
                case "--tacky" -> Mode.TACKY;
                case "--fold-constants" -> {
                    optimizations.add(Optimization.FOLD_CONSTANTS);
                    yield Mode.DUMMY;
                }
                case "--propagate-copies" -> {
                    optimizations.add(Optimization.PROPAGATE_COPIES);
                    yield Mode.DUMMY;
                }
                case "--eliminate-unreachable-code" -> {
                    optimizations.add(Optimization.ELIMINATE_UNREACHABLE_CODE);
                    yield Mode.DUMMY;
                }
                case "--eliminate-dead-stores" -> {
                    optimizations.add(Optimization.ELIMINATE_DEAD_STORES);
                    yield Mode.DUMMY;
                }
                case "--optimize" -> {
                    optimizations = EnumSet.allOf(Optimization.class);
                    yield Mode.DUMMY;
                }
                case "-S", "-s" -> Mode.COMPILE;
                case "-c" -> {
                    doNotCompile = true;
                    yield Mode.DUMMY;
                }
                default -> {
                    if (arg.startsWith("-l")) {
                        libs.addFirst(arg);
                        yield Mode.DUMMY;
                    }
                    yield null;
                }
            };
            if (newMode == Mode.DUMMY) {
                args.remove(i);
            } else if (newMode != null) {
                mode = newMode;
                args.remove(i);
            }
        }
        Path srcFile = Path.of(args.getFirst());
        if (args.size() > 1) {
            System.err.println("unrecognized argument: " + args.get(1));
            return -1;
        }
        String bareFileName = removeEnding(srcFile.getFileName().toString());
        Path intermediateFile = srcFile.resolveSibling(bareFileName + ".i");

        int preprocessExitCode = preprocess(srcFile, intermediateFile);
        if (preprocessExitCode != 0) {
            return preprocessExitCode;
        }
        String cSource = Files.readString(intermediateFile);
        Files.delete(intermediateFile);
        Map<String, SemanticAnalysis.Entry> identifierMap = new HashMap<>();
        Map<String, SemanticAnalysis.TagEntry> structureMap = new DebugHashMap<>();
        ArrayList<Declaration> builtinDeclarations = new ArrayList<>();

        mccHelper("""
                struct __builtin_va_list_item {
                    unsigned int gp_offset;
                    unsigned int fp_offset;
                    void *overflow_arg_area;
                    void *reg_save_area;
                };

                typedef struct __builtin_va_list_item  __builtin_va_list[1];
                """, Mode.VALIDATE, EnumSet.noneOf(Optimization.class), null,
                null, true, Collections.emptyList(), identifierMap, structureMap, builtinDeclarations, null);
        BUILTIN_VA_LIST =
                (Array) Mcc.SYMBOL_TABLE.get("__builtin_va_list").type();
        ArrayList<Declaration> declarations = new ArrayList<>();

        return mccHelper(cSource, mode, optimizations, srcFile, bareFileName, doNotCompile, libs, identifierMap, structureMap, declarations, builtinDeclarations);
    }
    public static Array BUILTIN_VA_LIST = null;
    private static int mccHelper(String cSource, Mode mode,
                                 EnumSet<Optimization> optimizations,
                                 Path srcFile, String bareFileName,
                                 boolean doNotCompile,
                                 List<String> libs,
                                 Map<String, SemanticAnalysis.Entry> identifierMap,
                                 Map<String, SemanticAnalysis.TagEntry> structureMap,
                                 ArrayList<Declaration> declarations,
                                 ArrayList<Declaration> builtinDeclarations) throws IOException, InterruptedException {
        TokenList l = Lexer.lex(cSource);
        if (mode == Mode.LEX) {
            return 0;
        }
        Program program = Parser.parseProgram(l, declarations);
        if (!l.isEmpty()) {
            throw makeErr("Unexpected token " + l.getFirst(), l);
        }
        if (mode == Mode.PARSE) {
            return 0;
        }

//        Map<String, SemanticAnalysis.Entry> identifierMap = new HashMap<>();
//        Map<String, SemanticAnalysis.TagEntry> structureMap = new DebugHashMap<>();
        program =
                SemanticAnalysis.resolveProgram(program, structureMap, identifierMap);
        SemanticAnalysis.typeCheckProgram(program);
        program = SemanticAnalysis.loopLabelProgram(program);
        // MR-TODO no need to do this redundant work on builtindeclarations

        if (builtinDeclarations!=null){
            for (int i = builtinDeclarations.size() - 1; i >= 0; i--) {
                Declaration x = builtinDeclarations.get(i);
                program.declarations().addFirst(x);
            }
        }

        if (mode == Mode.VALIDATE) {
            return 0;
        }
        ProgramIr programIr = IrGen.programIr(program);
        if (mode == Mode.TACKY) {
            return 0;
        }
        if (!optimizations.isEmpty()) {
            programIr = Optimizer.optimize(programIr, optimizations);
        }

        ProgramAsm programAsm = Codegen.generateProgramAssembly(programIr);
        if (mode == Mode.CODEGEN) {
            return 0;
        }
        Path asmFile = srcFile.resolveSibling(bareFileName + ".s");
        try (PrintWriter pw =
                     new PrintWriter(Files.newBufferedWriter(asmFile))) {
            programAsm.emitAsm(pw);
            pw.flush();
        }

        if (mode == Mode.COMPILE) {
            return 0;
        }
        int exitCode = assembleAndLink(asmFile, bareFileName, doNotCompile, libs);
        Files.delete(asmFile);
        return exitCode;
    }


    private static String removeEnding(String fileName) {
        String ending = ".c";
        if (fileName.endsWith(ending)) {
            return fileName.substring(0, fileName.length() - ending.length());
        }
        throw new IllegalArgumentException(fileName + " does not have ending "
                + ending);
    }

    public static Err makeErr(String s, TokenList tokens) {
        if (tokens!=null) {
            return new Err(s+" "+ tokens.positionString());
        }
        return new Err(s);
    }

}
