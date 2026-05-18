#include <stdio.h>
#include <windows.h>

int main(void) {
    WCHAR text[] = { 'm', 'c', 'c', 0 };
    LPUWSTR result = ua_CharUpperW(text);
    printf("%d %u\n", result == text, (unsigned)text[0]);
    return 0;
}
