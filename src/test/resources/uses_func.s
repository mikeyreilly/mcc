                .globl	foo
                .text
foo:
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$0, %rsp
	movq	%rdi, %rsi
	leaq	.Lstring.123(%rip), %rdi
	movl	$0, %eax
	call	printf
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
                .globl	main
                .text
main:
	pushq	%rbp
	movq	%rsp, %rbp
	subq	$0, %rsp
	leaq	__func__.117(%rip), %rdi
	call	foo
	movl	$0, %eax
	movq	%rbp, %rsp
	popq	%rbp
	ret
                .globl	__pid_t
                .bss
                .balign 4
__pid_t:
                .zero 4
                .globl	__syscall_slong_t
                .bss
                .balign 8
__syscall_slong_t:
                .zero 8
                .globl	__blkcnt_t
                .bss
                .balign 8
__blkcnt_t:
                .zero 8
                .globl	__loff_t
                .bss
                .balign 8
__loff_t:
                .zero 8
                .globl	__clockid_t
                .bss
                .balign 4
__clockid_t:
                .zero 4
                .globl	__int_least32_t
                .bss
                .balign 4
__int_least32_t:
                .zero 4
                .globl	__u_quad_t
                .bss
                .balign 8
__u_quad_t:
                .zero 8
                .globl	__off_t
                .bss
                .balign 8
__off_t:
                .zero 8
                .globl	__useconds_t
                .bss
                .balign 4
__useconds_t:
                .zero 4
                .globl	__FILE
                .bss
                .balign 8
__FILE:
                .zero 216
                .data
                .balign 1
__func__.117:
                .asciz "main"
                .globl	__suseconds64_t
                .bss
                .balign 8
__suseconds64_t:
                .zero 8
                .data
                .balign 1
__func__.116:
                .asciz "foo"
                .globl	__key_t
                .bss
                .balign 4
__key_t:
                .zero 4
                .globl	__uint8_t
                .bss
                .balign 1
__uint8_t:
                .zero 1
                .globl	__quad_t
                .bss
                .balign 8
__quad_t:
                .zero 8
                .globl	__clock_t
                .bss
                .balign 8
__clock_t:
                .zero 8
                .globl	__daddr_t
                .bss
                .balign 4
__daddr_t:
                .zero 4
                .globl	__uint_least32_t
                .bss
                .balign 4
__uint_least32_t:
                .zero 4
                .globl	__uint16_t
                .bss
                .balign 2
__uint16_t:
                .zero 2
                .globl	__gid_t
                .bss
                .balign 4
__gid_t:
                .zero 4
                .globl	__fsword_t
                .bss
                .balign 8
__fsword_t:
                .zero 8
                .globl	__uint64_t
                .bss
                .balign 8
__uint64_t:
                .zero 8
                .globl	__uid_t
                .bss
                .balign 4
__uid_t:
                .zero 4
                .globl	__mbstate_t
                .bss
                .balign 4
__mbstate_t:
                .zero 8
                .globl	__nlink_t
                .bss
                .balign 8
__nlink_t:
                .zero 8
                .globl	__int64_t
                .bss
                .balign 8
__int64_t:
                .zero 8
                .globl	__fsblkcnt_t
                .bss
                .balign 8
__fsblkcnt_t:
                .zero 8
                .globl	__sig_atomic_t
                .bss
                .balign 4
__sig_atomic_t:
                .zero 4
                .globl	FILE
                .bss
                .balign 8
FILE:
                .zero 216
                .globl	__gnuc_va_list
                .bss
                .balign 16
__gnuc_va_list:
                .zero 24
                .globl	_IO_lock_t
                .bss
                .balign 1
_IO_lock_t:
                .zero 1
                .globl	__blkcnt64_t
                .bss
                .balign 8
__blkcnt64_t:
                .zero 8
                .globl	__u_int
                .bss
                .balign 4
__u_int:
                .zero 4
                .globl	__ino64_t
                .bss
                .balign 8
__ino64_t:
                .zero 8
                .globl	__uint_least16_t
                .bss
                .balign 2
__uint_least16_t:
                .zero 2
                .globl	__fsfilcnt_t
                .bss
                .balign 8
__fsfilcnt_t:
                .zero 8
                .globl	size_t
                .bss
                .balign 8
size_t:
                .zero 8
                .globl	__time_t
                .bss
                .balign 8
__time_t:
                .zero 8
                .globl	__uintmax_t
                .bss
                .balign 8
__uintmax_t:
                .zero 8
                .globl	__ssize_t
                .bss
                .balign 8
__ssize_t:
                .zero 8
                .globl	__rlim_t
                .bss
                .balign 8
__rlim_t:
                .zero 8
                .globl	__u_short
                .bss
                .balign 2
__u_short:
                .zero 2
                .globl	__fsfilcnt64_t
                .bss
                .balign 8
__fsfilcnt64_t:
                .zero 8
                .globl	__rlim64_t
                .bss
                .balign 8
__rlim64_t:
                .zero 8
                .globl	__intmax_t
                .bss
                .balign 8
__intmax_t:
                .zero 8
                .globl	__fpos64_t
                .bss
                .balign 8
__fpos64_t:
                .zero 16
                .globl	__suseconds_t
                .bss
                .balign 8
__suseconds_t:
                .zero 8
                .globl	__int32_t
                .bss
                .balign 4
__int32_t:
                .zero 4
                .globl	__builtin_va_list
                .bss
                .balign 16
__builtin_va_list:
                .zero 24
                .globl	__int_least64_t
                .bss
                .balign 8
__int_least64_t:
                .zero 8
                .globl	__syscall_ulong_t
                .bss
                .balign 8
__syscall_ulong_t:
                .zero 8
                .globl	__id_t
                .bss
                .balign 4
__id_t:
                .zero 4
                .globl	__timer_t
                .bss
                .balign 8
__timer_t:
                .zero 8
                .globl	__blksize_t
                .bss
                .balign 8
__blksize_t:
                .zero 8
                .globl	__uint_least8_t
                .bss
                .balign 1
__uint_least8_t:
                .zero 1
                .globl	__int_least8_t
                .bss
                .balign 1
__int_least8_t:
                .zero 1
                .globl	__int_least16_t
                .bss
                .balign 2
__int_least16_t:
                .zero 2
                .globl	__fsblkcnt64_t
                .bss
                .balign 8
__fsblkcnt64_t:
                .zero 8
                .globl	__int8_t
                .bss
                .balign 1
__int8_t:
                .zero 1
                .globl	__u_char
                .bss
                .balign 1
__u_char:
                .zero 1
                .globl	__off64_t
                .bss
                .balign 8
__off64_t:
                .zero 8
                .globl	fpos_t
                .bss
                .balign 8
fpos_t:
                .zero 16
                .globl	__dev_t
                .bss
                .balign 8
__dev_t:
                .zero 8
                .globl	__uint_least64_t
                .bss
                .balign 8
__uint_least64_t:
                .zero 8
                .globl	__caddr_t
                .bss
                .balign 8
__caddr_t:
                .zero 8
                .globl	__uint32_t
                .bss
                .balign 4
__uint32_t:
                .zero 4
                .globl	__socklen_t
                .bss
                .balign 4
__socklen_t:
                .zero 4
                .globl	__fsid_t
                .bss
                .balign 4
__fsid_t:
                .zero 8
                .globl	__fpos_t
                .bss
                .balign 8
__fpos_t:
                .zero 16
                .globl	__u_long
                .bss
                .balign 8
__u_long:
                .zero 8
                .globl	__mode_t
                .bss
                .balign 4
__mode_t:
                .zero 4
                .globl	__intptr_t
                .bss
                .balign 8
__intptr_t:
                .zero 8
                .globl	__int16_t
                .bss
                .balign 2
__int16_t:
                .zero 2
                .globl	__ino_t
                .bss
                .balign 8
__ino_t:
                .zero 8
                .section .rodata
.Lstring.123:
                .asciz "%s\n"
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
