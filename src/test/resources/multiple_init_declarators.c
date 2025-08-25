int foo(void) {
        return 5;
}


int main (void) {
    int a, b;
    int z = 3;
    int x = 2, *y =&z;
    a=17;
    b=19;
    if(x + *y +foo() + a + b!= 46){
        return 1;
    }
    return 0;

}
