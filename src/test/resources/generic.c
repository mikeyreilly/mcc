extern int printf (const char *__restrict __format, ...);

int main(void) {
    printf("%s\n", _Generic(0,
                            long: "long",
                            int: "int",
                            default: "other"));

    printf("%s\n", _Generic(0L,
                            long: "long",
                            int: "int",
                            default: "other"));
    return 0;
}
