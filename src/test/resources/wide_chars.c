typedef int wchar_t;
typedef unsigned short char16_t;
typedef unsigned int char32_t;

extern int printf(const char* f, ...);



int main() {
    printf("sizeof wchar_t %lu\n",sizeof(wchar_t));
    printf("sizeof char16_t %lu\n",sizeof(char16_t));
    printf("sizeof char32_t %lu\n",sizeof(char32_t));

    char c = 'A';
    printf("char 'A' = %d\n",c);

    int i = L'A';

    printf("L'A' = %d\n",i);

    return 0;
}
