int function_taking_function_pointer(int (*)(int), int);

int function_taking_function_pointer(int (*f)(int), int x){
    return f(x);
}

struct S {
    int (*fp)(int);
};

int square(int x) {
	return x*x;
}

int main(void) {
	int (*func1)(int) = square;
    struct S s;
    s.fp = square;
	return func1(2) + function_taking_function_pointer(func1, 3) + s.fp(5);
}
