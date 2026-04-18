int main(void) {
    unsigned char value = 250;
    long shift = 32;

    value >>= (shift - 1);
    return value != 0;
}
