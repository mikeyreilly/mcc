extern int printf(const char *__format, ...);

typedef unsigned long long u64;

struct Db {
    u64 flags;
};

int main(void) {
    struct Db db = {0};
    db.flags |= 0x00000040
              | 0x00000020
              | 0x80000000
              | ((u64)0x00000010 << 32)
              | ((u64)0x00000020 << 32)
              | ((u64)0x00000040 << 32);
    printf("%llx\n", db.flags);
    return 0;
}
