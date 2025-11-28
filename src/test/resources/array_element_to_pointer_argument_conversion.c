short foo (char* a){
    return 5;
}

int main() {
    int szNew = 0;
    char *a[2];
    a[0] = "a";
    szNew += foo(a[0]);
    return szNew;
}
