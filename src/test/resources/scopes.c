int printf(const char* f,...);
int main() {
	int a = 2;
	{
		int b = a + 2;
		int a = 3;
		printf("%d %d", b, a);
	}
	return a;
}
