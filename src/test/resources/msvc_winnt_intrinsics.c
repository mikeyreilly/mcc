#include <stdio.h>

int main(void) {
    unsigned __int32 x = 7;
    long v = 5;
    long old = _InterlockedExchangeAdd(&v, 3);
    unsigned char buf[4];
    __stosb(buf, 'A', 3);
    buf[3] = 0;
    printf("%u %ld %ld %s %llu\n", x, old, v, buf,
           __shiftright128(0, 1, 64));
    return 0;
}
