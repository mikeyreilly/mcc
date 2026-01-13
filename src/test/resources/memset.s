                .globl	main
                .text
main:
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$112, %rsp
	movb	$0, -112(%rbp)
	movb	$0, -111(%rbp)
	movb	$0, -110(%rbp)
	movb	$0, -109(%rbp)
	movb	$0, -108(%rbp)
	movb	$0, -107(%rbp)
	movb	$0, -106(%rbp)
	movb	$0, -105(%rbp)
	movb	$0, -104(%rbp)
	movb	$0, -103(%rbp)
	movb	$0, -102(%rbp)
	movb	$0, -101(%rbp)
	movb	$0, -100(%rbp)
	movb	$0, -99(%rbp)
	movb	$0, -98(%rbp)
	movb	$0, -97(%rbp)
	movb	$0, -96(%rbp)
	movb	$0, -95(%rbp)
	movb	$0, -94(%rbp)
	movb	$0, -93(%rbp)
	movb	$0, -92(%rbp)
	movb	$0, -91(%rbp)
	movb	$0, -90(%rbp)
	movb	$0, -89(%rbp)
	movb	$0, -88(%rbp)
	movb	$0, -87(%rbp)
	movb	$0, -86(%rbp)
	movb	$0, -85(%rbp)
	movb	$0, -84(%rbp)
	movb	$0, -83(%rbp)
	movb	$0, -82(%rbp)
	movb	$0, -81(%rbp)
	movb	$0, -80(%rbp)
	movb	$0, -79(%rbp)
	movb	$0, -78(%rbp)
	movb	$0, -77(%rbp)
	movb	$0, -76(%rbp)
	movb	$0, -75(%rbp)
	movb	$0, -74(%rbp)
	movb	$0, -73(%rbp)
	movb	$0, -72(%rbp)
	movb	$0, -71(%rbp)
	movb	$0, -70(%rbp)
	movb	$0, -69(%rbp)
	movb	$0, -68(%rbp)
	movb	$0, -67(%rbp)
	movb	$0, -66(%rbp)
	movb	$0, -65(%rbp)
	movb	$0, -64(%rbp)
	movb	$0, -63(%rbp)
	movb	$0, -62(%rbp)
	movb	$0, -61(%rbp)
	movb	$0, -60(%rbp)
	movb	$0, -59(%rbp)
	movb	$0, -58(%rbp)
	movb	$0, -57(%rbp)
	movb	$0, -56(%rbp)
	movb	$0, -55(%rbp)
	movb	$0, -54(%rbp)
	movb	$0, -53(%rbp)
	movb	$0, -52(%rbp)
	movb	$0, -51(%rbp)
	movb	$0, -50(%rbp)
	movb	$0, -49(%rbp)
	movb	$0, -48(%rbp)
	movb	$0, -47(%rbp)
	movb	$0, -46(%rbp)
	movb	$0, -45(%rbp)
	movb	$0, -44(%rbp)
	movb	$0, -43(%rbp)
	movb	$0, -42(%rbp)
	movb	$0, -41(%rbp)
	movb	$0, -40(%rbp)
	movb	$0, -39(%rbp)
	movb	$0, -38(%rbp)
	movb	$0, -37(%rbp)
	movb	$0, -36(%rbp)
	movb	$0, -35(%rbp)
	movb	$0, -34(%rbp)
	movb	$0, -33(%rbp)
	movb	$0, -32(%rbp)
	movb	$0, -31(%rbp)
	movb	$0, -30(%rbp)
	movb	$0, -29(%rbp)
	movb	$0, -28(%rbp)
	movb	$0, -27(%rbp)
	movb	$0, -26(%rbp)
	movb	$0, -25(%rbp)
	movb	$0, -24(%rbp)
	movb	$0, -23(%rbp)
	movb	$0, -22(%rbp)
	movb	$0, -21(%rbp)
	movb	$0, -20(%rbp)
	movb	$0, -19(%rbp)
	movb	$0, -18(%rbp)
	movb	$0, -17(%rbp)
	movb	$0, -16(%rbp)
	movb	$0, -15(%rbp)
	movb	$0, -14(%rbp)
	movb	$0, -13(%rbp)
	movb	$0, -12(%rbp)
	movb	$0, -11(%rbp)
	movb	$0, -10(%rbp)
	leaq	-112(%rbp), %rsi
	movq	$1663823975275763479, %rax
	movq	%rsi, %rdi
	movl	$12, %ecx
	rep stosq
	movq	%rsi, %rdx
	movl	$387389207, 96(%rdx)
	movw	$5911, 100(%rdx)
	movb	$23, 102(%rdx)
	movl	$0, %r8d
.Lfor.5.start:
	cmpl	$103, %r8d
	movl	$0, %r9d
	setl	%r9b
	cmpl	$0, %r9d
	je	.Lfor.5.break
	leaq	-112(%rbp), %rax
	movslq	%r8d, %rdx
	leaq	(%rax,%rdx,1), %rax
	movb	0(%rax), %r9b
	movzbl	%r9b, %r9d
	cmpl	$23, %r9d
	movl	$0, %r9d
	setne	%r9b
	cmpl	$0, %r9d
	je	.Lend.14
	movl	$1, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
.Lend.14:
.Lfor.5.continue:
	addl	$1, %r8d
	jmp	.Lfor.5.start
.Lfor.5.break:
	leaq	.Lstring.17(%rip), %rdi
	movl	$0, %eax
	call	printf
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
                .section .rodata
.Lstring.17:
                .asciz "done"
                .ident	"GCC: (Ubuntu 11.4.0-1ubuntu1~22.04) 11.4.0"
                .section	.note.GNU-stack,"",@progbits
