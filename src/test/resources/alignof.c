extern int printf (const char *__restrict __format, ...);

struct S {
    char c;
    double d;
};

int main(void) {
    return alignof(struct S);
}
