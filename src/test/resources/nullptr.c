extern int printf (const char *__restrict __format, ...);

int main(void) {
    int *p = nullptr;
    int x = 42;
    int *q = &x;
    if (p == nullptr) {
        printf("p is a null pointer.\n");
    }
    if (q != nullptr) {
        printf("q points to value: %d\n", *q);
    }
    q = nullptr;
    if (q == nullptr) {
        printf("q is now a null pointer.\n");
    }
    return 0;
}
