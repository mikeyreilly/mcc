struct callbacks {
    int version;
    int (*init)(void *);
    void (*shutdown)(void *);
    void *arg;
};

struct config {
    int enabled;
    struct callbacks callbacks;
    void *heap;
    int heapSize;
    int refCount;
    void *mutex;
};

static struct config config = {
    1,
    {0, 0, 0, 0},
    0,
    17,
    0,
    0
};
static int after = 23;

int main(void) {
    if (config.enabled != 1) return 1;
    if (config.callbacks.version != 0) return 2;
    if (config.callbacks.init != 0) return 3;
    if (config.callbacks.shutdown != 0) return 4;
    if (config.callbacks.arg != 0) return 5;
    if (config.heap != 0) return 6;
    if (config.heapSize != 17) return 7;
    if (config.refCount != 0) return 8;
    if (config.mutex != 0) return 9;
    if (after != 23) return 10;
    return 0;
}
