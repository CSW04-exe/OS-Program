/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */
package com.mycompany.cs490project2;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

/**
 * CS490 Project 2 — Schedule Processes using a Priority Queue
 * @author Carter
 */
public class CS490Project2 {

    public static void main(String[] args) {
        final ProcessQueue queue = new ProcessQueue();

        // Start 5 consumer threads
        int WORKERS = 5;
        Worker[] workers = new Worker[WORKERS];
        for (int i = 0; i < WORKERS; i++) {
            workers[i] = new Worker("Worker-" + (i + 1), queue);
            workers[i].start();
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            int id = 1;
            long seq = 0;
            boolean shutdown = false;

            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equals("SHUTDOWN")) {
                    shutdown = true;
                    queue.stopNewProcesses();
                    break;
                }

                if (line.startsWith("PROCESS")) {
                    String[] parts = line.split("\\s+", 5);
                    if (parts.length < 5) {
                        System.err.println("Invalid input line: " + line);
                        continue;
                    }

                    try {
                        long ms = Long.parseLong(parts[1]);
                        String type = parts[2];
                        int priority = Integer.parseInt(parts[3]);
                        String name = parts[4];
                        ProcessClass cls = type.equals("RT") ? ProcessClass.REAL_TIME : ProcessClass.NORMAL;

                        queue.addProcess(new SimProcess(id++, name, ms, cls, priority, seq++));
                    } catch (Exception e) {
                        System.err.println("Error parsing: " + line);
                    }
                }
            }

            if (!shutdown) queue.stopNewProcesses();

            for (Worker w : workers) w.join();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

/**
 * Enumeration for process type.
 */
enum ProcessClass { REAL_TIME, NORMAL }

/**
 * Represents a process specification.
 */
class SimProcess {
    int id;
    String name;
    long duration;
    ProcessClass type;
    int priority;
    long sequence;

    SimProcess(int id, String name, long duration, ProcessClass type, int priority, long sequence) {
        this.id = id;
        this.name = name;
        this.duration = duration;
        this.type = type;
        this.priority = priority;
        this.sequence = sequence;
    }
}

/**
 * Custom synchronized priority queue implementation.
 */
class ProcessQueue {
    private final ArrayList<SimProcess> heap = new ArrayList<>();
    private boolean accepting = true;

    private static int compare(SimProcess a, SimProcess b) {
        if (a.type != b.type) return a.type == ProcessClass.REAL_TIME ? -1 : 1;
        if (a.priority != b.priority) return Integer.compare(a.priority, b.priority);
        return Long.compare(a.sequence, b.sequence);
    }

    public synchronized void addProcess(SimProcess p) {
        heap.add(p);
        siftUp(heap.size() - 1);
        notifyAll();
    }

    public synchronized SimProcess take() throws InterruptedException {
        while (heap.isEmpty() && accepting) wait();
        if (!accepting && heap.isEmpty()) return null;

        SimProcess top = heap.get(0);
        int last = heap.size() - 1;
        if (last == 0) heap.remove(last);
        else {
            heap.set(0, heap.remove(last));
            siftDown(0);
        }
        return top;
    }

    public synchronized void stopNewProcesses() {
        accepting = false;
        notifyAll();
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) / 2;
            if (compare(heap.get(i), heap.get(parent)) < 0) {
                swap(i, parent);
                i = parent;
            } else break;
        }
    }

    private void siftDown(int i) {
        int n = heap.size();
        while (true) {
            int left = 2 * i + 1;
            int right = left + 1;
            int smallest = i;
            if (left < n && compare(heap.get(left), heap.get(smallest)) < 0) smallest = left;
            if (right < n && compare(heap.get(right), heap.get(smallest)) < 0) smallest = right;
            if (smallest != i) {
                swap(i, smallest);
                i = smallest;
            } else break;
        }
    }

    private void swap(int i, int j) {
        SimProcess temp = heap.get(i);
        heap.set(i, heap.get(j));
        heap.set(j, temp);
    }
}

/**
 * Worker thread that executes processes.
 */
class Worker extends Thread {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS");
    private final ProcessQueue queue;

    Worker(String name, ProcessQueue q) {
        super(name);
        this.queue = q;
    }

    @Override
    public void run() {
        try {
            while (true) {
                SimProcess p = queue.take();
                if (p == null) break;

                String start = LocalDateTime.now().format(FORMATTER);
                System.out.println("BEGIN\t" + start + "\t" + p.id + "\t" + p.name);

                Thread.sleep(p.duration);

                String end = LocalDateTime.now().format(FORMATTER);
                System.out.println("END\t" + end + "\t" + p.id + "\t" + p.name);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

