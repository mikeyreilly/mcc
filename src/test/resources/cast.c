int main(int argc, char** args) {
    long l = 1L + (1L << 32);
    int i = (int) l;
    if (i != 1) {
        return 1;
    }
    return 0;
}
