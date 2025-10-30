struct S {
    int i;
};

int main(void) {
    return __builtin_offsetof(struct S, a);
}
