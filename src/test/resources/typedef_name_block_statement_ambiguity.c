#include <stdio.h>

typedef int T;
typedef int Label;

int main(void) {
    int T = 3;
    T = T + 4;
Label:
    T = T + 1;

    {
        typedef int Inner;
        {
            int Inner = 9;
            Inner = Inner + 1;
            printf("%d %d\n", T, Inner);
        }
    }

    return 0;
}
