typedef int (*f)();
static int z = 42;

int foo(){
    return z;
}

int main(){
    f p;
    p = &foo;
    f *pp = &p;
    int pint = (***pp)();
    return pint;
}
