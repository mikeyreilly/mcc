extern int printf (const char *__restrict __format, ...);

struct S {
    char c;
    double d;
};

int main(void) {
    printf("the first element is at offset %zu\n", __builtin_offsetof (struct S, c));
    printf("the double is at offset %zu\n", __builtin_offsetof (struct S, d));
}
