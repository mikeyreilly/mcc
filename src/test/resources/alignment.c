extern int printf (const char *__restrict __format, ...);
typedef unsigned long int uintptr_t;
int global_aligned __attribute__((aligned(32)));
struct S {
    char c;
    int x __attribute__((aligned(16)));
};

void test_local(void) {
    char local __attribute__((aligned(8)));
    if (((uintptr_t)&local) % 8 != 0) {
        printf("FAIL: local not 8-byte aligned\n");
    }
}
int main(void) {
    int failures = 0;
    if (((uintptr_t)&global_aligned) % 32 != 0) {
        printf("FAIL: global_aligned not 32-byte aligned\n");
        failures++;
    }
    struct S s;
    if (((uintptr_t)&s.x) % 16 != 0) {
        printf("FAIL: struct member not 16-byte aligned\n");
        failures++;
    }
    test_local();
    if (failures == 0) {
        printf("PASS\n");
    }
    return failures;
}
