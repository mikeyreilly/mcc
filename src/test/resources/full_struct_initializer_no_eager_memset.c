struct payload {
    long a;
    long b;
    long c;
};

int main(void) {
    struct payload p = {1, 2, 3};
    return (int) (p.a + p.b + p.c - 6);
}
