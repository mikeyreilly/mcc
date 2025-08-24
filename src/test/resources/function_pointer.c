int function_taking_function_pointer(int (*)(int), int);

int function_taking_function_pointer(int (*f)(int), int x){
    return f(x);
}

int square(int x) {
	return x*x;
}

int main(void) {
	int (*func1)(int) = square;
	return func1(8) + function_taking_function_pointer(func1, 8);
}
