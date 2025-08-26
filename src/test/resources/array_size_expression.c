int puts( const char* str );

int main(void) {
    double r = 1.0;
    char a[sizeof(r)+1];
    for (int i=0; i < sizeof(a); i++) {
        a[i]='a'+i;
    }
    a[sizeof(r) - 1] = 0;
    puts(a);
    return 0;
}
