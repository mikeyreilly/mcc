int printf(const char *f, ...);

struct S {
    unsigned long x: 40;
};

int main () {
    struct S s;
    s.x = 0xffffffffff;
    unsigned long y = s.x << 8;
    printf("%lx", y);
}
