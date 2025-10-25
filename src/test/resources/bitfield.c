typedef long unsigned int size_t;
extern void *memcpy (void *dest, const void *src, size_t n);
extern int printf (const char *__restrict __format, ...);

typedef struct Bitty {
    unsigned char a:1;
    unsigned char b:3;
    unsigned char c:4;
} Bitty;

int fun(Bitty bitty) {
    unsigned char tmp;
    memcpy(&tmp, &bitty, sizeof(tmp));
    return tmp;
}
Bitty swap_parts(Bitty bitty) {
    unsigned char tempa = bitty.b;
    unsigned char tempb = bitty.c;
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
