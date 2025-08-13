int square(int x) {
	return x*x;
}

int main(void) {
	int (*func1)(int) = square;
	return func1(8);
}
