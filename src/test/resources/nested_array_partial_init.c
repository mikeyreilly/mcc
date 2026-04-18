int main(void) {
    int arr[2][3] = {
        {1},
        {2}
    };

    if (arr[0][0] != 1 || arr[0][1] != 0 || arr[0][2] != 0) {
        return 1;
    }
    if (arr[1][0] != 2 || arr[1][1] != 0 || arr[1][2] != 0) {
        return 2;
    }
    return 0;
}
