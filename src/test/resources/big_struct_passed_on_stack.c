struct Big {
    int i[10];
};

int sum(struct Big b) {
    int sum = 0;
    for (int i = 0; i < 10; i++) {
        sum += b.i[i];
    }
    return sum;
}

int main() {
    struct Big b = {
        {1,2,3,4,5,6,7,8,9,10}
    };
    int s = sum(b);
    return s;
}
