void barrier(void)
{
    __sync_synchronize();
}

int main(void)
{
    barrier();
    return 0;
}
