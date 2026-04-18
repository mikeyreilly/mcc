struct hidden;
extern struct hidden external_obj;

int main(void) {
    struct hidden *ptr = &external_obj;
    return ptr == 0;
}
