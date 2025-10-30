#include <stdio.h>

int main(void) {
    unsigned long x = 0x0123456789ABCDEFUL;
    printf("%016lX\n", __builtin_bswap64(x));
    return 0;
}
