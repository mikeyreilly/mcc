int main(void) {
    unsigned char uc = 255;
    signed char sc = -127;
    char c = 5;

    if ((uc >> 3) != 31) {
        return 1;
    }
    if ((sc >> c) != -4) {
        return 2;
    }
    return 0;
}
