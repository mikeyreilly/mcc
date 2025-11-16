extern int puts (char * _c);

static char c[] = {
    65, 65, 65, 66, 66, 66, 0
};

static char *d = &c[3+1];

int main(void) {
    puts(d);
    return 0;
}
