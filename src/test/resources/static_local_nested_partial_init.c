static int check(void) {
    static unsigned long arr[3][2] = {
        {11ul},
        {22ul}
    };

    if (arr[0][0] != 11ul || arr[0][1] != 0ul) {
        return 1;
    }
    if (arr[1][0] != 22ul || arr[1][1] != 0ul) {
        return 2;
    }
    if (arr[2][0] != 0ul || arr[2][1] != 0ul) {
        return 3;
    }
    return 0;
}

int main(void) {
    return check();
}
