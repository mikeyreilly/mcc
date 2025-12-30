extern int printf (const char *__restrict __format, ...);

struct s {
    unsigned char a:1;
    unsigned char b:2;
};

int main() {
    struct s x;
    struct s *p=&x;
    p->a=0;
    p->b=1;
    unsigned char a = p->a;
    printf("x.a=%u",a);
}
