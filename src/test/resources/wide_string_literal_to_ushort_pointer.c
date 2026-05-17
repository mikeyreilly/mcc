typedef unsigned short wchar_t;

static void wide_assert(wchar_t const *message, wchar_t const *file, unsigned line) {
    (void)message;
    (void)file;
    (void)line;
}

int main(void) {
    ((void)((!!(1)) || (wide_assert(L"ok", L"file.c", 7), 0)));
    return 0;
}
