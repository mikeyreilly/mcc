	.file   "factors.c"
	.text
.Ltext0:
	.file 0 "/home/mreilly/wa/mcc" "factors.c"
	.section        .rodata
.LC0:
	.string         "didn't get number"
.LC1:
	.string         "%lu\n"
.LC2:
	.string         "prime"
	.text
	.globl  main
	.type   main, @function
main:
.LFB0:
	.file 1 "factors.c"
	.loc 1 4 33
	.cfi_startproc
	pushq   %rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
	movq    %rsp, %rbp
	.cfi_def_cfa_register 6
	subq    $48, %rsp
	movl    %edi, -36(%rbp)
	movq    %rsi, -48(%rbp)
	.loc 1 6 32
	movq    -48(%rbp), %rax
	addq    $8, %rax
	.loc 1 6 20
	movq    (%rax), %rax
	leaq    -32(%rbp), %rcx
	movl    $10, %edx
	movq    %rcx, %rsi
	movq    %rax, %rdi
	call    strtoul
	movq    %rax, -24(%rbp)
	.loc 1 7 20
	movq    -48(%rbp), %rax
	addq    $8, %rax
	movq    (%rax), %rdx
	.loc 1 7 13
	movq    -32(%rbp), %rax
	.loc 1 7 5
	cmpq    %rax, %rdx
	jne     .L2
	.loc 1 8 3
	movl    $.LC0, %edi
	call    puts
	.loc 1 9 10
	movl    $-1, %eax
	jmp     .L3
.L2:
	.loc 1 11 7
	movb    $1, -1(%rbp)
.LBB2:
	.loc 1 12 21
	movq    $2, -16(%rbp)
	.loc 1 12 2
	jmp     .L4
.L6:
	.loc 1 13 9
	movq    -24(%rbp), %rax
	movl    $0, %edx
	divq    -16(%rbp)
	movq    %rdx, %rax
	.loc 1 13 6
	testq   %rax, %rax
	jne     .L5
	.loc 1 14 4
	movq    -16(%rbp), %rax
	movq    %rax, %rsi
	movl    $.LC1, %edi
	movl    $0, %eax
	call    printf
	.loc 1 15 10
	movb    $0, -1(%rbp)
.L5:
	.loc 1 12 36 discriminator 2
	addq    $1, -16(%rbp)
.L4:
	.loc 1 12 30 discriminator 1
	movq    -16(%rbp), %rax
	cmpq    -24(%rbp), %rax
	jb      .L6
.LBE2:
	.loc 1 18 5
	cmpb    $0, -1(%rbp)
	je      .L7
	.loc 1 19 3
	movl    $.LC2, %edi
	call    puts
.L7:
	movl    $0, %eax
.L3:
	.loc 1 21 1
	leave
	.cfi_def_cfa 7, 8
	ret
	.cfi_endproc
.LFE0:
	.size   main, .-main
.Letext0:
	.section        .debug_info,"",@progbits

.Ldebug_info0:                                  #   Compilation Unit @ offset 0x0:
	.long   0x122                           #    Length:        0x122 (32-bit)
	.value  0x5                             #    Version:       5
	.byte   0x1                             #    Unit Type:     DW_UT_compile (1)
	.byte   0x8                             #    Pointer Size:  8
	.long   .Ldebug_abbrev0
	.uleb128 0x9                            #  <0><c>: Abbrev Number: 9 (DW_TAG_compile_unit)
	.long   .LASF11                         #     <d>   DW_AT_producer    : (indirect string, offset: 0x37): GNU C23 15.2.0 -mtune=generic -march=x86-64 -g
        .byte   0x1d                            #     <11>   DW_AT_language    : 29     (C11)
	.byte   0x3                             #     <12>   Unknown AT value: 90: 3
	.long   0x31647                         #     <13>   Unknown AT value: 91: 0x31647
	.long   .LASF0                          #     <17>   DW_AT_name        : (indirect line string, offset: 0x15): factors.c
	.long   .LASF1                          #     <1b>   DW_AT_comp_dir    : (indirect line string, offset: 0x0): /home/mreilly/wa/mcc
	.quad   .Ltext0                         #     <1f>   DW_AT_low_pc      : 0x401146
	.quad   .Letext0-.Ltext0                #     <27>   DW_AT_high_pc     : 0xba
	.long   .Ldebug_line0
	.uleb128 0x4                            #  <1><33>: Abbrev Number: 4 (DW_TAG_subprogram)
	.long   .LASF3                          #     <34>   DW_AT_external    : 1
	.byte   0x1                             #     <34>   DW_AT_name        : (indirect string, offset: 0x6b): printf
	.byte   0xc                             #     <38>   DW_AT_decl_file   : 1
	.long   0x49                            #     <38>   DW_AT_decl_line   : 1
	.long   0x49                            #     <39>   DW_AT_decl_column : 12
						#     <3a>   DW_AT_prototyped  : 1
						#     <3a>   DW_AT_type        : <0x49>
						#     <3e>   DW_AT_declaration : 1
						#     <3e>   DW_AT_sibling     : <0x49>
	.uleb128 0x1                            #  <2><42>: Abbrev Number: 1 (DW_TAG_formal_parameter)
	.long   0x50                            #     <43>   DW_AT_type        : <0x50>
	.uleb128 0xa                            #  <2><47>: Abbrev Number: 10 (DW_TAG_unspecified_parameters)
	.byte   0                               #  <2><48>: Abbrev Number: 0
	.uleb128 0xb                            #  <1><49>: Abbrev Number: 11 (DW_TAG_base_type)
	.byte   0x4                             #     <4a>   DW_AT_byte_size   : 4
	.byte   0x5                             #     <4b>   DW_AT_encoding    : 5      (signed)
	.string         "int"                   #     <4c>   DW_AT_name        : int
	.uleb128 0x2                            #  <1><50>: Abbrev Number: 2 (DW_TAG_pointer_type)
	.long   0x61                            #     <51>   DW_AT_byte_size   : 8
						#     <51>   DW_AT_type        : <0x61>
	.uleb128 0x5                            #  <1><55>: Abbrev Number: 5 (DW_TAG_restrict_type)
	.long   0x50                            #     <56>   DW_AT_type        : <0x50>
	.uleb128 0x3                            #  <1><5a>: Abbrev Number: 3 (DW_TAG_base_type)
	.byte   0x1                             #     <5b>   DW_AT_byte_size   : 1
	.byte   0x6                             #     <5c>   DW_AT_encoding    : 6      (signed char)
	.long   .LASF2                          #     <5d>   DW_AT_name        : (indirect string, offset: 0x66): char
	.uleb128 0xc                            #  <1><61>: Abbrev Number: 12 (DW_TAG_const_type)
	.long   0x5a                            #     <62>   DW_AT_type        : <0x5a>
	.uleb128 0x4                            #  <1><66>: Abbrev Number: 4 (DW_TAG_subprogram)
	.long   .LASF4                          #     <67>   DW_AT_external    : 1
	.byte   0x2                             #     <67>   DW_AT_name        : (indirect string, offset: 0x0): strtoul
	.byte   0x1a                            #     <6b>   DW_AT_decl_file   : 1
	.long   0x85                            #     <6b>   DW_AT_decl_line   : 2
	.long   0x85                            #     <6c>   DW_AT_decl_column : 26
						#     <6d>   DW_AT_prototyped  : 1
						#     <6d>   DW_AT_type        : <0x85>
						#     <71>   DW_AT_declaration : 1
						#     <71>   DW_AT_sibling     : <0x85>
	.uleb128 0x1                            #  <2><75>: Abbrev Number: 1 (DW_TAG_formal_parameter)
	.long   0x55                            #     <76>   DW_AT_type        : <0x55>
	.uleb128 0x1                            #  <2><7a>: Abbrev Number: 1 (DW_TAG_formal_parameter)
	.long   0x91                            #     <7b>   DW_AT_type        : <0x91>
	.uleb128 0x1                            #  <2><7f>: Abbrev Number: 1 (DW_TAG_formal_parameter)
	.long   0x49                            #     <80>   DW_AT_type        : <0x49>
	.byte   0                               #  <2><84>: Abbrev Number: 0
	.uleb128 0x3                            #  <1><85>: Abbrev Number: 3 (DW_TAG_base_type)
	.byte   0x8                             #     <86>   DW_AT_byte_size   : 8
	.byte   0x7                             #     <87>   DW_AT_encoding    : 7      (unsigned)
	.long   .LASF5                          #     <88>   DW_AT_name        : (indirect string, offset: 0x8): long unsigned int
	.uleb128 0x2                            #  <1><8c>: Abbrev Number: 2 (DW_TAG_pointer_type)
	.long   0x96                            #     <8d>   DW_AT_byte_size   : 8
						#     <8d>   DW_AT_type        : <0x96>
	.uleb128 0x5                            #  <1><91>: Abbrev Number: 5 (DW_TAG_restrict_type)
	.long   0x8c                            #     <92>   DW_AT_type        : <0x8c>
	.uleb128 0x2                            #  <1><96>: Abbrev Number: 2 (DW_TAG_pointer_type)
	.long   0x5a                            #     <97>   DW_AT_byte_size   : 8
						#     <97>   DW_AT_type        : <0x5a>
	.uleb128 0xd                            #  <1><9b>: Abbrev Number: 13 (DW_TAG_subprogram)
	.long   .LASF12                         #     <9c>   DW_AT_external    : 1
	.byte   0x1                             #     <9c>   DW_AT_name        : (indirect string, offset: 0x32): main
	.byte   0x4                             #     <a0>   DW_AT_decl_file   : 1
	.byte   0x5                             #     <a1>   DW_AT_decl_line   : 4
	.long   0x49                            #     <a2>   DW_AT_decl_column : 5
	.quad   .LFB0                           #     <a3>   DW_AT_prototyped  : 1
	.quad   .LFE0-.LFB0                     #     <a3>   DW_AT_type        : <0x49>
	.uleb128 0x1                            #     <a7>   DW_AT_low_pc      : 0x401146
	.byte   0x9c                            #     <af>   DW_AT_high_pc     : 0xba
	.long   0x11e                           #     <b7>   DW_AT_frame_base  : 1 byte block: 9c       (DW_OP_call_frame_cfa)
						#     <b9>   DW_AT_call_all_tail_calls: 1
						#     <b9>   DW_AT_sibling     : <0x11e>
	.uleb128 0x6                            #  <2><bd>: Abbrev Number: 6 (DW_TAG_formal_parameter)
	.long   .LASF6                          #     <be>   DW_AT_name        : (indirect string, offset: 0x1a): argc
	.byte   0xe                             #     <c2>   DW_AT_decl_file   : 1
	.long   0x49                            #     <c2>   DW_AT_decl_line   : 4
	.uleb128 0x2                            #     <c2>   DW_AT_decl_column : 14
	.byte   0x91                            #     <c3>   DW_AT_type        : <0x49>
	.sleb128 -52                            #     <c7>   DW_AT_location    : 2 byte block: 91 4c    (DW_OP_fbreg: -52)
	.uleb128 0x6                            #  <2><ca>: Abbrev Number: 6 (DW_TAG_formal_parameter)
	.long   .LASF7                          #     <cb>   DW_AT_name        : (indirect string, offset: 0x72): argv
	.byte   0x1b                            #     <cf>   DW_AT_decl_file   : 1
	.long   0x8c                            #     <cf>   DW_AT_decl_line   : 4
	.uleb128 0x2                            #     <cf>   DW_AT_decl_column : 27
	.byte   0x91                            #     <d0>   DW_AT_type        : <0x8c>
	.sleb128 -64                            #     <d4>   DW_AT_location    : 2 byte block: 91 40    (DW_OP_fbreg: -64)
	.uleb128 0x7                            #  <2><d7>: Abbrev Number: 7 (DW_TAG_variable)
	.long   .LASF8                          #     <d8>   DW_AT_name        : (indirect string, offset: 0x2b): endptr
	.byte   0x5                             #     <dc>   DW_AT_decl_file   : 1
	.byte   0x8                             #     <dc>   DW_AT_decl_line   : 5
	.long   0x96                            #     <dd>   DW_AT_decl_column : 8
	.uleb128 0x2                            #     <de>   DW_AT_type        : <0x96>
	.byte   0x91                            #     <e2>   DW_AT_location    : 2 byte block: 91 50    (DW_OP_fbreg: -48)
	.sleb128 -48
	.uleb128 0x8                            #  <2><e5>: Abbrev Number: 8 (DW_TAG_variable)
	.string         "l"                     #     <e6>   DW_AT_name        : l
	.byte   0x6                             #     <e8>   DW_AT_decl_file   : 1
	.byte   0x10                            #     <e8>   DW_AT_decl_line   : 6
	.long   0x85                            #     <e9>   DW_AT_decl_column : 16
	.uleb128 0x2                            #     <ea>   DW_AT_type        : <0x85>
	.byte   0x91                            #     <ee>   DW_AT_location    : 2 byte block: 91 58    (DW_OP_fbreg: -40)
	.sleb128 -40
	.uleb128 0x7                            #  <2><f1>: Abbrev Number: 7 (DW_TAG_variable)
	.long   .LASF9                          #     <f2>   DW_AT_name        : (indirect string, offset: 0x1f): prime
	.byte   0xb                             #     <f6>   DW_AT_decl_file   : 1
	.byte   0x7                             #     <f6>   DW_AT_decl_line   : 11
	.long   0x11e                           #     <f7>   DW_AT_decl_column : 7
	.uleb128 0x2                            #     <f8>   DW_AT_type        : <0x11e>
	.byte   0x91                            #     <fc>   DW_AT_location    : 2 byte block: 91 6f    (DW_OP_fbreg: -17)
	.sleb128 -17
	.uleb128 0xe                            #  <2><ff>: Abbrev Number: 14 (DW_TAG_lexical_block)
	.quad   .LBB2                           #     <100>   DW_AT_low_pc      : 0x4011a1
	.quad   .LBE2-.LBB2                     #     <108>   DW_AT_high_pc     : 0x48
	.uleb128 0x8                            #  <3><110>: Abbrev Number: 8 (DW_TAG_variable)
	.string         "d"                     #     <111>   DW_AT_name        : d
	.byte   0xc                             #     <113>   DW_AT_decl_file   : 1
	.byte   0x15                            #     <113>   DW_AT_decl_line   : 12
	.long   0x85                            #     <114>   DW_AT_decl_column : 21
	.uleb128 0x2                            #     <115>   DW_AT_type        : <0x85>
	.byte   0x91                            #     <119>   DW_AT_location    : 2 byte block: 91 60   (DW_OP_fbreg: -32)
	.sleb128 -32                            #  <3><11c>: Abbrev Number: 0
	.byte   0                               #  <2><11d>: Abbrev Number: 0
	.byte   0
	.uleb128 0x3                            #  <1><11e>: Abbrev Number: 3 (DW_TAG_base_type)
	.byte   0x1                             #     <11f>   DW_AT_byte_size   : 1
	.byte   0x2                             #     <120>   DW_AT_encoding    : 2     (boolean)
	.long   .LASF10                         #     <121>   DW_AT_name        : (indirect string, offset: 0x25): _Bool
	.byte   0                               #  <1><125>: Abbrev Number: 0

.section        .debug_abbrev,"",@progbits
.Ldebug_abbrev0:
	.uleb128 0x1     #   1      DW_TAG_formal_parameter    [no children]
	.uleb128 0x5
	.byte   0
	.uleb128 0x49    #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x13    #    DW_AT value: 0     DW_FORM value: 0
	.byte   0
	.byte   0
	.uleb128 0x2     #   2      DW_TAG_pointer_type    [no children]
	.uleb128 0xf
	.byte   0
	.uleb128 0xb     #    DW_AT_byte_size    DW_FORM_implicit_const: 8
	.uleb128 0x21    #    DW_AT_type         DW_FORM_ref4
	.sleb128 8       #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x49
	.uleb128 0x13
	.byte   0
	.byte   0
	.uleb128 0x3     #   3      DW_TAG_base_type    [no children]
	.uleb128 0x24
	.byte   0
	.uleb128 0xb     #    DW_AT_byte_size    DW_FORM_data1
	.uleb128 0xb     #    DW_AT_encoding     DW_FORM_data1
	.uleb128 0x3e    #    DW_AT_name         DW_FORM_strp
	.uleb128 0xb     #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x3
	.uleb128 0xe
	.byte   0
	.byte   0
	.uleb128 0x4     #   4      DW_TAG_subprogram    [has children]
	.uleb128 0x2e
	.byte   0x1
	.uleb128 0x3f    #    DW_AT_external     DW_FORM_flag_present
	.uleb128 0x19    #    DW_AT_name         DW_FORM_strp
	.uleb128 0x3     #    DW_AT_decl_file    DW_FORM_implicit_const: 1
	.uleb128 0xe     #    DW_AT_decl_line    DW_FORM_data1
	.uleb128 0x3a    #    DW_AT_decl_column  DW_FORM_data1
	.uleb128 0x21    #    DW_AT_prototyped   DW_FORM_flag_present
	.sleb128 1       #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x3b    #    DW_AT_declaration  DW_FORM_flag_present
	.uleb128 0xb     #    DW_AT_sibling      DW_FORM_ref4
	.uleb128 0x39    #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0xb
	.uleb128 0x27
	.uleb128 0x19
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x3c
	.uleb128 0x19
	.uleb128 0x1
	.uleb128 0x13
	.byte   0
	.byte   0
	.uleb128 0x5     #   5      DW_TAG_restrict_type    [no children]
	.uleb128 0x37
	.byte   0
	.uleb128 0x49    #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x13    #    DW_AT value: 0     DW_FORM value: 0
	.byte   0
	.byte   0
	.uleb128 0x6     #   6      DW_TAG_formal_parameter    [no children]
	.uleb128 0x5
	.byte   0
	.uleb128 0x3     #    DW_AT_name         DW_FORM_strp
	.uleb128 0xe     #    DW_AT_decl_file    DW_FORM_implicit_const: 1
	.uleb128 0x3a    #    DW_AT_decl_line    DW_FORM_implicit_const: 4
	.uleb128 0x21    #    DW_AT_decl_column  DW_FORM_data1
	.sleb128 1       #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x3b    #    DW_AT_location     DW_FORM_exprloc
	.uleb128 0x21    #    DW_AT value: 0     DW_FORM value: 0
	.sleb128 4
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte   0
	.byte   0
	.uleb128 0x7     #   7      DW_TAG_variable    [no children]
	.uleb128 0x34
	.byte   0
	.uleb128 0x3     #    DW_AT_name         DW_FORM_strp
	.uleb128 0xe     #    DW_AT_decl_file    DW_FORM_implicit_const: 1
	.uleb128 0x3a    #    DW_AT_decl_line    DW_FORM_data1
	.uleb128 0x21    #    DW_AT_decl_column  DW_FORM_data1
	.sleb128 1       #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x3b    #    DW_AT_location     DW_FORM_exprloc
	.uleb128 0xb     #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte   0
	.byte   0
	.uleb128 0x8     #   8      DW_TAG_variable    [no children]
	.uleb128 0x34
	.byte   0
	.uleb128 0x3     #    DW_AT_name         DW_FORM_string
	.uleb128 0x8     #    DW_AT_decl_file    DW_FORM_implicit_const: 1
	.uleb128 0x3a    #    DW_AT_decl_line    DW_FORM_data1
	.uleb128 0x21    #    DW_AT_decl_column  DW_FORM_data1
	.sleb128 1       #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x3b    #    DW_AT_location     DW_FORM_exprloc
	.uleb128 0xb     #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte   0
	.byte   0
	.uleb128 0x9     #   9      DW_TAG_compile_unit    [has children]
	.uleb128 0x11
	.byte   0x1
	.uleb128 0x25    #    DW_AT_producer     DW_FORM_strp
	.uleb128 0xe     #    DW_AT_language     DW_FORM_data1
	.uleb128 0x13    #    Unknown AT value: 90 DW_FORM_data1
	.uleb128 0xb     #    Unknown AT value: 91 DW_FORM_data4
	.uleb128 0x90    #    DW_AT_name         DW_FORM_line_strp
	.uleb128 0xb     #    DW_AT_comp_dir     DW_FORM_line_strp
	.uleb128 0x91    #    DW_AT_low_pc       DW_FORM_addr
	.uleb128 0x6     #    DW_AT_high_pc      DW_FORM_data8
	.uleb128 0x3     #    DW_AT_stmt_list    DW_FORM_sec_offset
	.uleb128 0x1f    #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x1b
	.uleb128 0x1f
	.uleb128 0x11
	.uleb128 0x1
	.uleb128 0x12
	.uleb128 0x7
	.uleb128 0x10
	.uleb128 0x17
	.byte   0
	.byte   0
	.uleb128 0xa     #   10      DW_TAG_unspecified_parameters    [no children]
	.uleb128 0x18
	.byte   0
	.byte   0        #    DW_AT value: 0     DW_FORM value: 0
	.byte   0
	.uleb128 0xb     #   11      DW_TAG_base_type    [no children]
	.uleb128 0x24
	.byte   0
	.uleb128 0xb     #    DW_AT_byte_size    DW_FORM_data1
	.uleb128 0xb     #    DW_AT_encoding     DW_FORM_data1
	.uleb128 0x3e    #    DW_AT_name         DW_FORM_string
	.uleb128 0xb     #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x3
	.uleb128 0x8
	.byte   0
	.byte   0
	.uleb128 0xc     #   12      DW_TAG_const_type    [no children]
	.uleb128 0x26
	.byte   0
	.uleb128 0x49    #    DW_AT_type         DW_FORM_ref4
	.uleb128 0x13    #    DW_AT value: 0     DW_FORM value: 0
	.byte   0
	.byte   0
	.uleb128 0xd     #   13      DW_TAG_subprogram    [has children]
	.uleb128 0x2e
	.byte   0x1
	.uleb128 0x3f    #    DW_AT_external     DW_FORM_flag_present
	.uleb128 0x19    #    DW_AT_name         DW_FORM_strp
	.uleb128 0x3     #    DW_AT_decl_file    DW_FORM_data1
	.uleb128 0xe     #    DW_AT_decl_line    DW_FORM_data1
	.uleb128 0x3a    #    DW_AT_decl_column  DW_FORM_data1
	.uleb128 0xb     #    DW_AT_prototyped   DW_FORM_flag_present
	.uleb128 0x3b    #    DW_AT_type         DW_FORM_ref4
	.uleb128 0xb     #    DW_AT_low_pc       DW_FORM_addr
	.uleb128 0x39    #    DW_AT_high_pc      DW_FORM_data8
	.uleb128 0xb     #    DW_AT_frame_base   DW_FORM_exprloc
	.uleb128 0x27    #    DW_AT_call_all_tail_calls DW_FORM_flag_present
	.uleb128 0x19    #    DW_AT_sibling      DW_FORM_ref4
	.uleb128 0x49    #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x13
	.uleb128 0x11
	.uleb128 0x1
	.uleb128 0x12
	.uleb128 0x7
	.uleb128 0x40
	.uleb128 0x18
	.uleb128 0x7c
	.uleb128 0x19
	.uleb128 0x1
	.uleb128 0x13
	.byte   0
	.byte   0
	.uleb128 0xe     #   14      DW_TAG_lexical_block    [has children]
	.uleb128 0xb
	.byte   0x1
	.uleb128 0x11    #    DW_AT_low_pc       DW_FORM_addr
	.uleb128 0x1     #    DW_AT_high_pc      DW_FORM_data8
	.uleb128 0x12    #    DW_AT value: 0     DW_FORM value: 0
	.uleb128 0x7     #
	.byte   0
	.byte   0
	.byte   0
	.section        .debug_aranges,"",@progbits
	.long   0x2c
	.value  0x2
	.long   .Ldebug_info0
	.byte   0x8
	.byte   0
	.value  0
	.value  0
	.quad   .Ltext0
	.quad   .Letext0-.Ltext0
	.quad   0
	.quad   0
	.section        .debug_line,"",@progbits
.Ldebug_line0:
	.section        .debug_str,"MS",@progbits,1
.LASF4:
	.string         "strtoul"
.LASF5:
	.string         "long unsigned int"
.LASF6:
	.string         "argc"
.LASF9:
	.string         "prime"
.LASF10:
	.string         "_Bool"
.LASF8:
	.string         "endptr"
.LASF12:
	.string         "main"
.LASF11:
	.string         "GNU C23 15.2.0 -mtune=generic -march=x86-64 -g"
.LASF2:
	.string         "char"
.LASF3:
	.string         "printf"
.LASF7:
	.string         "argv"
	.section        .debug_line_str,"MS",@progbits,1
.LASF1:
	.string         "/home/mreilly/wa/mcc"
.LASF0:
	.string         "factors.c"
	.ident  "GCC: (GNU) 15.2.0"
	.section        .note.GNU-stack,"",@progbits
