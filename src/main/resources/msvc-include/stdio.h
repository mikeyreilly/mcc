typedef unsigned long long size_t;
typedef struct _iobuf FILE;
int printf(const char *format, ...);
int puts(const char *s);
int snprintf(char *buffer, size_t count, const char *format, ...);
void perror(const char *s);
int fclose(FILE *stream);
FILE *fopen(const char *filename, const char *mode);
