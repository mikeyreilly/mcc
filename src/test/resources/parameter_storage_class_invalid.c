int scale(int value, static unsigned factor) {
    return value * (int) factor;
}

int main(void) {
    return scale(2, 3);
}
