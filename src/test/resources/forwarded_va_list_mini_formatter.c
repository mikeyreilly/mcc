#include <stdio.h>
#include <stdarg.h>

static void append(char *out, int *n, const char *z, int len)
{
    for (int i = 0; i < len; i++) {
        out[*n] = z[i];
        *n = *n + 1;
    }
    out[*n] = 0;
}

static void mini_vformat(char *out, const char *fmt, va_list ap)
{
    int n = 0;
    int c;
    char *bufpt;
    int precision;
    int length;
    int done;

    for (; (c = *fmt) != 0; ++fmt) {
        if (c != '%') {
            bufpt = (char *)fmt;
            do {
                fmt++;
            } while (*fmt && *fmt != '%');
            append(out, &n, bufpt, (int)(fmt - bufpt));
            if (*fmt == 0) break;
        }
        if ((c = (*++fmt)) == 0) {
            append(out, &n, "%", 1);
            break;
        }

        precision = -1;
        done = 0;
        do {
            switch (c) {
                case '.': {
                    c = *++fmt;
                    if (c == '*') {
                        precision = va_arg(ap, int);
                        c = *++fmt;
                    } else {
                        precision = 0;
                    }
                    done = 1;
                    break;
                }
                default:
                    done = 1;
                    break;
            }
        } while (!done && (c = (*++fmt)) != 0);

        switch (c) {
            case 's':
                bufpt = va_arg(ap, char *);
                if (precision >= 0) {
                    for (length = 0; length < precision && bufpt[length]; length++) {}
                } else {
                    for (length = 0; bufpt[length]; length++) {}
                }
                append(out, &n, bufpt, length);
                break;
        }
    }
}

static void mini_outer(char *out, const char *fmt, va_list ap)
{
    mini_vformat(out, fmt, ap);
}

static void mini_format(char *out, const char *fmt, ...)
{
    va_list ap;
    va_start(ap, fmt);
    mini_outer(out, fmt, ap);
    va_end(ap);
}

int main(void)
{
    char out[64];
    mini_format(out, "CREATE %s %.*s", "TABLE", 10, "foo(i int);");
    printf("%s\n", out);
    return 0;
}
