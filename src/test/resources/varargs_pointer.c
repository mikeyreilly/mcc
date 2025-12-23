#include <stdio.h>
#include <stdarg.h>

void test(const char *tag, ...)
{
    va_list ap;
    va_start(ap, tag);

    int p = *va_arg(ap, int *);
    printf("%s: %d\n", tag, p);

    va_end(ap);
}

int main(void)
{
    int x = 42;
    test("ptr", &x);
    return 0;
}
