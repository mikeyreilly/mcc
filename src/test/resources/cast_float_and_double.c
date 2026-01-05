extern int printf(const char *, ...);

int main(void) {
    float f = 1.5f;
    double d = 0x1.fffffffffffffp1;

    /* float -> double */
    double fd = (double)f;

    /* double -> float */
    float df = (float)d;

    /* Print with enough precision to catch mistakes */
    printf("f  = %.16f\n", f);
    printf("d  = %.16f\n", d);
    printf("fd = %.16f\n", fd);
    printf("df = %.16f\n", df);

    /* /\* Return a value dependent on casts *\/ */
    return f == 1.5f && d == 3.9999999999999996 && fd == 1.5f && df == 4.0 ? 0 : 1;
}
