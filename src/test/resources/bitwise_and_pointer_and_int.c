int foo(void){
    return 0;
}

int main(void){
    int (*f)(void);
    int i=0;
    f = (i & 0x080000) ? 0 : foo;
    return f();
}
