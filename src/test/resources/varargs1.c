extern int printf (char *format, ...);

int main (void) {
    char *s = "hello";
    int d = 17;
    long ld = 1L << 32;
    double f = 17.0/12.0;
    printf("%s %d %ld %f\n", s, d, ld, f);
    return 0;
}
