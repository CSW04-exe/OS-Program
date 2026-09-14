# Priority Process Scheduler

**Type:** Individual project
**Contributor:** Carter Ward
**Course:** CS 490 (Operating Systems) — Project 2
**Completed:** Fall 2025

## Purpose
This project simulates an OS process scheduler in Java using real threads. It builds a multi-threaded priority scheduler that reads process definitions from stdin, queues them by priority, and dispatches them to a pool of worker threads that "execute" each one by sleeping for its duration. The goal was hands-on practice with the two problems every real scheduler solves at once: choosing which process runs next, and doing so safely when multiple execution units share one ready queue.

## Problem and Approach
This models priority-based CPU scheduling on a multiprocessor system as a classic producer-consumer problem. The main thread is the producer — it parses stdin into process objects and pushes them onto a shared ready queue. Five `Worker` threads act as consumers/CPUs, each pulling the highest-priority process and running it. The scheduling policy: any `RT` process always beats any `NORMAL` process regardless of number; within a class, lower priority numbers go first; ties break by arrival order, keeping the scheduler stable.

## Structure and Methodologies
- Hand-written binary min-heap (`ProcessQueue`) over an `ArrayList`, with `siftUp`/`siftDown` for O(log n) insert/remove, instead of `java.util.PriorityQueue`
- `SimProcess` data class, `ProcessClass` enum (`REAL_TIME`/`NORMAL`), and `Worker` thread subclass
- Custom `compare()` encoding the three-level ordering: class, then priority, then sequence
- Concurrency via intrinsic locking only, no `java.util.concurrent`: `synchronized` methods on the queue, `wait()`/`notifyAll()` for blocking/waking workers, and a flag-based poison-pill shutdown

## Process
1. Main thread creates the shared queue and starts 5 `Worker` threads, which block on an empty queue.
2. Main thread reads `PROCESS <ms> <RT|NORMAL> <priority> <name>` lines from stdin and enqueues each, waking workers via `notifyAll()`.
3. A woken worker pulls the highest-priority process off the heap, logs `BEGIN`, sleeps for its duration, then logs `END`.
4. On `SHUTDOWN` (or EOF), the queue stops accepting new work and wakes any blocked workers.
5. Workers drain the remaining queue, exit once it's empty, and `main()` joins all five before exiting.

## Outcome
Running a mixed batch of RT and NORMAL processes produces correctly interleaved `BEGIN`/`END` output from up to 5 concurrent workers, always respecting the RT-then-priority-then-arrival ordering, with no process ever dropped during shutdown. Building this taught me how to reason about shared mutable state under concurrent access using raw locks — getting the heap invariant right under `synchronized`, using `wait()`/`notifyAll()` correctly to avoid deadlock and busy-waiting, and designing a clean multi-consumer termination condition.

### How to run

```bash
javac -d out CS490Project2.java
java -cp out com.mycompany.cs490project2.CS490Project2
```

```
PROCESS 2000 RT 1 Alpha
PROCESS 1000 NORMAL 3 Beta
SHUTDOWN
```
