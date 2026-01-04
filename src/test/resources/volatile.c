// Declaring something as volatile doesn't currently have any effect.
// But we need to at least handle volatile declarators.

static int ** volatile a;
static int * volatile *b;
static int volatile **c;

int main() {
    return 0;
}
