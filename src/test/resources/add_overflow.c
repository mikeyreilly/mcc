#include <stdlib.h>
extern int printf (const char *__restrict __format, ...);


int main(int argc, char** args) {
    int sum;
    bool b = __builtin_add_overflow (atoi(args[0]), 2, &sum);
    printf("sum=%d, overflow=%b\n",sum, b);
    b = __builtin_add_overflow (1073741824, 1073741824, &sum);
    printf("sum=%d, overflow=%b\n",sum, b);
}
