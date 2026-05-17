int apply(int fn(int), int x) {
    return fn(x);
}

int square(int x) {
    return x * x;
}

int main(void) {
    return apply(square, 7);
}
