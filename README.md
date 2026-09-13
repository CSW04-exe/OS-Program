# OS-Program — CS490 Project 2: Priority-Based Process Scheduler

A Java simulation of an operating system process scheduler, built as Project 2 for CS490 (Operating Systems). It models how a real OS dispatcher might pick the next process to run when several processes of different priorities and classes are competing for CPU time, using a hand-built priority queue and a small pool of concurrent worker threads.

## Purpose

The goal of this project was to demonstrate, in working code, one of the core mechanisms of an operating system kernel: process scheduling. Rather than just describing how a priority-based scheduler works on paper, the program actually implements one — accepting a stream of process definitions, ordering them by class and priority, and dispatching them to independent worker "CPUs" for execution.

This matters because scheduling is one of the few OS concepts that is genuinely hard to appreciate without building it. It's easy to say "real-time tasks preempt normal tasks" or "ties are broken by arrival order," but writing the comparator and the underlying heap forces you to confront exactly what those rules mean in code, and what happens when multiple threads try to read and modify the same queue at once. The project exists to turn a scheduling algorithm from a textbook diagram into something that can be run, fed input, and observed.

## Problem and approach

The assignment was to simulate a scheduler that manages incoming processes and executes them according to priority rules rather than simple arrival order (a step up from a plain FIFO/round-robin scheduler). Concretely, the program needed to:

- Accept process definitions at runtime (type, priority, name, and simulated runtime).
- Guarantee that **real-time** processes always run before **normal** processes, regardless of when they arrived.
- Within the same class, respect numeric priority, and break remaining ties by arrival order (FIFO) so the scheduler is deterministic and fair.
- Actually execute multiple processes concurrently, rather than just sorting a list and printing it.
- Support a clean shutdown command so the simulation can end gracefully instead of blocking forever.

The approach taken was to treat this as a classic **producer–consumer** problem: a single main thread acts as the producer, reading `PROCESS` commands from standard input and pushing them into a shared queue; a fixed pool of worker threads acts as the consumers, each pulling the highest-priority process off the queue and "running" it by sleeping for its specified duration. Ordering is enforced entirely inside the queue's own comparator, so the workers themselves stay simple — they just ask the queue for whatever should run next.

## Structure and methodologies

The whole program lives in a single file, `CS490Project2.java` (originally generated as a NetBeans project under the package `com.mycompany.cs490project2`), but it is organized into several distinct classes that mirror the pieces of a real scheduler:

- **`CS490Project2` (main/driver)** — parses commands from stdin (`PROCESS <durationMs> <RT|NORMAL> <priority> <name>` and `SHUTDOWN`), assigns each process a monotonically increasing id and sequence number, and hands it to the queue.
- **`SimProcess`** — a plain data object representing a process: id, name, duration, class (`REAL_TIME` or `NORMAL`), priority, and an insertion sequence number used for FIFO tie-breaking.
- **`ProcessClass`** — a two-value enum (`REAL_TIME`, `NORMAL`) that encodes the scheduling class of a process.
- **`ProcessQueue`** — the heart of the project: a **custom binary min-heap** built directly on top of an `ArrayList<SimProcess>` (no `java.util.PriorityQueue` — the heap, `siftUp`, and `siftDown` operations are hand-written). Its comparator enforces the three-level ordering rule: real-time before normal, then lower numeric priority value first, then earlier sequence number first.
- **`Worker`** — a `Thread` subclass representing a CPU/consumer. Each worker loops, blocking on the queue when it's empty, and when a process is available, prints a `BEGIN` line with a millisecond-precision timestamp, sleeps for the process's duration to simulate execution, then prints an `END` line.

Key techniques and library features actually used:

- **Concurrency primitives**: `synchronized` methods and `wait()`/`notifyAll()` on `ProcessQueue` coordinate the producer (main thread) and five consumers (`Worker` threads) without any external concurrency library — just core `java.lang.Object` monitor methods.
- **Custom data structure**: a manually implemented binary heap (array-backed, index arithmetic for parent/child, `siftUp`/`siftDown`, `swap`) rather than relying on a built-in collection, so the scheduling comparator could be fully custom (class, then priority, then arrival order).
- **Graceful shutdown protocol**: an `accepting` flag plus `stopNewProcesses()` lets the queue tell blocked workers to exit (`take()` returns `null`) once no more work will arrive, avoiding threads stuck forever in `wait()`.
- **`java.time`**: `LocalDateTime` and `DateTimeFormatter` for millisecond-accurate `BEGIN`/`END` timestamps in each worker's output.
- **Standard I/O parsing**: `BufferedReader`/`InputStreamReader` over `System.in`, with `String.split` and defensive `try/catch` around malformed input lines.
- No external dependencies or build framework beyond the JDK — this is a single-file program originally developed and packaged in NetBeans (visible from the license-template header comment and the `com.mycompany.cs490project2` package name).

## Process

The commit history is compact (an initial commit, a README revision, and a final upload, all pushed within a couple of minutes of each other), which reflects that the file was developed and tested locally in NetBeans and pushed to GitHub as a finished unit rather than committed incrementally — a common pattern for a single-file class assignment. Working backward from the structure of the code, the build order reads as:

1. **Model the problem first.** Before any concurrency, the process representation had to exist: `SimProcess` and the `ProcessClass` enum, capturing exactly the fields the scheduling rule needs (class, priority, sequence) plus the fields needed to report on execution (id, name, duration).
2. **Get the ordering rule right in isolation.** The `compare` method in `ProcessQueue` encodes the three-tier rule (real-time beats normal; then priority; then sequence) as a single static function, which made it possible to reason about and adjust the ordering logic independently of the heap mechanics.
3. **Build the heap around that comparator.** `siftUp`/`siftDown`/`swap` on an `ArrayList` back the priority queue, giving O(log n) insert and removal driven entirely by `compare`.
4. **Add concurrency safety.** Once the heap worked as a plain (single-threaded) data structure, `addProcess` and `take` were wrapped in `synchronized` with `wait()`/`notifyAll()` so multiple threads could safely share one queue — the classic bounded/unbounded producer-consumer pattern, here unbounded on the producer side.
5. **Introduce the workers.** `Worker extends Thread` was layered on top, with each worker just calling `queue.take()` in a loop — deliberately dumb, so all the scheduling intelligence stays in the queue rather than being duplicated per-thread.
6. **Wire up the driver and shutdown path.** The `main` method's stdin-parsing loop, the `PROCESS`/`SHUTDOWN` command grammar, and the `accepting` flag for a clean stop were the last pieces, letting the whole thing run as an interactive or scripted simulation (e.g., piping a file of `PROCESS` lines followed by `SHUTDOWN` into the program) and letting all worker threads `join()` before the program exits.
7. **Document it.** The README was written and then expanded (per the "Enhance README with detailed project description" commit) once the implementation was working, to explain the scheduling behavior to a reader who hasn't seen the code.

## Outcome

The finished program is a working, multi-threaded priority scheduler that correctly and deterministically orders real-time processes ahead of normal ones, resolves same-class ties by numeric priority and then by arrival order, and executes up to 5 processes concurrently while logging precise start/end timestamps for each — enough to verify, from the printed `BEGIN`/`END` output alone, that the scheduling policy is being honored under concurrent execution.

Building it required going beyond textbook description and actually reasoning through several classic OS/concurrency concerns end-to-end:

- Implementing a **priority queue from scratch** (heap indexing, sift operations) instead of relying on a library collection, which required getting comfortable with how a binary heap maintains its ordering invariant under both insertion and removal.
- Using **low-level synchronization primitives** (`synchronized`, `wait()`, `notifyAll()`) correctly to coordinate a producer thread and multiple consumer threads sharing mutable state, including handling the shutdown edge case where a worker is blocked in `wait()` and needs to be woken up and told there is no more work rather than left hanging.
- Designing a **multi-level sort/comparator** that encodes real scheduling policy (class, then priority, then FIFO) as a single, testable function, which is directly analogous to how real OS schedulers combine multiple criteria into one dispatch decision.
- Practicing **defensive input parsing** and a simple text-based command protocol, so the simulation can be driven deterministically from scripted input for testing.

Overall, the project demonstrates the ability to translate an OS scheduling concept into a correct, concurrent, testable implementation without leaning on built-in shortcuts (like `java.util.PriorityQueue` or higher-level concurrency utilities) — and it reinforced, very concretely, why real operating systems put so much care into how their run queues are synchronized: get the locking or the wake-up logic even slightly wrong, and a worker thread simply stops making progress instead of throwing an obvious error.
