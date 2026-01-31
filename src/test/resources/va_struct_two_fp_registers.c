typedef __builtin_va_list va_list;

int printf(const char *f, ...);
struct S {
	int i[3];
};
struct S s={{11, 13, 17}};

int check(...) {
	va_list ap;
	__builtin_c23_va_start(ap);
	struct S arg = __builtin_va_arg(ap, struct S);

	int x = 17;
	int y = arg.i[2];
	if (x != y)
		return 2;

	__builtin_va_end(ap);
	return 0;
}
int main() {
	return check(s);
}
