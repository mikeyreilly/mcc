struct S {
    void (*f)(void*);
};

void foo(void *p){
    return;
}

static int bar(struct S *p){
    if( p->f==foo) {
        return 1;}
    return 0;
}

int main() {
    struct S s;
    s.f = foo;
    return bar(&s);
}
