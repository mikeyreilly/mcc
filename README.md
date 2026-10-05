# Overview

`mcc` is a C compiler written in Java with x86-64 code generation,
optimization passes, and debug information support. It owes its
existence to Nora Sandler's [Writing a C
Compiler](https://nostarch.com/writing-c-compiler). I followed that
book and ended up with much of a C compiler. Then I kept going.
It's not complete—I haven't got round to atomic types or BitInts yet—but
it can compile SQLite, and the resulting executable runs.

Alongside the usual frontend and code generation pieces, it includes features
that are often skipped in small compiler projects:

-   register allocation
-   optimization passes, including constant folding, copy propagation,
    unreachable-code elimination, and dead-store elimination
-   debug information, with DWARF on Linux/GNU and CodeView/PDB support on
    Windows
-   ABI-aware code generation for Linux and Windows x86-64 calls

Supported output targets are:

-   `linux-gnu`
-   `windows-msvc`

By default, the compiler targets the host operating system.

Since May 2026, I have been learning [Sea of
Nodes](https://github.com/SeaOfNodes) so that I can greatly improve
the compiler's optimizations and make it compile faster.

Right now, this compiler is pretty janky. It requires an external assembler and
it calls another compiler to do the preprocessing and linking.

# Requirements

-   JDK 25 or newer
-   Maven 3.x
-   A C toolchain on `PATH`

For Linux, `mcc` invokes `gcc` for preprocessing, assembly, and linking.

For Windows, `mcc` uses Clang/LLVM: `clang-cl` for preprocessing and `clang`
plus `lld` for assembly and linking. Linking still requires the Windows SDK and
runtime libraries to be visible to Clang, such as from a Visual Studio Developer
Command Prompt or an equivalent LLVM setup.

# Getting Started

Compile the Java sources:

    mvn -q compile

The launcher scripts run the main class from `target/classes`, so run the build
step before using them.

On Unix-like systems:

    ./mcc -o factors factors.c
    ./factors 42

On Windows:

    mcc.bat -o factors.exe factors.c
    factors.exe 42

You can also run the compiler directly:

    java -Xss10M -Djava.util.logging.config.file=logging.properties \
      -cp target/classes com.quaxt.mcc.Mcc factors.c

# Usage

    mcc [options] <source.c>

The compiler accepts one C source file. If no output file is specified, it writes
the result next to the input file:

-   executable: `source` on Linux, `source.exe` on Windows
-   object file with `-c`: `source.o` on Linux, `source.obj` on Windows
-   assembly with `-S`: `source.s`

Common options:

<table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">


<colgroup>
<col  class="org-left" />

<col  class="org-left" />
</colgroup>
<thead>
<tr>
<th scope="col" class="org-left">Option</th>
<th scope="col" class="org-left">Meaning</th>
</tr>
</thead>
<tbody>
<tr>
<td class="org-left"><code>-o &lt;file&gt;</code></td>
<td class="org-left">Set the output path</td>
</tr>

<tr>
<td class="org-left"><code>-S</code> or <code>-s</code></td>
<td class="org-left">Emit assembly only</td>
</tr>

<tr>
<td class="org-left"><code>-c</code></td>
<td class="org-left">Compile to an object file, but do not link</td>
</tr>

<tr>
<td class="org-left"><code>-g</code></td>
<td class="org-left">Emit debug information</td>
</tr>

<tr>
<td class="org-left"><code>-I&lt;dir&gt;</code></td>
<td class="org-left">Add an include directory for preprocessing</td>
</tr>

<tr>
<td class="org-left"><code>-l&lt;name&gt;</code></td>
<td class="org-left">Pass a library argument to the linker</td>
</tr>

<tr>
<td class="org-left"><code>--target=linux-gnu</code></td>
<td class="org-left">Emit Linux/GNU ABI output</td>
</tr>

<tr>
<td class="org-left"><code>--target=windows-msvc</code></td>
<td class="org-left">Emit Windows/MSVC ABI output</td>
</tr>
</tbody>
</table>

Optimization options:

<table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">


<colgroup>
<col  class="org-left" />

<col  class="org-left" />
</colgroup>
<thead>
<tr>
<th scope="col" class="org-left">Option</th>
<th scope="col" class="org-left">Meaning</th>
</tr>
</thead>
<tbody>
<tr>
<td class="org-left"><code>--optimize</code></td>
<td class="org-left">Enable all implemented optimizations</td>
</tr>

<tr>
<td class="org-left"><code>--fold-constants</code></td>
<td class="org-left">Fold constant expressions</td>
</tr>

<tr>
<td class="org-left"><code>--propagate-copies</code></td>
<td class="org-left">Propagate copy values</td>
</tr>

<tr>
<td class="org-left"><code>--eliminate-unreachable-code</code></td>
<td class="org-left">Remove unreachable code</td>
</tr>

<tr>
<td class="org-left"><code>--eliminate-dead-stores</code></td>
<td class="org-left">Remove dead stores</td>
</tr>
</tbody>
</table>

Debugging and development options:

<table border="2" cellspacing="0" cellpadding="6" rules="groups" frame="hsides">


<colgroup>
<col  class="org-left" />

<col  class="org-left" />
</colgroup>
<thead>
<tr>
<th scope="col" class="org-left">Option</th>
<th scope="col" class="org-left">Meaning</th>
</tr>
</thead>
<tbody>
<tr>
<td class="org-left"><code>--lex</code></td>
<td class="org-left">Stop after lexing</td>
</tr>

<tr>
<td class="org-left"><code>--parse</code></td>
<td class="org-left">Stop after parsing</td>
</tr>

<tr>
<td class="org-left"><code>--validate</code></td>
<td class="org-left">Stop after semantic validation</td>
</tr>

<tr>
<td class="org-left"><code>--tacky</code></td>
<td class="org-left">Stop after IR generation. Tacky is the name used for the IR in <em>Writing a C Compiler</em>.</td>
</tr>

<tr>
<td class="org-left"><code>--codegen</code></td>
<td class="org-left">Stop after assembly generation in memory</td>
</tr>

<tr>
<td class="org-left"><code>--no-register-allocator</code></td>
<td class="org-left">Disable the register allocator</td>
</tr>
</tbody>
</table>



# Examples

Build an executable with optimizations:

    ./mcc --optimize -o factors factors.c

Compile to assembly:

    ./mcc -S -o factors.s factors.c

Compile to an object file:

    ./mcc -c -o factors.o factors.c

Compile a file that uses an extra include directory:

    ./mcc -Iinclude -o program program.c

Select a supported target explicitly:

    ./mcc --target=linux-gnu -S -o program.s program.c



# Tests

Run the test suite with Maven:

    mvn test

Many tests compile C files from `src/test/resources` and then run the resulting
programs. The same external toolchain requirements apply to tests.

