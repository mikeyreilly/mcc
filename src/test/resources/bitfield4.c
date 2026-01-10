struct S {
    signed int f : 11;
};
int main(void) {
  int j = 1025;
  struct S l;

  // max value for signed int:11 is 1023
  if ((l.f = j) == j)
      return 1;
  return 0;
}
