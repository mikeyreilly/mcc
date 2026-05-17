#ifndef MCC_MSVC_INTRIN_H
#define MCC_MSVC_INTRIN_H

#define _byteswap_ushort(x) __builtin_bswap16((unsigned short)(x))
#define _byteswap_ulong(x) __builtin_bswap32((unsigned long)(x))
#define _byteswap_uint64(x) __builtin_bswap64((unsigned long long)(x))

void _ReadWriteBarrier(void);

#endif
