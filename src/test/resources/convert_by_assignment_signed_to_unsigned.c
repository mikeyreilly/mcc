#include <stdio.h>

int main() {
    int i=-1;
    unsigned int u;
    unsigned int* pu=&u;
    int* pi=&i;
    pu=pi;
    printf("%u\n",*pu);
}
