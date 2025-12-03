extern int printf (const char * __format, ...);

int main(void) {
    float inf = (__builtin_inff ());
    double nanv = (__builtin_nanf (""));
    printf("INFINITY (float) printed with %%f: %f\n", inf);
    printf("NAN (double) printed with %%f: %f\n", nanv);
}
