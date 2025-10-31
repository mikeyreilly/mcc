typedef long unsigned int size_t;
extern int printf (const char * __format, ...);
extern int snprintf (char * __s, size_t __maxlen, const char * __format, ...);
extern void perror (const char *__s);
extern void *malloc (size_t __size);
extern char *strdup (const char *__s);
struct Outer {
    int i;
    struct Inner {
        int j;
        char *s;
    } inner[];
};
int main(void) {
    int n = 3;
    printf("sizeof(struct Outer) = %lu\n", sizeof(struct Outer));
    printf("offsetof(struct Outer, inner) = %lu\n", __builtin_offsetof(struct Outer, inner));
    struct Outer *outer = malloc(sizeof(struct Outer) + n * sizeof(outer->inner[0]));
    if (!outer) {
        perror("malloc");
        return 1;
    }
    outer->i = 42;
    for (int k = 0; k < n; k++) {
        outer->inner[k].j = k * 10;
        char buf[32];
        snprintf(buf, sizeof(buf), "string %d", k);
        outer->inner[k].s = strdup(buf);
    }
    printf("Outer.i = %d\n", outer->i);
    for (int k = 0; k < n; k++) {
        printf("  Inner[%d]: j=%d, s=\"%s\"\n", k, outer->inner[k].j, outer->inner[k].s);
    }
    return 0;
}
