#include <stdio.h>
#include <stdarg.h>

void foo(va_list ap)
{
    char *p = va_arg(ap, char *);
    printf("%s\n", p);
}

void test(...)
{
    va_list ap;
    va_start(ap);
    foo(ap);
    va_end(ap);
}

int main(void)
{
    char *x = "a string";
    test(x);
    return 0;
}
