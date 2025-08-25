static unsigned char utf8_width_print(const char *zUtf){
  const unsigned char *a = (const unsigned char*)zUtf;
  
  unsigned char sum = 0;
  for (int i=0;i<5;i++) {
      sum+=a[i];
  }
  return sum;
}

int main(int argc, char** args) {
    const char* hello = "hello";
    int sum = utf8_width_print(hello);
    if (sum != 20) {
        return 1;
    }
    long l = 1L + (1L << 32);
    int i = (int) l;
    if (i != 1) {
        return 2;
    }
    return 0;
}
