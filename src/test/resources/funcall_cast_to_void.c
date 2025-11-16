int puts(const char *_c);

int foo(){
    puts("hello");
    return 0;
}

struct S{
    int (*p)();
};

int main(){
    struct S s;
    s.p=foo;
    (void)s.p();
    return 0;
}
