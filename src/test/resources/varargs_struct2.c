#include <stdarg.h>

struct S {
    double d;
    char c;
    int i;
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
        17,'A',
        100.0
    };
    struct S a1 = {
        19,'A',
        200.0
    };
    struct S a2 = {
        23,'A',
        300.0
    };
    struct S a3 = {
        23,'A',
        400.0
    };
    struct S a4 = {
        29,'A',
        500.0
    };
    struct S a5 = {
        31,'A',
        600.0
    };
    struct S a6 = {
        37,'A',
        700.0
    };
    return foo(a0,a1,a2,a3,a4,a5,a6);
}
