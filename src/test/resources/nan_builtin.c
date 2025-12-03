extern int printf (const char * __format, ...);

int main(void) {
    double nanv = (__builtin_nanf (""));
    printf("NAN (double) printed with %%f: %f\n", nanv);
}
