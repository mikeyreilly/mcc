struct S {
    int a;
    int b;
    int c;
    int d;
    int e;
};

int bar(struct S *x) {
  if (x->a != 1)
    return 1;
  /* if (x->b != 2) */
  /*   return 2; */
  /* if (x->c != 3) */
  /*   return 3; */
  /* if (x->d != 4) */
  /*   return 4; */
  /* if (x->e != 5) */
  /*   return 5; */
  /* if (f != 6) */
  /*   return 6; */
  /* if (g != 7) */
  /*   return 7; */
  /* if (h != 8) */
  /*   return 8; */
  /* if (i != 9) */
  /*   return 9; */
  /* if (j != 10) */
  /*   return 10; */
  return 0;
}

int foo(struct S x) {
    return bar(&x);
}

int main() {
    struct S x = {1,2,3,4,5};

  return foo(x);
}
