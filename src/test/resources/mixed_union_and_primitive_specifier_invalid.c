union U {
    int x;
};

int main(void) {
    union U short y;
    return (int) sizeof(y);
}
