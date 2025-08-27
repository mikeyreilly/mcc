int main() {
    double d = 0.0;

    int size = -1;
    switch(sizeof (d)) {
    case 1: size = 10; break;
    case 1+1: size = 11; break;
    case 1|2: size = 12; break;
    case 2*2: size = 13; break;
    case 8-3: size = 14; break;
    case 7^1: size = 15; break;
    case sizeof(d)-1: size = 16; break;
    case sizeof(d): size = sizeof(d); break;
    default: size = -2;
    }
    return size;
}
