#include <stdio.h>
#include <string.h>
#include <limits.h>
#include <stdarg.h>

static int read_pointer_arg(const char *tag, ...) {
    va_list ap;
    va_start(ap, tag);
    int *p = va_arg(ap, int *);
    va_end(ap);
    return *p;
}

int main(void) {
    char dst[4];
    int value = 42;
    memcpy(dst, "ok", 3);
    if (strcmp(dst, "ok") != 0) return 1;
    printf("%s %d %lu\n", dst, read_pointer_arg("p", &value),
           (unsigned long)BITINT_MAXWIDTH);
    return 0;
}
