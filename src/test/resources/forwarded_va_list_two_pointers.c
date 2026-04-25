#include <stdio.h>
#include <stdarg.h>

void format_pair(va_list ap)
{
    char *left = va_arg(ap, char *);
    char *right = va_arg(ap, char *);
    printf("%s.%s\n", left, right);
}

void test(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    format_pair(ap);
    va_end(ap);
}

int main(void)
{
    test("%s.%s", "main", "sqlite_master");
    return 0;
}
