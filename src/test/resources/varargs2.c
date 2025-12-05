typedef __builtin_va_list va_list;


/* This compiler, like gcc, does not inline variadic functions. Putting the
   inline keyword to ensure using it doesn't break anything.*/

inline int add_nums_c23(...) {
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
    return add_nums_c23(11, 1, 2, 3, 4, 5,
        6, 7, 8, 9, 10, 11);
}
