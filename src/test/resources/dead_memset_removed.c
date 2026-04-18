struct payload {
    long a;
    long b;
    long c;
};

int target(void) {
    struct payload p = {0};
    return 7;
}

int main(void) {
    return target();
}
