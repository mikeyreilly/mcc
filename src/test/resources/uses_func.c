#include <stdio.h>

int foo(char *fname) {
    printf("%s\n",fname);
}

int main() {
    //    static const char __func__[] = "main";
    foo(__func__);
}
