package com.quaxt.mcc;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MccTest {

    public static String startProcessAndCaptureOutput(String... args) throws InterruptedException, IOException {
        ProcessBuilder pb = new ProcessBuilder(args);
        pb.redirectErrorStream(true);
        Process p=pb.start();
        int ret=pb.start().waitFor();
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
        returns("function_pointer", 38);
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
        outputs("nullptr", "p is a null pointer.\n" +
                "q points to value: 42\n" + "q is now a null pointer.\n");
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
        assertEquals(0, Mcc.mcc("src/test/resources/" + testProgram + ".c", "--optimize"));
        assertEquals(expectedExitCode,
                Mcc.startProcess("src/test/resources/" + testProgram));
    }
    private static void outputs(String testProgram,
                                String expectedOutput) throws Exception {
        assertEquals(0, Mcc.mcc("src/test/resources/" + testProgram + ".c", "--optimize"));
        assertEquals(expectedOutput,
                startProcessAndCaptureOutput("src/test/resources/" + testProgram));
    }

    @Test
    void uses_func()  throws Exception {
        outputs("uses_func", "main\n");
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
        outputs("bitfield", "x=52\n" + "y=38\n");
    }

    @Test
    void bitfield_with_anon()  throws Exception {
        outputs("bitfield_with_anon", "x=1540\n" + "y=1030\n");
    }


    @Test
    void bytes_swap()  throws Exception {
        outputs("bytes_swap", "EFCDAB8967452301\n");
    }
    @Test
    void extern2_test()  throws Exception {
        returns("extern2", 0);
    }
    @Test
    void offsetof_test()  throws Exception {
        outputs("offsetof", "the first element is at offset 0\n" +
                "the double is at offset 8\n");
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
        Err thrown = assertThrows(Err.class, () -> {
            returns("struct_no_such_member", 0);
        });
        assertEquals("Structure has no member with this name",
                thrown.getMessage());
    }
    @Test
    void offsetof_no_such_member_test()  {
        Err thrown = assertThrows(Err.class, () -> {
            returns("offsetof_no_such_member", 0);
        });
        assertEquals("Structure has no member with this name",
                thrown.getMessage());
    }

    @Test
    void nested_struct() throws Exception {
        outputs("nested_struct",
                "sizeof(struct Outer) = 8\n" +
                        "offsetof(struct Outer, inner) = 8\n" +
                        "Outer.i = 42\n" + "  Inner[0]: j=0, s=\"string 0\"\n" +
                        "  Inner[1]: j=10, s=\"string 1\"\n" +
                        "  Inner[2]: j=20, s=\"string 2\"\n");
    }
    
    @Test
    void array_init() throws Exception {
        outputs("array_init","BB\n");
    }

    @Test
    void array_init2() throws Exception {
        outputs("array_init2","BBB\n");
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
}
