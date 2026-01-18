extern int printf (const char *__restrict __format, ...);

struct s {
    unsigned a:1;
    unsigned b:2;
    unsigned : 4;
    unsigned c:3;
    unsigned d:4;
} x = {0,1,2,3};

int main() {
    printf("x.a=%u\n", x.a);
    printf("x.b=%u\n", x.b);
    printf("x.c=%u\n", x.c);
    printf("x.d=%u\n", x.d);
}
