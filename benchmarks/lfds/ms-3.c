#include <stdatomic.h>
#include <pthread.h>
#include <assert.h>
#include <stdbool.h>
#include <stdlib.h>

#define CAS(ptr, expected, desired) (atomic_compare_exchange_strong_explicit(ptr, expected, desired, __ATOMIC_ACQ_REL, __ATOMIC_RELAXED))
#define load(loc) (atomic_load_explicit(loc, __ATOMIC_ACQUIRE))
#define store(loc, val) (atomic_store_explicit(loc, val, __ATOMIC_RELEASE))
#define rx_load(loc) (atomic_load_explicit(loc, __ATOMIC_RELAXED))
#define rx_store(loc, val) (atomic_store_explicit(loc, val, __ATOMIC_RELAXED))

#define EMPTY -1

typedef struct Node {
	int val;
	_Atomic(struct Node*) next;
} Node;

_Atomic(Node *) Tail;
_Atomic(Node *) Head;


void init() {
	Node* node = malloc(sizeof (Node));
	atomic_init(&node->next, NULL);
	atomic_init(&Head, node); 
	atomic_init(&Tail, node);
}

void enqueue(int value) {
	Node *tail, *next, *node;

    node = malloc(sizeof (Node));
	node->val = value;
	atomic_init(&node->next, NULL);

	while (true) {
		tail = load(&Tail);
		assert(tail != NULL);
		next = load(&tail->next);

		if (tail == load(&Tail)) {
			if (next != NULL) {
				CAS(&Tail, &tail, next);
			} else {
				if (CAS(&tail->next, &next, node)) {
				    CAS(&Tail, &tail, node);
					break;
				}
			}
		}
	}
}

int dequeue() {
	Node *head, *next, *tail;
	int result;

	while (true) {
		head = load(&Head);
		assert(head != NULL);
		tail = load(&Tail);
		assert(tail != NULL);
		next = load(&head->next);

		if (head == load(&Head)) {
			if (next == NULL) {
				result = EMPTY;
				break;
			} else {
				if (head == tail) {
					CAS(&Tail, &tail, next);
				} else {
					result = next->val;
					if (CAS(&Head, &head, next)) {
                        //retire(head);
                        break;
                    }
				}
			}
		}
	}

	return result;
}


// =========== Worker threads ==============

void *worker_1(void *unused)
{
	enqueue(42);
    int r = dequeue();
	assert(r != EMPTY);
	return NULL;
}

void *worker_2(void *unused)
{
	enqueue(41);
    int r = dequeue();
	assert(r != EMPTY);
	return NULL;
}


int main()
{
    pthread_t t1, t2, t3;

    init();

    pthread_create(&t1, NULL, worker_1, NULL);
    pthread_create(&t2, NULL, worker_2, NULL);
    pthread_create(&t3, NULL, worker_2, NULL);

    pthread_join(t1, NULL);
    pthread_join(t2, NULL);
    pthread_join(t3, NULL);
    
    int r = dequeue();
    assert(r == EMPTY);

    return 0;
}
