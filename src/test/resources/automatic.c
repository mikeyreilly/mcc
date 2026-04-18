int test_partial(void) {
    double arr[5] = {1.0, 123e4};
    return (arr[0] == 1.0 && arr[1] == 123e4 && !arr[2] && !arr[3] && !arr[4]);
}

int main(void) {
    if (!test_partial()) {
        return 2;
    }
    return 0;
}
