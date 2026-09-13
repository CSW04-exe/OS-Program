# CS490 Project 2 — Priority Process Scheduler

## Purpose

This is my second project for CS490 (Operating Systems) at UAH. The assignment
was to build a working simulation of an OS process scheduler in Java, and I
built a multi-threaded priority scheduler that reads process definitions from
standard input, queues them by priority, and hands them off to a pool of
worker threads that "execute" each process by sleeping for its specified
duration. The point of the project is to get hands-on with the two things a
real OS scheduler has to solve at once: deciding *which* process runs next,
and making that decision safely when multiple execution units are pulling
from the same ready queue at the same time.

## Problem and Approach

The specific OS concept here is **priority-based CPU scheduling on a
multiprocessor system**, implemented as a classic **producer-consumer**
problem. In a real OS, a scheduler maintains a ready queue of processes and
one or more CPUs pull the next process to run based on a scheduling policy
(FCFS, priority, round robin, etc.). I modeled that directly:

- The **main thread** acts as the process producer — it reads lines from
  stdin, parses them into `SimProcess` objects, and pushes them onto a shared
  ready queue.
- **Five `Worker` threads** act as five CPUs (consumers) — each one loops
  forever, pulling the highest-priority process off the queue and running it.
- The scheduling **policy** I implemented is priority scheduling with a
  real-time class: any process marked `RT` always runs before any `NORMAL`
  process, regardless of its numeric priority. Within the same class, lower
  priority numbers run first (priority 0 beats priority 5), and if two
  processes tie on both class and priority, the one that arrived first wins
  — so the scheduler is stable and starvation of same-priority processes
  doesn't happen due to reordering.

Input is a small command protocol I designed for driving the simulation:

```
PROCESS <duration_ms> <RT|NORMAL> <priority> <name>
SHUTDOWN
```

`PROCESS` enqueues a new job; `SHUTDOWN` (or EOF on stdin) tells the queue to
stop accepting new work and lets the workers drain whatever is left before
exiting.

## Structure and Methodologies

The file is organized into one public class and four package-private support
classes/enum, each with a single responsibility — closer to a small
MVC-style separation than one giant `main` method:

- **`CS490Project2`** — entry point. Owns the input-parsing loop, spins up
  the worker pool, and coordinates shutdown.
- **`ProcessClass`** (enum) — `REAL_TIME` / `NORMAL`, the two scheduling
  classes.
- **`SimProcess`** — a plain data record for one process: `id`, `name`,
  `duration` (ms), `type`, `priority`, and a monotonically increasing
  `sequence` number used purely as a tie-breaker.
- **`ProcessQueue`** — the core data structure: a **hand-written binary min-heap
  over an `ArrayList<SimProcess>`**, with `siftUp`/`siftDown`/`swap` helpers
  implementing standard heap operations (`O(log n)` insert/remove) rather than
  relying on `java.util.PriorityQueue`. The custom `compare()` method encodes
  the three-level ordering (RT-before-NORMAL, then priority, then sequence).
- **`Worker`** — a `Thread` subclass; each instance repeatedly calls
  `queue.take()`, "executes" the process it gets, and logs start/end
  timestamps.

For concurrency I used Java's intrinsic locking rather than
`java.util.concurrent` collections:

- `addProcess`, `take`, and `stopNewProcesses` are all `synchronized` on the
  `ProcessQueue` instance, so the heap is never mutated by two threads at
  once.
- `take()` uses the classic **wait/notify** pattern: a worker calls
  `wait()` when the queue is empty and still accepting new work, and
  `addProcess`/`stopNewProcesses` call `notifyAll()` to wake every blocked
  worker whenever there's something new to check — either a fresh process or
  a shutdown signal.
- Shutdown is implemented as a **poison-pill-by-flag**: once `accepting` is
  set `false` and the heap is empty, `take()` returns `null` instead of
  blocking forever, which each `Worker` treats as its exit signal.
- Timestamps use `java.time.LocalDateTime` with a millisecond-precision
  `DateTimeFormatter` so the BEGIN/END log lines show real wall-clock timing
  of each simulated execution.

## Process

Step by step, this is what happens when the program runs:

1. `main()` creates one shared `ProcessQueue`.
2. It creates and starts 5 `Worker` threads (`Worker-1` … `Worker-5`), each of
   which immediately calls `queue.take()` and blocks, since the queue starts
   empty.
3. The main thread opens a `BufferedReader` on `System.in` and reads input
   line by line.
4. For each `PROCESS <ms> <RT|NORMAL> <priority> <name>` line, it parses the
   fields, builds a `SimProcess` with an auto-incrementing `id` and
   `sequence`, and calls `queue.addProcess(...)`, which inserts it into the
   heap and calls `notifyAll()`.
5. A woken `Worker` re-checks the queue under its lock, pulls the
   highest-priority `SimProcess` off the heap (root removal + sift-down),
   prints a `BEGIN` line with the current timestamp, process id, and name,
   then calls `Thread.sleep(duration)` to simulate the process actually
   running for that long.
6. When the sleep finishes, the worker prints an `END` line with the new
   timestamp, then loops back to `take()` the next process — so up to 5
   processes can be "running" concurrently at once, mirroring a 5-core
   machine.
7. When the main thread reads a `SHUTDOWN` line (or hits end-of-input), it
   calls `queue.stopNewProcesses()`, which flips `accepting` to `false` and
   calls `notifyAll()` one more time so any worker currently blocked in
   `wait()` re-checks its condition.
8. Each worker drains any processes still left in the heap; once the heap is
   empty and `accepting` is `false`, `take()` returns `null` and the worker's
   loop exits.
9. `main()` calls `join()` on all five workers, so the program only exits
   after every in-flight and queued process has actually finished running.

## Outcome

Running the program with a mixed batch of RT and NORMAL processes produces
interleaved `BEGIN`/`END` log lines from up to 5 workers at once, and the
order processes get picked up in always respects the intended policy: every
`RT` process starts before any `NORMAL` process is taken (as long as an RT
process is waiting), ties are broken by priority number, and same-class/
same-priority processes come out in the order they were submitted. Because
shutdown is coordinated through the same lock/condition-variable mechanism as
normal enqueue/dequeue, no process is ever silently dropped — everything
already queued when `SHUTDOWN` arrives still gets executed before the program
exits.

Building this taught me how to reason about a shared mutable data structure
under concurrent access without ready-made `java.util.concurrent` types: I
had to get the heap invariant right under `synchronized`, use `wait()`/
`notifyAll()` correctly to avoid both deadlock (missed wakeups) and busy-
waiting, and design a clean termination condition for a producer-consumer
pipeline with multiple consumers. More broadly, it's a direct, hands-on model
of what a real OS scheduler does — maintaining a ready queue, applying a
priority policy with a real-time class distinction, and dispatching to
multiple execution units — which connected the lecture material on
scheduling algorithms and synchronization primitives to something I could
actually run and watch behave correctly (or misbehave, before I fixed my
first race condition around the shutdown flag).

### How to run

```bash
javac -d out CS490Project2.java
java -cp out com.mycompany.cs490project2.CS490Project2
```

Then type input like:

```
PROCESS 2000 RT 1 Alpha
PROCESS 1000 NORMAL 3 Beta
PROCESS 500 NORMAL 1 Gamma
SHUTDOWN
```

and watch the `BEGIN`/`END` lines print as the five workers pick up and run
each process.
