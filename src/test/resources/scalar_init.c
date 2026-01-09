extern int printf(const char *__format, ...);

static int ga = {};
static int gb = {2};
static int gc = 3;

void main() {
    int a = {};
    int b = {5};
    int c = 7;
    printf("%d %d %d\n",ga, gb, gc);
    printf("%d %d %d\n", a,
        b,  c);
}
