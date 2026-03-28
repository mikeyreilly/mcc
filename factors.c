extern int printf (const char *__restrict __format, ...);
extern unsigned long int strtoul (const char *__restrict __nptr, char **__restrict __endptr, int __base);

int main(int argc, char** argv) {
	char *endptr;
	unsigned long l = strtoul(argv[1], &endptr, 10);
	if (endptr == argv[1]) {
		printf("didn't get number\n");
		return -1;
	}
	bool prime = true;
	for (unsigned long d = 2; d < l; d++) {
		if (l % d == 0) {
			printf("%lu\n", d);
			prime = false;
		}
	}
	if (prime) {
		printf("prime\n");
	}
}
