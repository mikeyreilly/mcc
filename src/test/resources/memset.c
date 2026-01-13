typedef long unsigned int size_t;
extern int printf (const char *__restrict __format, ...);

int main ()
{
  unsigned char t[103] = {};
  __builtin_memset(&t,0x17,sizeof(t));
  for (int i = 0; i< 103; i++) {
          if (t[i] != 0x17){
          return 1;
      }
  }
  printf("done");

  return 0;
}
