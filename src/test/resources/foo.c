extern int printf (const char *restrict __format, ...);

static int thing_before(void *pPrior) {
  long *p;
  p = (long*)pPrior;
  p--;
  return (int)p[0];
}

int main() {
    long a[] = {17, 19};
    int x = thing_before(a+1);
    printf("it's %d", x);
}
