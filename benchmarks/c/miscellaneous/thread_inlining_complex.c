#include <pthread.h>
#include <stdatomic.h>

/*
    The test checks if inlining of thread-creating functions + reassigning pthread_t variables works properly.
    Expected result: PASS
    Current result:
     - development: PASS
     - rework: Parsing error (matching between pthread_join and pthread_create fails)
*/

atomic_int data;

void *thread2(void *arg)
{
   data = 42;
}

pthread_t threadCreator() {
   pthread_t t;
   pthread_create(&t, NULL, thread2, NULL);
   return t;
}

void *thread1(void *arg)
{
    pthread_t t = threadCreator();
    pthread_join(t, NULL);
}

int main()
{
    pthread_t t1;
    pthread_create(&t1, NULL, thread1, NULL);
    pthread_join(t1, NULL);

    assert(data == 42);
}
