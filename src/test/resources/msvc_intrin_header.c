#include <intrin.h>
#include <stdio.h>

int main(void) {
    unsigned short s = _byteswap_ushort(0x1234);
    unsigned long l = _byteswap_ulong(0x12345678);
    unsigned long long q = _byteswap_uint64(0x0123456789ABCDEFULL);
    _ReadWriteBarrier();
    printf("%04X %08lX %016llX\n", s, l, q);
    return 0;
}
