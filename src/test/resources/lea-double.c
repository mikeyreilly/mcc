extern int printf(const char *f, ...);


void foo(int a, int b, int c, int d, int e, int f, double* y, double* z) {
  printf("a[0]=%lf\n", y[0]);
  printf("b[0]=%lf\n", z[0]);
}

int main(void) {
  double y[1] = {11.5};
  double z[1] = {13.5};
  foo(0,1,2,3,4,5, y, z);
  return 0;
}
