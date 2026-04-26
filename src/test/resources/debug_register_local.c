int main(void) {
    int a = 1;
    int b = 2;
    int c = a + b;
    int d = c + 1;
    int e = d - 1;
    int f = e;
    int g = f;
    return g == 3 ? 0 : 1;
}
