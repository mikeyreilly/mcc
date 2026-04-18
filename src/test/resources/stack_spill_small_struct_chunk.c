struct eleven_bytes {
    char arr[11];
};

int check(struct eleven_bytes in_reg,
          int a, int b, int c, int d, int e,
          struct eleven_bytes on_stack) {
    if (a != 1 || b != 2 || c != 3 || d != 4 || e != 5) {
        return 1;
    }
    if (in_reg.arr[8] != 9 || in_reg.arr[9] != 10 || in_reg.arr[10] != 11) {
        return 2;
    }
    if (on_stack.arr[8] != 9 || on_stack.arr[9] != 10 || on_stack.arr[10] != 11) {
        return 3;
    }
    return 0;
}

int main(void) {
    struct eleven_bytes value = {{0}};
    value.arr[8] = 9;
    value.arr[9] = 10;
    value.arr[10] = 11;
    return check(value, 1, 2, 3, 4, 5, value);
}
