package com.quaxt.mcc;

import com.quaxt.mcc.asm.Codegen;
import com.quaxt.mcc.asm.ProgramAsm;
import com.quaxt.mcc.optimizer.Optimizer;
import com.quaxt.mcc.parser.*;
import com.quaxt.mcc.semantic.*;
import com.quaxt.mcc.tacky.*;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.InvalidPathException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    public static Target target = Target.hostDefault();

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
            case Primitive p -> target.alignment(p);
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
            case FunType(List<Type> params, Type ret, boolean varargs,
                         Exp alignment) -> alignment ==
                    null ? 1 : (int) SemanticAnalysis.evaluateExpAsConstant(alignment).toLong();
            case Pointer _, NullptrT _ -> 8;
            case Primitive p -> target.alignment(p);
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
            case Primitive p -> target.size(p);

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
            case CHAR, SCHAR, INT, LONG, DOUBLE, FLOAT -> true;
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
        if (target.isWindowsMsvc()) {
            args.addAll(Arrays.asList("clang-cl", "/nologo", "/P", "/TC",
                    "/Fi" + iFile, "/I", msvcIncludePath().toString(),
                    "/D__ATOMIC_SEQ_CST=5", "/D_CRT_SECURE_NO_WARNINGS",
                    "/D_NO_CRT_STDIO_INLINE", "/DBITINT_MAXWIDTH=64",
                    cFile.toString()));
            for (String includePath : includePaths) {
                args.add(includePath.startsWith("-I") ? "/I" + includePath.substring(2) : includePath);
            }
        } else {
            args.addAll(Arrays.asList("gcc", "-E", "-std=c23", "-D_POSIX_C_SOURCE=200809",
                    cFile.toString(), "-o", iFile.toString()));
            args.addAll(includePaths);
        }
        return startProcess(args.toArray(new String[0]));
    }

    private static Path msvcIncludePath() throws IOException {
        URL resource = Mcc.class.getClassLoader().getResource("msvc-include");
        if (resource == null) {
            throw new FileNotFoundException("missing msvc-include resources");
        }
        try {
            return Path.of(resource.toURI()).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | URISyntaxException e) {
            throw new IOException("msvc-include resources are not available as files", e);
        }
    }

    public static int startProcess(String... args) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(args).inheritIO();
        return pb.start().waitFor();
    }

    public static int assembleAndLink(Path asmFile,
                                      boolean doNotCompile,
                                      List<String> libs,
                                      String outputFileName) throws InterruptedException, IOException {
        if (target.isWindowsMsvc()) {
            Path objectFile = doNotCompile ? Paths.get(outputFileName) :
                    Files.createTempFile("mcc-", ".obj");
            int assembleExit = startProcess("clang",
                    "--target=x86_64-pc-windows-msvc",
                    "-x", "assembler", "-c", asmFile.toString(),
                    "-o", objectFile.toString());
            if (assembleExit != 0 || doNotCompile) return assembleExit;
            ArrayList<String> linkArgs = new ArrayList<>(Arrays.asList("clang",
                    "--target=x86_64-pc-windows-msvc", "-fuse-ld=lld",
                    objectFile.toString(), "-o", outputFileName,
                    "-Wl,/subsystem:console",
                    "-Wl,/defaultlib:legacy_stdio_definitions"));
            if (addDebugInfo) {
                linkArgs.add("-Wl,/debug");
                linkArgs.add("-Wl,/pdb:" + pdbPath(outputFileName));
            }
            linkArgs.addAll(libs);
            int linkExit = startProcess(linkArgs.toArray(new String[0]));
            Files.deleteIfExists(objectFile);
            return linkExit;
        } else {
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
    }

    private static String pdbPath(String outputFileName) {
        Path outputPath = Paths.get(outputFileName);
        Path fileNamePath = outputPath.getFileName();
        String fileName = fileNamePath == null ? outputFileName :
                fileNamePath.toString();
        String pdbName = fileName.toLowerCase(Locale.ROOT).endsWith(".exe") ?
                fileName.substring(0, fileName.length() - ".exe".length()) +
                        ".pdb" :
                fileName + ".pdb";
        Path parent = outputPath.getParent();
        return parent == null ? pdbName : parent.resolve(pdbName).toString();
    }

    public static void main(String[] args) {
        try {
            System.exit(mcc(args));
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(-1);
        }
    }

    public static boolean addDebugInfo = false;

    public static int mcc(String... args0) throws Exception {
        LOGGER.info("started with args " + String.join(" ", args0));

        SYMBOL_TABLE.clear();
        TYPE_TABLE.clear();
        TEMP_COUNT.set(0L);
        ENUM_MAP.clear();
        Codegen.clear();
        target = Target.hostDefault();
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
                case "-g" -> {
                    addDebugInfo = true;
                    yield Mode.DUMMY;
                }
                case String s when s.startsWith("--target=") -> {
                    target = Target.parse(s.substring("--target=".length()));
                    yield Mode.DUMMY;
                }
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
        Path intermediateFile = Files.createTempFile("mcc-" + bareFileName,
                ".i");
        String cSource;
        try {
            int preprocessExitCode =
                    preprocess(srcFile, intermediateFile, includePaths);
            if (preprocessExitCode != 0) {
                return preprocessExitCode;
            }
            cSource = Files.readString(intermediateFile);
        } finally {
            Files.deleteIfExists(intermediateFile);
        }
        Scope identifierMap = new Scope();
        Map<String, SemanticAnalysis.TagEntry> structureMap = new DebugHashMap<>();
        ArrayList<Declaration> builtinDeclarations = new ArrayList<>();

        String vaListBuiltin = target.isWindowsMsvc() ? """
                typedef char *__builtin_va_list;
                void abort (void);
                static inline void __builtin_abort (void) {
                    abort();
                }
                static long _InterlockedExchangeAdd(long volatile *Addend, long Value) {
                    long old = *Addend;
                    *Addend = old + Value;
                    return old;
                }
                static long long _InterlockedExchangeAdd64(long long volatile *Addend, long long Value) {
                    long long old = *Addend;
                    *Addend = old + Value;
                    return old;
                }
                static long long _mul128(long long Multiplier, long long Multiplicand, long long *HighProduct) {
                    if (HighProduct) *HighProduct = 0;
                    return Multiplier * Multiplicand;
                }
                static unsigned long long _umul128(unsigned long long Multiplier, unsigned long long Multiplicand, unsigned long long *HighProduct) {
                    if (HighProduct) *HighProduct = 0;
                    return Multiplier * Multiplicand;
                }
                static unsigned long long __shiftright128(unsigned long long LowPart, unsigned long long HighPart, unsigned char Shift) {
                    if (Shift == 0) return LowPart;
                    if (Shift < 64) return (LowPart >> Shift) | (HighPart << (64 - Shift));
                    return HighPart >> (Shift - 64);
                }
                static void _ReadWriteBarrier(void) {
                }
                static void __stosb(unsigned char *Destination, unsigned char Value, unsigned long long Count) {
                    for (unsigned long long i = 0; i < Count; i = i + 1) Destination[i] = Value;
                }
                static unsigned char __readgsbyte(unsigned long Offset) {
                    return 0;
                }
                static unsigned short __readgsword(unsigned long Offset) {
                    return 0;
                }
                static unsigned long __readgsdword(unsigned long Offset) {
                    return 0;
                }
                static unsigned long long __readgsqword(unsigned long Offset) {
                    return 0;
                }
                static volatile void *RtlSetVolatileMemory(volatile void *Destination, int Fill, unsigned long long Length) {
                    volatile unsigned char *p = Destination;
                    for (unsigned long long i = 0; i < Length; i = i + 1) p[i] = (unsigned char)Fill;
                    return Destination;
                }
                static void *MapViewOfFileNuma2(void *FileMappingHandle, void *ProcessHandle, unsigned long long Offset, void *BaseAddress, unsigned long long ViewSize, unsigned long AllocationType, unsigned long PageProtection, unsigned long PreferredNode) {
                    return (void *)0;
                }
                static unsigned short *CharUpperW(unsigned short *lpsz) {
                    return lpsz;
                }
                """ : """
                struct __builtin_va_list_item {
                    unsigned int gp_offset;
                    unsigned int fp_offset;
                    void *overflow_arg_area;
                    void *reg_save_area;
                };

                typedef struct __builtin_va_list_item  __builtin_va_list[1];
                void abort (void);
                static inline void __builtin_abort (void) {
                    abort();
                }
                """;
        mccHelper(vaListBuiltin, Mode.VALIDATE, EnumSet.noneOf(Optimization.class), null, true, Collections.emptyList(), identifierMap, structureMap, builtinDeclarations, null,null);
        BUILTIN_VA_LIST =
                Mcc.SYMBOL_TABLE.get("__builtin_va_list").type();
        ArrayList<Declaration> declarations = new ArrayList<>();
        //Path asmFile = srcFile.resolveSibling(bareFileName + ".s");
        if (outputFileName == null) {
            outputFileName = srcFile.resolveSibling(
                    bareFileName + (doNotCompile ? target.objectSuffix() : target.executableSuffix())).toString();
            if (mode == Mode.COMPILE) outputFileName = srcFile.resolveSibling(bareFileName + target.assemblySuffix()).toString();
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

    public static Type BUILTIN_VA_LIST = null;
    private static int mccHelper(String cSource, Mode mode,
                                 EnumSet<Optimization> optimizations,
                                 Path srcFile,
                                 boolean doNotCompile,
                                 List<String> libs,
                                 Scope identifierMap,
                                 Map<String, SemanticAnalysis.TagEntry> structureMap,
                                 ArrayList<Declaration> declarations,
                                 ArrayList<Declaration> builtinDeclarations,
                                 String outputFileName) throws IOException, InterruptedException {
        TokenList tokenList = Lexer.lex(cSource);
        if (mode == Mode.LEX) {
            return 0;
        }
        Program program = Parser.parseProgram(tokenList, declarations);
        if (!tokenList.isEmpty()) {
            throw makeErr("Unexpected token " + tokenList.getFirst(), tokenList);
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
        Path asmFile = mode == Mode.COMPILE ? Paths.get(outputFileName) : Paths.get(outputFileName + target.assemblySuffix());
        try (PrintWriter pw =
                     new PrintWriter(Files.newBufferedWriter(asmFile))) {
            if (addDebugInfo && !target.isWindowsMsvc()){
                var filesNames = tokenList.fileNames;
                for (int i = 0; i < filesNames.size(); i++) {
                    String fileName = filesNames.get(i);
                    pw.println("\t.file " + (i+1) + " \"" + fileName + "\"");
                }
            } else if (addDebugInfo) {
                emitCodeViewSourceFiles(pw, tokenList.fileNames,
                        srcFile.toAbsolutePath());
            }
            programAsm.emitAsm(pw, srcFile.toAbsolutePath());
            pw.flush();
        }

        if (mode == Mode.COMPILE) {
            return 0;
        }

        int exitCode = assembleAndLink(asmFile, doNotCompile, libs, outputFileName);
        Files.delete(asmFile);
        return exitCode;
    }

    private static void emitCodeViewSourceFiles(PrintWriter out,
                                                List<String> fileNames,
                                                Path mainSourcePath) throws IOException {
        Path fallback = mainSourcePath.toAbsolutePath().normalize();
        for (int i = 0; i < fileNames.size(); i++) {
            Path debugPath = readableDebugFile(fileNames.get(i), fallback);
            out.println("\t.cv_file " + (i + 1) + " " +
                    assemblyStringLiteral(debugPath.toString()) + " \"" +
                    md5(debugPath) + "\" 1");
        }
    }

    private static Path readableDebugFile(String fileName, Path fallback) {
        Path candidate;
        try {
            candidate = Paths.get(fileName);
        } catch (InvalidPathException _) {
            return fallback;
        }
        if (!candidate.isAbsolute()) {
            candidate = Paths.get("").toAbsolutePath().resolve(candidate);
        }
        candidate = candidate.normalize();
        return Files.isRegularFile(candidate) && Files.isReadable(candidate) ?
                candidate : fallback;
    }

    private static String md5(Path file) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        byte[] buffer = new byte[8192];
        try (InputStream in = Files.newInputStream(file)) {
            for (int read; (read = in.read(buffer)) != -1; ) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] bytes = digest.digest();
        char[] hex = new char[bytes.length * 2];
        char[] digits = "0123456789ABCDEF".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xff;
            hex[i * 2] = digits[b >>> 4];
            hex[i * 2 + 1] = digits[b & 0xf];
        }
        return new String(hex);
    }

    private static String assemblyStringLiteral(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c == '\\') sb.append("\\\\");
            else if (c == '"') sb.append("\\\"");
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '\t') sb.append("\\t");
            else if (c < 32 || c > 126) {
                String octal = Integer.toString(c & 0xff, 8);
                sb.append("\\000", 0, 4 - octal.length());
                sb.append(octal);
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
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

    public static void addDebugPos(List<InstructionIr> instructions, int pos) {
        if (addDebugInfo){
            instructions.add(new Pos(pos));
        }
    }

    public static void printIndent(PrintWriter out, String s) {
        out.println("\t" + s);
    }


}
