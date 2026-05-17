#include <stdio.h>

int main(void) {
    int x = 1;
    __try {
        x = x + 2;
    } __except(x = 99) {
        x = 100;
    }
    printf("%d\n", x);
    return 0;
}
