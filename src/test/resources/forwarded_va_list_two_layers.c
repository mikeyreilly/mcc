#include <stdio.h>
#include <stdarg.h>

void format_piece(va_list ap)
{
    char *kind = va_arg(ap, char *);
    int length = va_arg(ap, int);
    char *sql = va_arg(ap, char *);
    printf("%s %.*s\n", kind, length, sql);
}

void format_outer(va_list ap)
{
    format_piece(ap);
}

void test(const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    format_outer(ap);
    va_end(ap);
}

int main(void)
{
    test("CREATE %s %.*s", "TABLE", 10, "create table foo(i int)");
    return 0;
}
