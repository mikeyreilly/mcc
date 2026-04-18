struct inner {
    int one_i;
    unsigned char two_arr[3];
    unsigned three_u;
};

struct outer {
    long one_l;
    struct inner two_struct;
    char *three_msg;
    double four_d;
    int five_i;
};

int main(void) {
    struct outer partial = {
            1000,
            {1},
            "Partial"};

    if (partial.one_l != 1000 || partial.two_struct.one_i != 1) {
        return 1;
    }
    if (partial.two_struct.two_arr[0] || partial.two_struct.two_arr[1] ||
        partial.two_struct.two_arr[2] || partial.two_struct.three_u) {
        return 2;
    }
    if (partial.four_d || partial.five_i) {
        return 3;
    }
    if (partial.three_msg[0] != 'P' || partial.three_msg[1] != 'a') {
        return 4;
    }
    return 0;
}
