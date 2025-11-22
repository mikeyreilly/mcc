extern int printf (const char *__restrict __format, ...);

int main() {
    /* bool t = true; */
    /* bool f = false; */
    /* int ti = t; */
    /* int fi = f; */
    /* printf("true=%d\n", ti); */
    /* printf("false=%d\n", fi); */
    /* printf("sizeof(bool)=%lu\n", sizeof(bool)); */
    int i = 0xffff;
    bool b = (bool)i;
    printf("cast=%d\n", b);
    return 0;
}
