extern int printf (const char * __format, ...);

int main(void) {
    float inf = (__builtin_inff ());
    printf("INFINITY (float) printed with %%f: %f\n", inf);
}
