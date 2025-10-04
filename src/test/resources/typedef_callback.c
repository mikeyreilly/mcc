extern int puts (const char *__s);
typedef int (*OperationCallback)(int, int);

int add(int a, int b) {
    return a + b;
}

int multiply(int a, int b) {
    return a * b;
}

int main(void) {
    OperationCallback op;
    int x = 5;
    int y = 3;
    op = add;
    int result1 = op(x, y);
    puts(result1 == 8 ? "8" : "error");
    op = multiply;
    int result2 = op(x, y);
    puts(result2 == 15 ? "15" : "error");
    return 0;
}
