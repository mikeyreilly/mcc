extern int printf(const char *f, ...);


void foo(int a, int b, int c, int d, int e, int f, long* y, long* z) {
  printf("a[0]=%ld\n", y[0]);
  printf("b[0]=%ld\n", z[0]);
}

int main(void) {
  long y[1] = {11L};
  long z[1] = {13L};
  foo(0,1,2,3,4,5, y, z);
  return 0;
}
