	.file	"factors.c"
	.text
.Ltext0:
	.file 0 "/home/mreilly/wa/mcc" "factors.c"
	.section	.rodata
.LC0:
	.string	"didn't get number"
.LC1:
	.string	"%lu\n"
.LC2:
	.string	"prime"
	.text
	.globl	main
	.type	main, @function
main:
.LFB0:
	.file 1 "factors.c"
	.loc 1 4 33
	.cfi_startproc
	pushq	%rbp
	.cfi_def_cfa_offset 16
	.cfi_offset 6, -16
	movq	%rsp, %rbp
	.cfi_def_cfa_register 6
	subq	$48, %rsp
	movl	%edi, -36(%rbp)
	movq	%rsi, -48(%rbp)
	.loc 1 6 32
	movq	-48(%rbp), %rax
	addq	$8, %rax
	.loc 1 6 20
	movq	(%rax), %rax
	leaq	-32(%rbp), %rcx
	movl	$10, %edx
	movq	%rcx, %rsi
	movq	%rax, %rdi
	call	strtoul
	movq	%rax, -24(%rbp)
	.loc 1 7 20
	movq	-48(%rbp), %rax
	addq	$8, %rax
	movq	(%rax), %rdx
	.loc 1 7 13
	movq	-32(%rbp), %rax
	.loc 1 7 5
	cmpq	%rax, %rdx
	jne	.L2
	.loc 1 8 3
	movl	$.LC0, %edi
	call	puts
	.loc 1 9 10
	movl	$-1, %eax
	jmp	.L3
.L2:
	.loc 1 11 7
	movb	$1, -1(%rbp)
.LBB2:
	.loc 1 12 21
	movq	$2, -16(%rbp)
	.loc 1 12 2
	jmp	.L4
.L6:
	.loc 1 13 9
	movq	-24(%rbp), %rax
	movl	$0, %edx
	divq	-16(%rbp)
	movq	%rdx, %rax
	.loc 1 13 6
	testq	%rax, %rax
	jne	.L5
	.loc 1 14 4
	movq	-16(%rbp), %rax
	movq	%rax, %rsi
	movl	$.LC1, %edi
	movl	$0, %eax
	call	printf
	.loc 1 15 10
	movb	$0, -1(%rbp)
.L5:
	.loc 1 12 36 discriminator 2
	addq	$1, -16(%rbp)
.L4:
	.loc 1 12 30 discriminator 1
	movq	-16(%rbp), %rax
	cmpq	-24(%rbp), %rax
	jb	.L6
.LBE2:
	.loc 1 18 5
	cmpb	$0, -1(%rbp)
	je	.L7
	.loc 1 19 3
	movl	$.LC2, %edi
	call	puts
.L7:
	movl	$0, %eax
.L3:
	.loc 1 21 1
	leave
	.cfi_def_cfa 7, 8
	ret
	.cfi_endproc
.LFE0:
	.size	main, .-main
.Letext0:
	.section	.debug_info,"",@progbits
.Ldebug_info0:
	.long	0x122
	.value	0x5
	.byte	0x1
	.byte	0x8
	.long	.Ldebug_abbrev0
	.uleb128 0x9
	.long	.LASF11
	.byte	0x1d
	.byte	0x3
	.long	0x31647
	.long	.LASF0
	.long	.LASF1
	.quad	.Ltext0
	.quad	.Letext0-.Ltext0
	.long	.Ldebug_line0
	.uleb128 0x4
	.long	.LASF3
	.byte	0x1
	.byte	0xc
	.long	0x49
	.long	0x49
	.uleb128 0x1
	.long	0x50
	.uleb128 0xa
	.byte	0
	.uleb128 0xb
	.byte	0x4
	.byte	0x5
	.string	"int"
	.uleb128 0x2
	.long	0x61
	.uleb128 0x5
	.long	0x50
	.uleb128 0x3
	.byte	0x1
	.byte	0x6
	.long	.LASF2
	.uleb128 0xc
	.long	0x5a
	.uleb128 0x4
	.long	.LASF4
	.byte	0x2
	.byte	0x1a
	.long	0x85
	.long	0x85
	.uleb128 0x1
	.long	0x55
	.uleb128 0x1
	.long	0x91
	.uleb128 0x1
	.long	0x49
	.byte	0
	.uleb128 0x3
	.byte	0x8
	.byte	0x7
	.long	.LASF5
	.uleb128 0x2
	.long	0x96
	.uleb128 0x5
	.long	0x8c
	.uleb128 0x2
	.long	0x5a
	.uleb128 0xd
	.long	.LASF12
	.byte	0x1
	.byte	0x4
	.byte	0x5
	.long	0x49
	.quad	.LFB0
	.quad	.LFE0-.LFB0
	.uleb128 0x1
	.byte	0x9c
	.long	0x11e
	.uleb128 0x6
	.long	.LASF6
	.byte	0xe
	.long	0x49
	.uleb128 0x2
	.byte	0x91
	.sleb128 -52
	.uleb128 0x6
	.long	.LASF7
	.byte	0x1b
	.long	0x8c
	.uleb128 0x2
	.byte	0x91
	.sleb128 -64
	.uleb128 0x7
	.long	.LASF8
	.byte	0x5
	.byte	0x8
	.long	0x96
	.uleb128 0x2
	.byte	0x91
	.sleb128 -48
	.uleb128 0x8
	.string	"l"
	.byte	0x6
	.byte	0x10
	.long	0x85
	.uleb128 0x2
	.byte	0x91
	.sleb128 -40
	.uleb128 0x7
	.long	.LASF9
	.byte	0xb
	.byte	0x7
	.long	0x11e
	.uleb128 0x2
	.byte	0x91
	.sleb128 -17
	.uleb128 0xe
	.quad	.LBB2
	.quad	.LBE2-.LBB2
	.uleb128 0x8
	.string	"d"
	.byte	0xc
	.byte	0x15
	.long	0x85
	.uleb128 0x2
	.byte	0x91
	.sleb128 -32
	.byte	0
	.byte	0
	.uleb128 0x3
	.byte	0x1
	.byte	0x2
	.long	.LASF10
	.byte	0
	.section	.debug_abbrev,"",@progbits
.Ldebug_abbrev0:
	.uleb128 0x1
	.uleb128 0x5
	.byte	0
	.uleb128 0x49
	.uleb128 0x13
	.byte	0
	.byte	0
	.uleb128 0x2
	.uleb128 0xf
	.byte	0
	.uleb128 0xb
	.uleb128 0x21
	.sleb128 8
	.uleb128 0x49
	.uleb128 0x13
	.byte	0
	.byte	0
	.uleb128 0x3
	.uleb128 0x24
	.byte	0
	.uleb128 0xb
	.uleb128 0xb
	.uleb128 0x3e
	.uleb128 0xb
	.uleb128 0x3
	.uleb128 0xe
	.byte	0
	.byte	0
	.uleb128 0x4
	.uleb128 0x2e
	.byte	0x1
	.uleb128 0x3f
	.uleb128 0x19
	.uleb128 0x3
	.uleb128 0xe
	.uleb128 0x3a
	.uleb128 0x21
	.sleb128 1
	.uleb128 0x3b
	.uleb128 0xb
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x27
	.uleb128 0x19
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x3c
	.uleb128 0x19
	.uleb128 0x1
	.uleb128 0x13
	.byte	0
	.byte	0
	.uleb128 0x5
	.uleb128 0x37
	.byte	0
	.uleb128 0x49
	.uleb128 0x13
	.byte	0
	.byte	0
	.uleb128 0x6
	.uleb128 0x5
	.byte	0
	.uleb128 0x3
	.uleb128 0xe
	.uleb128 0x3a
	.uleb128 0x21
	.sleb128 1
	.uleb128 0x3b
	.uleb128 0x21
	.sleb128 4
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte	0
	.byte	0
	.uleb128 0x7
	.uleb128 0x34
	.byte	0
	.uleb128 0x3
	.uleb128 0xe
	.uleb128 0x3a
	.uleb128 0x21
	.sleb128 1
	.uleb128 0x3b
	.uleb128 0xb
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte	0
	.byte	0
	.uleb128 0x8
	.uleb128 0x34
	.byte	0
	.uleb128 0x3
	.uleb128 0x8
	.uleb128 0x3a
	.uleb128 0x21
	.sleb128 1
	.uleb128 0x3b
	.uleb128 0xb
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x49
	.uleb128 0x13
	.uleb128 0x2
	.uleb128 0x18
	.byte	0
	.byte	0
	.uleb128 0x9
	.uleb128 0x11
	.byte	0x1
	.uleb128 0x25
	.uleb128 0xe
	.uleb128 0x13
	.uleb128 0xb
	.uleb128 0x90
	.uleb128 0xb
	.uleb128 0x91
	.uleb128 0x6
	.uleb128 0x3
	.uleb128 0x1f
	.uleb128 0x1b
	.uleb128 0x1f
	.uleb128 0x11
	.uleb128 0x1
	.uleb128 0x12
	.uleb128 0x7
	.uleb128 0x10
	.uleb128 0x17
	.byte	0
	.byte	0
	.uleb128 0xa
	.uleb128 0x18
	.byte	0
	.byte	0
	.byte	0
	.uleb128 0xb
	.uleb128 0x24
	.byte	0
	.uleb128 0xb
	.uleb128 0xb
	.uleb128 0x3e
	.uleb128 0xb
	.uleb128 0x3
	.uleb128 0x8
	.byte	0
	.byte	0
	.uleb128 0xc
	.uleb128 0x26
	.byte	0
	.uleb128 0x49
	.uleb128 0x13
	.byte	0
	.byte	0
	.uleb128 0xd
	.uleb128 0x2e
	.byte	0x1
	.uleb128 0x3f
	.uleb128 0x19
	.uleb128 0x3
	.uleb128 0xe
	.uleb128 0x3a
	.uleb128 0xb
	.uleb128 0x3b
	.uleb128 0xb
	.uleb128 0x39
	.uleb128 0xb
	.uleb128 0x27
	.uleb128 0x19
	.uleb128 0x49
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
	.byte	0
	.byte	0
	.uleb128 0xe
	.uleb128 0xb
	.byte	0x1
	.uleb128 0x11
	.uleb128 0x1
	.uleb128 0x12
	.uleb128 0x7
	.byte	0
	.byte	0
	.byte	0
	.section	.debug_aranges,"",@progbits
	.long	0x2c
	.value	0x2
	.long	.Ldebug_info0
	.byte	0x8
	.byte	0
	.value	0
	.value	0
	.quad	.Ltext0
	.quad	.Letext0-.Ltext0
	.quad	0
	.quad	0
	.section	.debug_line,"",@progbits
.Ldebug_line0:
	.section	.debug_str,"MS",@progbits,1
.LASF4:
	.string	"strtoul"
.LASF5:
	.string	"long unsigned int"
.LASF6:
	.string	"argc"
.LASF9:
	.string	"prime"
.LASF10:
	.string	"_Bool"
.LASF8:
	.string	"endptr"
.LASF12:
	.string	"main"
.LASF11:
	.string	"GNU C23 15.2.0 -mtune=generic -march=x86-64 -g"
.LASF2:
	.string	"char"
.LASF3:
	.string	"printf"
.LASF7:
	.string	"argv"
	.section	.debug_line_str,"MS",@progbits,1
.LASF1:
	.string	"/home/mreilly/wa/mcc"
.LASF0:
	.string	"factors.c"
	.ident	"GCC: (GNU) 15.2.0"
	.section	.note.GNU-stack,"",@progbits
