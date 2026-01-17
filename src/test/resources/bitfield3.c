int printf(const char *s, ...);
struct S1 {
  int : 1;
  signed f1 : 6;
};

void main() {
    struct S1 g = {-2};
    printf("%d\n", g.f1);
}
