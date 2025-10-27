typedef long unsigned int size_t;
extern void *memcpy (void *dest, const void *src, size_t n);
extern int printf (const char *__restrict __format, ...);


typedef struct Bitty {
    unsigned int a : 1;
    unsigned int b : 3;
    unsigned int : 5;
    unsigned int c : 4;
} Bitty;

int fun(Bitty bitty) {
    unsigned int tmp;
    memcpy(&tmp, &bitty, sizeof(tmp));
    return tmp;
}
Bitty swap_parts(Bitty bitty) {
    unsigned int tempa = bitty.b;
    unsigned int tempb = bitty.c;
    bitty.b = tempb;
    bitty.c = tempa;
    return bitty;
}
int main(void) {
    Bitty x = {0, 2, 3};
    Bitty y = x;
    y = swap_parts(y);
    printf("x=%d\n", fun(x));
    printf("y=%d\n", fun(y));
    return 0;
}
