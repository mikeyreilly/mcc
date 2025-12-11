extern int printf (const char * __format, ...);

int main() {
    int argc=1;
    //looks weird but it's legal
    int x=({ if (argc==1) printf ("true"); else printf ("false");17;});
    printf("\nx=%d\n", x);
    return 0;
}
