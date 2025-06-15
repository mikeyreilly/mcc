/* int p, *get_jimmy(); */

/* int *get_jimmy() { */
/*     return 0; */
/* } */

int foo(void) {
        return 5;
}


int main (void) {
    int z = 3;
    int x = 2, *y =&z;
    if(x + *y +foo() != 10){
        return 1;
    }
    /* int *jimmy = get_jimmy(); */
    /* if (jimmy != 0) return 2 */;
    return 0;

}
