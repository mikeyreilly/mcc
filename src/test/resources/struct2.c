int main() {
    struct {
        char *a;
    } a[] = {
        { "hello"}
    };
    return sizeof(a);
}
