                .globl	main
                .text
main:
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$64, %rsp
	movq	$0, -8(%rbp)
	movq	$27, -16(%rbp)
	leaq	-8(%rbp), %r11
	movq	%r11, -24(%rbp)
	# AtomicStore
	movq	-24(%rbp), %rax
	movq	-16(%rbp), %r10
	movq	%r10, 0(%rax)
	leaq	-8(%rbp), %r11
	movq	%r11, -32(%rbp)
	movq	-32(%rbp), %rax
	# load:  Pseudo{identifier='ptr.8', type=QUADWORD, isStatic=false, isAliased=false}
	movq	0(%rax), %r10
	movq	%r10, -40(%rbp)
	movq	-40(%rbp), %r10
	movq	%r10, -48(%rbp)
	leaq	.Lstring.10(%rip), %r11
	movq	%r11, -56(%rbp)
	movq	-56(%rbp), %rdi
	movq	-8(%rbp), %rsi
	movq	-48(%rbp), %rdx
	movl	$0, %eax
	call	printf
	movl	%eax, -60(%rbp)
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
                .globl	__builtin_va_list
                .bss
                .balign 16
__builtin_va_list:
                .zero 24
                .section .rodata
                .balign 16
.Lstring.10:
                .asciz "x = %ld, y = %ld\n"
                .section .rodata
                .balign 8
.Lc.0x1.0p63:
                .quad 4890909195324358656
                .section .rodata
                .balign 16
.Lc._0x0.0p0:
                .quad -9223372036854775808
                .ident	"GCC: (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0"
                .section	.note.GNU-stack,"",@progbits
