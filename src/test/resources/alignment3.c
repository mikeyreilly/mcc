extern int printf(const char *format, ...);

void foo(void) {}

void bar(void) __attribute__((aligned(256)));

void bar(void) {}

int main() {
  printf("alignof(foo)=%lu\n", alignof(foo));
  printf("alignof(bar)=%lu\n", alignof(bar));
}
