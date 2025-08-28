struct inner
{
    int i;
};

struct container
  {
    struct inner *a;
    union
      {
          int x;
          double d;
      };
  };


int main() {
    struct inner i = {5};
    struct container c1;
    c1.a=&i;
    c1.x=17;


    struct inner j = {6};
    struct container c2;
    c2.a=&i;
    c2.d=19.0;

    return c1.a->i+c1.x+c2.a->i+c2.d;
}
