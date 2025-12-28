extern int printf (const char *__restrict __format, ...);
static int foo(){
  return 0;
}
static int bar(){
  return 42;
}
static int (*const fs[])() = {
  foo,
  bar,
};
int main(void) {
    printf("bar returned %d",  fs[1]());
}
