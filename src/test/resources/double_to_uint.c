extern int printf (const char *__restrict __format, ...);



int main() {
    double d[] = {
        -1.0,
        1.0,
        1.0E10,
        1.0E20,
        1.0E30
    };
    int l = sizeof(d)/sizeof(d[0]);
    printf("long\n");
    for (int i = 0; i < l; i++){
        unsigned long ui = (unsigned long)d[i];
        printf("%lu\n", ui);
    }
    printf("int\n");
    for (int i = 0; i < l; i++){
        unsigned int ui = (unsigned int)d[i];
        printf("%u\n", ui);
    }
       printf("short\n");
    for (int i = 0; i < l; i++){
        unsigned short ui = (unsigned short)d[i];
        printf("%u\n", ui);
    }
    printf("char\n");
    for (int i = 0; i < l; i++){
        unsigned char ui = (unsigned char)d[i];
        printf("%u\n", ui);
    }
    return 0;
}
