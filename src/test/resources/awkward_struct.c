struct outer {
    const struct inner *i;
};

struct inner {
    double d;
};

int main() {
    struct outer o;
    struct inner i = {10.0};
    o.i=&i;
    return o.i->d;
}
