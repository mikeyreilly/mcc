extern int printf (const char *__restrict __format, ...);

struct S {
    long i;
    long d;
};

typedef struct S S;

void bar (S *a0, S *a1, S *a2, S *a3, S *a4, S *a5, S *a6, S *a7) {

    printf("%ld\n", a0->i);
    printf("%ld\n", a0->d);
    printf("%ld\n", a1->i);
    printf("%ld\n", a1->d);
    printf("%ld\n", a2->i);
    printf("%ld\n", a2->d);
    printf("%ld\n", a3->i);
    printf("%ld\n", a3->d);
    printf("%ld\n", a4->i);
    printf("%ld\n", a4->d);
    printf("%ld\n", a5->i);
    printf("%ld\n", a5->d);
    printf("%ld\n", a6->i);
    printf("%ld\n", a6->d);
    printf("%ld\n", a7->i);
    printf("%ld\n", a7->d);

}

int main(void) {
    struct S a0 = {0,1};
    struct S a1 = {2,3};
    struct S a2 = {4,5};
    struct S a3 = {6,7};
    struct S a4 = {8,9};
    struct S a5 = {10,11};
    struct S a6 = {12,13};
    struct S a7 = {14,15};
    bar(&a0,&a1,&a2,&a3,&a4,&a5,&a6,&a7);
}
