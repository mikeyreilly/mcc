extern int printf (const char *__restrict __format, ...);

int main() {
    int sum;
    bool b = __builtin_sub_overflow (2, 1, &sum);
    printf("sum=%d, overflow=%b\n", sum, b);
    b = __builtin_sub_overflow (-2147483648, 1, &sum);
    printf("sum=%d, overflow=%b\n", sum, b);
}
