typedef __builtin_va_list va_list;
#define va_start(...) __builtin_c23_va_start(__VA_ARGS__)
#define va_arg(ap, type) __builtin_va_arg(ap, type)
#define va_end(ap) __builtin_va_end(ap)
