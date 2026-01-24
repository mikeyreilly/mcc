extern int printf (const char *__restrict __format, ...);
typedef unsigned long int uintptr_t;

struct __attribute__((aligned(64))) BigAligned {
    char data[3];
};
struct __attribute__((aligned(alignof(struct BigAligned)))) BigAligned2 {
    char data[3];
};

int main(void) {
    struct BigAligned2 b;
    if (((uintptr_t)&b) % 64 != 0) {
        printf("FAIL: struct not 64-byte aligned\n");
    } else {
        printf("PASS\n");
        return 1;
    }
    return 0;
}
