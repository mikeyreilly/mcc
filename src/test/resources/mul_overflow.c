extern int printf (const char *__restrict __format, ...);

int main() {
    int product;
    bool b = __builtin_mul_overflow (6, 7, &product);
    printf("product=%d, overflow=%b\n", product, b);
    b = __builtin_mul_overflow (2147483647, 2, &product);
    printf("product=%d, overflow=%b\n", product, b);
}
