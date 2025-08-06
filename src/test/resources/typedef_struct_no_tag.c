int main(void) {
    typedef struct {int i;} S;
    S s = {17};
    return s.i;
}
