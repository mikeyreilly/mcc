package com.quaxt.mcc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class MccTest {

    public static String startProcessAndCaptureOutput(String... args) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p=pb.start();
        pb.start().waitFor();
        byte[] bytes;
        try (InputStream is = p.getInputStream()) {
            bytes = is.readAllBytes();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    @Test
    void void_fn_pointer() throws Exception {
        outputs("void_fn_pointer", "hello\n");
    }

    @Test
    void function_pointer() throws Exception {
        returns("function_pointer", 38, true);
    }

    @Test
    void canReturn42() throws Exception {
        returns("return42", 42);
    }

    @Test
    void hello_world() throws Exception {
        outputs("hello_world", "hello world");
    }

    @Test
    void nullptr() throws Exception {
        outputs("nullptr", """
                p is a null pointer.
                q points to value: 42
                q is now a null pointer.
                """);
    }


    @Test
    void double_decl_with_struct() throws Exception {
        outputs("double_decl_with_struct", "hello world");
    }

    @Test
    void typedef() throws Exception {
        returns("typedef", 0);
    }

    @Test
    void typedef_callback() throws Exception {
        outputs("typedef_callback", "8\n15\n");
    }

    @Test
    void typedef_struct_no_tag() throws Exception {
        returns("typedef_struct_no_tag", 17);
    }
    @Test
    void typeof() throws Exception {
        returns("typeof", 8);
    }

    @Test
    void struct() throws Exception {
        returns("struct", 0);
    }

    @Test
    void anon_struct() throws Exception {
        returns("anon_struct", 46);
    }

    @Test
    void awkward_struct() throws Exception {
        returns("awkward_struct", 10);
    }

    @Test
    void switch_test() throws Exception {
        returns("switch", 8);
    }

    @Test
    void struct_inline_struct_member() throws Exception {
        returns("struct_inline_struct_member", 36);
    }

    @Test
    void cast() throws Exception {
        returns("cast", 0);
    }

    @Test
    void multipleInitDeclarators() throws Exception {
        returns("multiple_init_declarators", 0);
    }


    private static void returns(String testProgram,
                                int expectedExitCode) throws Exception {
        returns(testProgram, expectedExitCode, true);
    }


    private static void returns(String testProgram,
                                int expectedExitCode, boolean optimize) throws Exception {
        if (optimize) assertEquals(0, Mcc.mcc("src/test/resources/" + testProgram + ".c", "--optimize"));
        else assertEquals(0, Mcc.mcc("src/test/resources/" + testProgram + ".c"));
        assertEquals(expectedExitCode,
                Mcc.startProcess("src/test/resources/" + testProgram));
    }

    private static void outputs(String testProgram,
                                String expectedOutput,
                                boolean optimize) throws Exception {
        if (optimize) {
            assertEquals(0, Mcc.mcc(
                    "src/test/resources/" + testProgram + ".c", "--optimize"));
        } else {
            assertEquals(0, Mcc.mcc(
                    "src/test/resources/" + testProgram + ".c"));
        }
        assertEquals(expectedOutput, startProcessAndCaptureOutput(
                "src/test/resources/" + testProgram));
    }

    @Test
    void uses_func()  throws Exception {
        outputs("uses_func", "main\n");
    }

    private void outputs(String testProgram,
                         String expectedOutput) throws Exception {
        outputs(testProgram,expectedOutput,true);
    }

    @Test
    void varargs1()  throws Exception {
        returns("varargs1", 0);
    }

    @Test
    void varargs2()  throws Exception {
        returns("varargs2", 66);
    }

    @Test
    void varargs3()  throws Exception {
        returns("varargs3", 66);
    }
    @Test
    void varargs3_no_stack()  throws Exception {
        returns("varargs3_no_stack", 15);
    }

    @Test
    void varargs_int_no_stack()  throws Exception {
        returns("varargs_int_no_stack", 15);
    }


    @Test
    void varargs_struct()  throws Exception {
        returns("varargs_struct", 163);
    }

    @Test
    void short_test()  throws Exception {
        returns("short", 5);
    }


    @Test
    void const_literals_test()  throws Exception {
        returns("const_literals", 3);
    }


    @Test
    void float_test()  throws Exception {
        returns("float", 30);
    }

    @Test
    void enum_test()  throws Exception {
        returns("enum", 221);
    }

    @Test
    void extern_test()  throws Exception {
        outputs("extern", "a.b.c\n");
    }

    @Test
    void bitfield_test()  throws Exception {
        outputs("bitfield", """
                x=52
                y=38
                """);
    }

    @Test
    void bitfield_with_anon()  throws Exception {
        outputs("bitfield_with_anon", """
                x=1540
                y=1030
                """);
    }


    @Test
    void bytes_swap()  throws Exception {
        outputs("bytes_swap", """
                EFCDAB8967452301
                78563412
                3412
                """, false);
    }

    @Test
    void multiple_deref_function_pointer() throws Exception {
        returns("multiple_deref_function_pointer", 42, false);
    }

    @Test
    void extern2_test()  throws Exception {
        returns("extern2", 0);
    }
    @Test
    void subscript_then_cast_test()  throws Exception {
        returns("subscript_then_cast", 42);
    }

    @Test
    void conditional_fptr_and_null()  throws Exception {
        returns("conditional_fptr_and_null", 42);
    }

    @Test
    void array_element_to_pointer_argument_conversion()  throws Exception {
        returns("array_element_to_pointer_argument_conversion", 5);
    }

    @Test
    void compare_function_and_function_pointer() throws Exception {
        returns("compare_function_and_function_pointer", 1);
    }

    @Test
    void offsetof_test()  throws Exception {
        outputs("offsetof", """
                the first element is at offset 0
                the double is at offset 8
                """);
    }
    @Test
    void string_init()  throws Exception {
        outputs("string_init", "howdy");
    }
    @Test
    void sizeof_test()  throws Exception {
        returns("sizeof", 16);
    }
    @Test
    void struct_no_such_member_test()  {
        Err thrown = assertThrows(Err.class, () -> returns("struct_no_such_member", 0));
        assertEquals("Structure has no member with this name",
                thrown.getMessage());
    }
    @Test
    void offsetof_no_such_member_test()  {
        Err thrown = assertThrows(Err.class, () -> returns("offsetof_no_such_member", 0));
        assertEquals("Structure has no member with this name",
                thrown.getMessage());
    }

    @Test
    void nested_struct() throws Exception {
        outputs("nested_struct", """
                sizeof(struct Outer) = 8
                offsetof(struct Outer, inner) = 8
                Outer.i = 42
                  Inner[0]: j=0, s="string 0"
                  Inner[1]: j=10, s="string 1"
                  Inner[2]: j=20, s="string 2"
                """);
    }

    @Test
    void array_init() throws Exception {
        outputs("array_init","BB\n");
    }

    @Test
    void array_init2() throws Exception {
        outputs("array_init2","BBB\n");
    }

    @Test
    void funcall_cast_to_void() throws Exception {
        outputs("funcall_cast_to_void","hello\n");
    }


    @AfterAll
    static void tearDown() {
        try (var st = Files.walk(Paths.get("src/test/resources"))) {
            st.forEach((Path f) -> {
                if (Files.isRegularFile(f)) {
                    String name = f.toString();
                    if (!name.endsWith(".c") && !name.endsWith(".h")) {
                        try {
                            Files.delete(f);
                        } catch (IOException e) {
                            System.err.println("exception deleting: " + f);
                            e.printStackTrace();
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void array_size_expression() throws Exception {
        outputs("array_size_expression", "abcdefg\n");
    }

    @Test
    void atomic() throws Exception {
        outputs("atomic", "x = 27, y = 27\n", true);
    }

    @Test
    void bool() throws Exception {
        outputs("bool", """
                true=1
                false=0
                sizeof(bool)=1
                cast=1
                """);
    }

    @Test
    void add_overflow() throws Exception {
        outputs("add_overflow", """
                sum=3, overflow=0
                sum=-2147483648, overflow=1
                """);

    }

    @Test
    void sub_overflow() throws Exception {
        outputs("sub_overflow", """
                sum=1, overflow=0
                sum=2147483647, overflow=1
                """);

    }
    @Test
    void mul_overflow() throws Exception {
        outputs("mul_overflow", """
                product=42, overflow=0
                product=-2, overflow=1
                """);

    }


    @Test
    void clzll() throws Exception {
        returns("clzll", 63, false);
    }

    @Test
    void longlong() throws Exception{
        outputs("longlong", """
                long
                long long
                """);
    }

    @Test
    void generic() throws Exception{
        outputs("generic", """
                int
                long
                """);
    }

    @Test
    void bitwise_and_pointer_and_int() throws Exception{
        returns("bitwise_and_pointer_and_int", 0);
    }

    @Test
    void struct2() throws Exception{
        returns("struct2", 8);
    }

    @Test
    void init() throws Exception{
        returns("init", 32);
    }


    @Test
    void static_unsigned_char_pointer_init() throws Exception{
        returns("static_unsigned_char_pointer_init", 32);
    }
    @Test
    void cast_to_double() throws Exception{
        returns("cast_to_double", 0);
    }

    @Test
    void double_to_uint() throws Exception {
        outputs("double_to_uint", """
                long
                18446744073709551615
                1
                10000000000
                0
                0
                int
                4294967295
                1
                1410065408
                0
                0
                short
                65535
                1
                0
                0
                0
                char
                255
                1
                0
                0
                0
                """, false);
    }

    @Test
    void duplicate_goto_labels() throws Exception {
        returns("duplicate_goto_labels", 42, false);
    }

    @Test
    void shl() throws Exception {
        returns("shl", 64, false);
    }

    @Test
    void infinity_builtin() throws Exception {
        outputs("infinity_builtin", "INFINITY (float) printed with %f: inf\n" );
    }


    @Test
    void nan_builtin() throws Exception {
        outputs("nan_builtin", "NAN (double) printed with %f: nan\n");
    }

    private static String assemble(String testProgram,
                                   boolean optimize) throws Exception {
        String baseFileName = "src/test/resources/" + testProgram;
        if (optimize)
            assertEquals(0, Mcc.mcc(baseFileName + ".c", "-S", "--optimize"));
        else assertEquals(0, Mcc.mcc(baseFileName + ".c", "-S"));
        return Files.readString(Paths.get(baseFileName + ".s"));
    }


    @Test
    void inline() throws Exception {
        returns("inline", 36, false);
        Stream<String> lines = assemble("inline", false).lines();
        assertTrue(lines.filter(x -> x.contains("call\tsum")).findAny().isEmpty());
    }
}
