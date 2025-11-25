static struct S {
    void (*f)(void);
} aS[1];

int foo(void) {
    return 42;
}

static int bar(){
    return ((int(*)(void))aS[0].f)();
}

int main(void) {
    aS[0].f = (void (*)(void))  foo;
    return bar();
}
