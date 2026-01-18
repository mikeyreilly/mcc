//#include <stdio.h>

int main() {
    char c = '\101';
    if (c!='A') return 1;
    c = '\xfff';
    if (c!=-1) return 2;
    unsigned char *x = "\200\20b\x7f";
    if(x[0] != 128) {
        return 3;
    }
    if(x[1] != 16) {
        return 4;
    }
    if(x[3] != 0x7f) {
        return 5;
    }
    return 0;
}
