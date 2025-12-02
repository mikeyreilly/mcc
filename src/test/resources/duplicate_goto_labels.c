int foo(void) {
    goto end;

end:
    return 42;
}

int main(void) {
    goto end;

end:
    return foo();
}
