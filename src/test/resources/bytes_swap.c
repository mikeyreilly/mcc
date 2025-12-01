#include <stdio.h>

int main(void) {
    unsigned long l = 0x0123456789ABCDEFUL;
    printf("%016lX\n", __builtin_bswap64(l));
    unsigned int i = 0x12345678U;
    printf("%08X\n", __builtin_bswap32(i));

    unsigned short s = 0x1234U;
    printf("%04X\n", __builtin_bswap16(s));
    return 0;
}
