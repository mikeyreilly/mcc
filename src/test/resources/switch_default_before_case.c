int classify(int c)
{
    switch (c) {
        case '-':
            return 1;
        default:
            return 4;
        case '.':
            return 2;
        case '*':
            return 3;
    }
    return 5;
}

int main(void)
{
    if (classify('.') != 2) return 1;
    if (classify('*') != 3) return 2;
    if (classify('-') != 1) return 3;
    if (classify('x') != 4) return 4;
    return 0;
}
