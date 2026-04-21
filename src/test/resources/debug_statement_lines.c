int main(void) {
    int x = 0;
label:
    x = x + 1;
    if (x == 1) {
        x = x + 2;
    }
    while (x < 5) {
        x = x + 1;
        if (x == 4) {
            break;
        }
    }
    do {
        x = x + 1;
        continue;
    } while (x < 7);
    for (x = 0; x < 2; x = x + 1) {
        goto done;
    }
    switch (x) {
    case 0:
        x = 1;
        break;
    default:
        x = 2;
    }
done:
    return x;
}
