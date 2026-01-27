struct S {
    unsigned int a : 5;   /* can hold values 0..31 */
    signed int   b : 6;   /* roughly -32..31 */
};

int main(void)
{
    struct S s;

    /* += */
    s.a = 3;
    s.a += 4;
    if (s.a != 7)
        return 1;

    /* -= */
    s.a -= 2;
    if (s.a != 5)
        return 2;

    /* *= */
    s.a *= 3;
    if (s.a != 15)
        return 3;

    /* <<= */
    s.a <<= 1;
    if (s.a != 30)
        return 4;

    /* >>= */
    s.a >>= 2;
    if (s.a != 7)
        return 5;

    /* |= */
    s.a = 0x12;   /* 18 */
    s.a |= 0x5;   /* OR with 00101 */
    if (s.a != (18 | 5))
        return 6;

    /* &= */
    s.a &= 0xF;
    if (s.a != ((18 | 5) & 0xF))
        return 7;

    /* ^= */
    s.a ^= 0x3;
    if (s.a != (((18 | 5) & 0xF) ^ 3))
        return 8;

    /* Signed bit-field test */
    s.b = -5;
    s.b += 3;
    if (s.b != -2)
        return 9;

    s.b <<= 1;
    if (s.b != -4)
        return 10;

    s.b >>= 2;
    if (s.b != -1)
        return 11;

    return 0;
}
