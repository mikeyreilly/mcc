struct Foo {
  int type;
  union {
      int i;
      double d;
  } value;
};

int main(void) {
    struct Foo f;
    f.type=1;
    f.value.i=17;

    struct Foo g;
    g.type = 1;
    g.value.d = 19.0;

    return f.value.i + g.value.d;
}
