int main() {
    if (0xFp1f != 30.0f) {
        return 1;
    }
    if (0x1.8p1f != 3.0f) {
        return 2;
    }
    if (0xFp1 != 30.0) {
        return 3;
    }
    if (0x1.8p1 != 3.0) {
        return 4;
    }
    if (0xFp1 != 30.0l) {
        return 5;
    }
    if (0x1.8p1 != 3.0) {
        return 6;
    }
    return 0;
}
