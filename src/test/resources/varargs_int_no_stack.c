typedef __builtin_va_list va_list;


int add_nums_c23(...) {
    int result = 0;
    va_list args;
    __builtin_c23_va_start(args);
    //    args[0].overflow_arg_area=17;
    int count = __builtin_va_arg(args, int);
    for (int i = 0; i < count; ++i) {
        result += __builtin_va_arg(args, int);
    }
    __builtin_va_end(args);
    return result;
}
int main(void) {
    return add_nums_c23(5, 1, 2, 3, 4, 5);
}
