static int matrix[2][3] = {
    { 10, 11, 12 },
    { 20, 21, 22 }
};

/* Pointer statically initialized with address-of nested array element */
static int *p = &matrix[1][2];

int main(void) {
    /* Extra sanity checks */
    if (p != &matrix[1][2]) return 1;
    if (*p != 22) return 2;

    return 0;
}
