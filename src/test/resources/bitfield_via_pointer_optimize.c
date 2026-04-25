struct Flags {
    unsigned a : 1;
    unsigned b : 1;
    unsigned c : 1;
    unsigned d : 1;
    unsigned e : 1;
};

static void clear_e(struct Flags *p)
{
    p->e = 0;
}

static int get_e(struct Flags *p)
{
    return p->e;
}

int main(void)
{
    struct Flags f = {0, 0, 0, 0, 1};
    if (get_e(&f) != 1) return 1;
    clear_e(&f);
    if (get_e(&f) != 0) return 2;
    return 0;
}
