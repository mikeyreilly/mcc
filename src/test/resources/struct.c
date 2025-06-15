struct person {
    char* name;
    int age;
};

int main(void) {
    struct person p = {"Jimmy", 21};
    if (p.age!=21) {
        return 1;
    }
    return 0;
}
