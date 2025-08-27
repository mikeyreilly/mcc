int puts( const char* str );

char* f(char * c) {
    return c;
}

int main() {
    void*(*ff)(void*) = (void*(*)(void*))f;

    void* r = f("hello");
    puts(r);
}
