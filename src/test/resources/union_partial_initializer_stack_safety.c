union big_u {
    char c;
    unsigned char bytes[11];
};

int main(void) {
    union big_u x = {1};
    if (x.c != 1) {
        return 1;
    }
    return 0;
}
