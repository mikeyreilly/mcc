extern int printf (const char *__restrict __format, ...);

int main(){
    long long l = 1LL;
    printf("%lu\n", sizeof(long long));
    
    return 0;
}
