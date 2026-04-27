#ifndef MCC_MSVC_STDARG_H
#define MCC_MSVC_STDARG_H

#ifndef _VA_LIST_DEFINED
#define _VA_LIST_DEFINED
typedef __builtin_va_list va_list;
#endif

#define va_start(...) __builtin_c23_va_start(__VA_ARGS__)
#define va_arg(ap, type) __builtin_va_arg(ap, type)
#define va_end(ap) __builtin_va_end(ap)

#endif
