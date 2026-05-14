typedef int myint;

int a(void) {
    int myint = 5;
    return myint;
}

int b(void) {
    myint y = 17;
    typedef long myint;
    return y + sizeof(myint);
}


int c(void) {
    typedef long myint;
    return sizeof(myint);
}

int d(void) {
    return sizeof(myint);
}

int main(void) {
    if (a() != 5) {
        return 1;
    }
    if (b() != 17 + sizeof(long)) {
        return 2;
    }
    if (c() != sizeof(long)) {
        return 3;
    }
    if (d() != 4) {
        return 4;
    }
    return 0;
}
