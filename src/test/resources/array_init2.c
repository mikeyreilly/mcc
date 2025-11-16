extern int puts (char * _c);

int main(void) {
    char c[] = {
        65, 65, 65, 66, 66, 66, 0
    };

    char *d = &c[3];
    puts(d);
    return 0;
}
