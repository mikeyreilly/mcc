extern int printf(const char *__format, ...);

int main() {
    float f = 0.5f;
    f =- f;
    printf("%f\n", f );


    double d = 0.5;
    d =- d;
    printf("%f\n", d);

}
