#include <stdio.h>

int main(void) {
    unsigned __int32 x = 7;
    long v = 5;
    long old = _InterlockedExchangeAdd(&v, 3);
    long c = 0;
    long cmpOld = _InterlockedCompareExchange(&c, 9, 0);
    long cmpFail = _InterlockedCompareExchange(&c, 4, 0);
    unsigned char buf[4];
    __stosb(buf, 'A', 3);
    buf[3] = 0;
    printf("%u %ld %ld %ld %ld %ld %ld %s %llu\n", x, old, v,
           cmpOld, c, cmpFail, c, buf,
           __shiftright128(0, 1, 64));
    return 0;
}
