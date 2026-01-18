#include <limits.h>

extern int printf (const char *__restrict __format, ...);

int main() {
    _BitInt(65) b = 1;
    printf("sizeof(b)=%lu", sizeof(b));
    printf("BITINT_MAXWIDTH=%lu", BITINT_MAXWIDTH);

    return 0;
}
