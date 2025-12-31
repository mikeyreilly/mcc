extern int printf (const char *__restrict __format, ...);

struct S {
    double x0;
    double x1;
    double x2;
    double x3;
    double x4;
    double x5;
    double x6;
    double x7;
    double x8;
    double x9;
    double x10;
    double x11;
    double x12;
    double x13;
    double d;
    char c;
    int i;
};

typedef struct S S;

int bar (S *a0, S *a1, S *a2, S *a3, S *a4, S *a5, S *a6, S *a7) {
    double sum = 0;
    sum+=17*a0->i;
    sum+=18*a0->d;
    sum+=19*a1->i;
    sum+=20*a1->d;
    sum+=21*a2->i;
    sum+=22*a2->d;
    sum+=23*a3->i;
    sum+=24*a3->d;
    sum+=25*a4->i;
    sum+=26*a4->d;
    sum+=27*a5->i;
    sum+=28*a5->d;
    sum+=29*a6->i;
    sum+=30*a6->d;
    sum+=31*a7->i;
    sum+=32*a7->d;
    return (int)sum;
}

void foo(S *a0, S *a1, S *a2, S *a3, S *a4, S *a5, S *a6, S *a7){
    a0->d=2*17.000000;
    a0->i=1*100;
    a1->d=3*19.000000;
    a1->i=4*200;
    a2->d=5*23.000000;
    a2->i=6*300;
    a3->d=7*23.000000;
    a3->i=8*400;
    a4->d=9*29.000000;
    a4->i=10*500;
    a5->d=11*31.000000;
    a5->i=12*600;
    a6->d=13*37.000000;
    a6->i=14*700;
    a7->d=15*38.000000;
    a7->i=16*210;
}

int main(void) {
    S a0 ;
    S a1 ;
    S a2 ;
    S a3 ;
    S a4 ;
    S a5 ;
    S a6 ;
    S a7 ;
    foo(&a7,&a6,&a5,&a4,&a3,&a2,&a1,&a0);
    int sum = bar(&a0,&a1,&a2,&a3,&a4,&a5,&a6,&a7);
    printf("sum=%d",sum);
}
