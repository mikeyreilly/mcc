#include <stdio.h>

static inline unsigned long foo(unsigned  long x) {
    return ((x & 0x00000000000000FFUL) << 56) |
        ((x & 0x000000000000FF00UL) << 40) |
        ((x & 0x0000000000FF0000UL) << 24) |
        ((x & 0x00000000FF000000UL) << 8)  |
        ((x & 0x000000FF00000000UL) >> 8)  |
        ((x & 0x0000FF0000000000UL) >> 24) |
        ((x & 0x00FF000000000000UL) >> 40) |
        ((x & 0xFF00000000000000UL) >> 56);
}

int main(void) {
    unsigned long x = 0x0123456789ABCDEFUL;
    unsigned long y = foo(x);
    printf("%016lX\n", y);
    return 0;
}
