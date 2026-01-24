package com.quaxt.mcc;

import com.quaxt.mcc.asm.Codegen;
import com.quaxt.mcc.asm.ProgramAsm;
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
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.quaxt.mcc.parser.Nullptr.NULLPTR;
import static com.quaxt.mcc.semantic.Primitive.*;

import java.util.logging.*;

public class Mcc {

    /* If the user defines functions of any of these functions then we ignore
     those definitions, because that's what GCC does*/
    public static final Set<String> IGNORE_LIST =
            Set.of("_Exit", "abort", "abs", "acos", "acosh", "acoshf",
                    "acoshl", "asin", "asinh", "asinhf", "asinhl", "atan",
                    "atan2", "atanh", "atanhf", "atanhl", "cabs", "cabsf",
                    "cabsl", "cacos", "cacosf", "cacosh", "cacoshf", "cacoshl"
                    , "cacosl", "calloc", "carg", "cargf", "cargl", "casin",
                    "casinf", "casinh", "casinhf", "casinhl", "casinl",
                    "catan", "catanf", "catanh", "catanhf", "catanhl",
                    "catanl", "cbrt", "cbrtf", "cbrtl", "ccos", "ccosf",
                    "ccosh", "ccoshf", "ccoshl", "ccosl", "ceil", "cexp",
                    "cexpf", "cexpl", "cimag", "cimagf", "cimagl", "clog",
                    "clogf", "clogl", "conj", "conjf", "conjl", "copysign",
                    "copysignf", "copysignl", "cos", "cosh", "cpow", "cpowf",
                    "cpowl", "cproj", "cprojf", "cprojl", "creal", "crealf",
                    "creall", "csin", "csinf", "csinh", "csinhf", "csinhl",
                    "csinl", "csqrt", "csqrtf", "csqrtl", "ctan", "ctanf",
                    "ctanh", "ctanhf", "ctanhl", "ctanl", "erf", "erfc",
                    "erfcf", "erfcl", "erff", "erfl", "exp2", "exp2f", "exp2l"
                    , "expm1", "expm1f", "expm1l", "fdim", "fdimf", "fdiml",
                    "fma", "fmaf", "fmal", "fmax", "fmaxf", "fmaxl", "fmin",
                    "fminf", "fminl", "hypot", "hypotf", "hypotl", "ilogb",
                    "ilogbf", "ilogbl", "imaxabs", "isblank", "iswblank",
                    "lgamma", "lgammaf", "lgammal", "llabs", "llrint",
                    "llrintf", "llrintl", "llround", "llroundf", "llroundl",
                    "log1p", "log1pf", "log1pl", "log2", "log2f", "log2l",
                    "logb", "logbf", "logbl", "lrint", "lrintf", "lrintl",
                    "lround", "lroundf", "lroundl", "nearbyint", "nearbyintf"
                    , "nearbyintl", "nextafter", "nextafterf", "nextafterl",
                    "nexttoward", "nexttowardf", "nexttowardl", "remainder",
                    "remainderf", "remainderl", "remquo", "remquof", "remquol"
                    , "rint", "rintf", "rintl", "round", "roundf", "roundl",
                    "scalbln", "scalblnf", "scalblnl", "scalbn", "scalbnf",
                    "scalbnl", "sin", "sinh", "snprintf", "sprintf", "sqrt",
                    "sscanf", "strcat", "strchr", "strcmp", "strcpy",
                    "strcspn", "strlen", "strncat", "strncmp", "strncpy",
                    "strpbrk", "strrchr", "strspn", "strstr", "tan", "tanh",
                    "tgamma", "tgammaf", "tgammal", "tolower", "toupper",
                    "trunc", "truncf", "truncl", "vfprintf", "vfscanf",
                    "vprintf", "vscanf", "vsnprintf", "vsprintf", "vsscanf");

    private static final Logger LOGGER = Logger.getLogger(Mcc.class.getName());

    public static final HashMap<String, SymbolTableEntry> SYMBOL_TABLE =
            new DebugHashMap<>();
    public static final HashMap<String, StructDef> TYPE_TABLE =
            new HashMap<>() {
                public StructDef put(String key, StructDef value) {
                    return super.put(key, value);
                }
            };
    public static final HashMap<String, EnumSpecifier> ENUM_MAP =
            new HashMap<>();

    public static final AtomicLong TEMP_COUNT = new AtomicLong(0L);

    /**
     * This is sometimes useful in testing
     */
    public static boolean registerAllocatorDisabled = false;

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
                yield size < 16L &&
                        element.isScalar() ? (int) Mcc.size(element) : 16;
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
            case Aligned(Type t, Exp alignment) -> (int)SemanticAnalysis.evaluateExpAsConstant(alignment).toLong();
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
            case Aligned(Type _, Exp alignment) -> (int)SemanticAnalysis.evaluateExpAsConstant(alignment).toLong();
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
            case WidthRestricted(Type element, int _) -> size(element);
            case Aligned(Type inner, Exp _) -> size(inner);
        };
    }

    public static Type type(ValIr val) {
        return switch(val){
            case null -> VOID;
            case Constant constant -> constant.type();
            case VarIr v -> Mcc.SYMBOL_TABLE.get(v.identifier()).type();
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

    public static FunType funType(VarIr v) {
        Type t = type(v);
        if (t instanceof FunType ft) return ft;
        if (t instanceof Pointer(FunType ft)) return ft;
        if (t instanceof Array(Type p, Constant _) && p instanceof Pointer(FunType ft)) return ft;
        throw new IllegalArgumentException("Required function or pointer to function. Found "+t);
    }


    enum Mode {LEX, PARSE, VALIDATE, CODEGEN, COMPILE, TACKY, ASSEMBLE, DUMMY}



    public static int preprocess(Path cFile,
                                 Path iFile, List<String> includePaths) throws IOException,
            InterruptedException {
        ArrayList<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("gcc", "-E", "-std=c23", "-D_POSIX_C_SOURCE=200809",
                cFile.toString(), "-o", iFile.toString()));
        args.addAll(includePaths);
        return startProcess(args.toArray(new String[0]));
    }

    public static int startProcess(String... args) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(args).inheritIO();
        return pb.start().waitFor();
    }

    public static int assembleAndLink(Path asmFile,
                                      boolean doNotCompile,
                                      List<String> libs,
                                      String outputFileName) throws InterruptedException, IOException {
        List<String> gccArgs = new ArrayList<>(Arrays.asList("gcc",
                asmFile.toString()));
        if (doNotCompile) {
            gccArgs.add("-c");
        }
        gccArgs.addAll(Arrays.asList("-o",
                outputFileName));
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
        Codegen.clear();
        ArrayList<String> args =
                Arrays.stream(args0).collect(Collectors.toCollection(ArrayList::new));

        Mode mode = Mode.ASSEMBLE;
        boolean doNotCompile = false;
        List<String> libs = new ArrayList<>();
        List<String> includePaths = new ArrayList<>();
        EnumSet<Optimization> optimizations =
                EnumSet.noneOf(Optimization.class);
        String outputFileName = null;
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
                case "-o" -> {
                    outputFileName = removeArg(args, i + 1, "missing filename after -o");
                    yield Mode.DUMMY;
                }
                default -> {
                    if (arg.startsWith("-l")) {
                        libs.addFirst(arg);
                        yield Mode.DUMMY;
                    }
                    if (arg.startsWith("-I")) {
                        includePaths.addFirst(arg);
                        yield Mode.DUMMY;
                    }
                    if (arg.equals("--no-register-allocator")) {
                        registerAllocatorDisabled = true;
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

        int preprocessExitCode = preprocess(srcFile, intermediateFile, includePaths);
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
                """, Mode.VALIDATE, EnumSet.noneOf(Optimization.class), null, true, Collections.emptyList(), identifierMap, structureMap, builtinDeclarations, null,null);
        BUILTIN_VA_LIST =
                (Array) Mcc.SYMBOL_TABLE.get("__builtin_va_list").type();
        ArrayList<Declaration> declarations = new ArrayList<>();
        //Path asmFile = srcFile.resolveSibling(bareFileName + ".s");
        if (outputFileName == null) {
            outputFileName = srcFile.resolveSibling(
                    bareFileName + (doNotCompile ? ".o" : "")).toString();
            if (mode == Mode.COMPILE) outputFileName = outputFileName + ".s";
        }

        return mccHelper(cSource, mode, optimizations, srcFile, doNotCompile, libs, identifierMap, structureMap, declarations, builtinDeclarations, outputFileName);
    }

    private static String removeArg(ArrayList<String> args, int argIndex, String error) {
        if (argIndex < args.size()) {
            return args.remove(argIndex);
        }
        System.err.println(error);
        System.exit(-1);
        return null;
    }

    public static Type decayArrayType(Type t) {
        return t instanceof Array(Type r, Constant _) ? new Pointer(r) : t;
    }

    public static Array BUILTIN_VA_LIST = null;
    private static int mccHelper(String cSource, Mode mode,
                                 EnumSet<Optimization> optimizations,
                                 Path srcFile,
                                 boolean doNotCompile,
                                 List<String> libs,
                                 Map<String, SemanticAnalysis.Entry> identifierMap,
                                 Map<String, SemanticAnalysis.TagEntry> structureMap,
                                 ArrayList<Declaration> declarations,
                                 ArrayList<Declaration> builtinDeclarations,
    String outputFileName) throws IOException, InterruptedException {
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
        Path asmFile = mode == Mode.COMPILE ? Paths.get(outputFileName) : Paths.get(outputFileName+".s");
        try (PrintWriter pw =
                     new PrintWriter(Files.newBufferedWriter(asmFile))) {
            programAsm.emitAsm(pw);
            pw.flush();
        }

        if (mode == Mode.COMPILE) {
            return 0;
        }

        int exitCode = assembleAndLink(asmFile, doNotCompile, libs, outputFileName);
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
        if (tokens != null) {
            return new Err(s + " " + tokens.positionString());
        }
        return new Err(s);
    }

}
