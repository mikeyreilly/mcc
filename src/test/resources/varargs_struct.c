#include <stdarg.h>

struct S {
    int i;
    double d;
};

int foo (...) {
    va_list args;
    va_start(args);
    double sum = 0;
    for (int i=0;i<7;i++){
        struct S s = va_arg(args, struct S);
        sum+=s.i;
        sum+=s.d;
    }
    va_end(args);
    return (int)sum;
}

int main(void) {
    struct S a0 = {
        100,
        17.0
    };
    struct S a1 = {
        200,
        19.0
    };
    struct S a2 = {
        300,
        23.0
    };
    struct S a3 = {
        400,
        23.0
    };
    struct S a4 = {
        500,
        29.0
    };
    struct S a5 = {
        600,
        31.0
    };
    struct S a6 = {
        700,
        37.0
    };
    return foo(a0,a1,a2,a3,a4,a5,a6);
}
