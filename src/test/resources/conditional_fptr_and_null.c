int foo(void){
    return 42;
}
int bar(void){
    return 17;
}

int main(){
    bool b = true;
    int (*f)(void) = b ? foo : 0;
    return f();
}
