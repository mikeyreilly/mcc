typedef __builtin_va_list va_list;


int add_nums_c23(...) {
    double result = 0;
    va_list args;
    __builtin_c23_va_start(args);
    //    args[0].overflow_arg_area=17;
    int count = __builtin_va_arg(args, int);
    for (int i = 0; i < count; ++i) {
        result += __builtin_va_arg(args, double);
    }
    __builtin_va_end(args);
    return result;
}

int main(void) {
    // passing a bunch of floats - these will be promoted to double
    return add_nums_c23(11, 1.0f, 2.0f, 3.0f, 4.0f, 5.0f,
        6.0f, 7.0f, 8.0f, 9.0f, 10.0f, 11.0f);
}
