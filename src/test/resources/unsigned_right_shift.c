extern int printf(const char *__format, ...);

int main() {
  unsigned int u;
  u = 0xFFFFFFFF;
  u >>= 1;
  printf("%X\n", u);
}
