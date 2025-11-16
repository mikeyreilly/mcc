extern int printf (const char *__restrict __format, ...);

int main(void) {
    long x = 0L;
    long z = 27L;

    // Atomically store 42 into x
    __atomic_store_n(&x, z, __ATOMIC_SEQ_CST);

    // Atomically load from x
    long y = __atomic_load_n(&x, __ATOMIC_SEQ_CST);

    printf("x = %ld, y = %ld\n", x, y);
    return 0;
}
