#include <stdlib.h>
#include <pthread.h>
#include <assert.h>
#include <dat3m.h>
#include <lkmm.h>

int x, y, a, b;

pthread_t h;
void *handler(void *arg)
{
    WRITE_ONCE(y, 3);
    return NULL;
}

void *thread_1(void *arg)
{
    __VERIFIER_make_interrupt_handler();
    pthread_create(&h, NULL, handler, NULL);

    __VERIFIER_disable_irq();
    smp_store_release(&x, 1);
    a = READ_ONCE(y);
    __VERIFIER_enable_irq();

    pthread_join(h, 0);

    return NULL;
}

void *thread_2(void *arg)
{
    b = READ_ONCE(x);
    smp_store_release(&y, 2);
    return NULL;
}

int main()
{
    pthread_t t1, t2;

    pthread_create(&t1, 0, thread_1, NULL);
    pthread_create(&t2, 0, thread_2, NULL);
    pthread_join(t1, 0);
    pthread_join(t2, 0);

    assert(!(b == 1 && a == 3 && y == 3));

    return 0;
}
