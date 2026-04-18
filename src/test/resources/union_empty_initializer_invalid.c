union value {
    int i;
    double d;
};

int main(void) {
    union value v = {};
    return v.i;
}
