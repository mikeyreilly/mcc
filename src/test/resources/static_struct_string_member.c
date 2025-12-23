typedef long unsigned int size_t;
struct S {
  const char *str;
};

extern size_t strlen (const char *__s);

int main() {
    static struct S s = {"some string!"};
    const char *str = s.str;
    return strlen(str);
}
