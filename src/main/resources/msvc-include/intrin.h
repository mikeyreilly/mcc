#ifndef MCC_MSVC_INTRIN_H
#define MCC_MSVC_INTRIN_H

unsigned short _byteswap_ushort(unsigned short _Number);
unsigned long _byteswap_ulong(unsigned long _Number);
unsigned __int64 _byteswap_uint64(unsigned __int64 _Number);

void _ReadWriteBarrier(void);

#endif
