package com.lin.spring.bean.utils;

public class EvenOddPrinter {

    public static void main(String[] args) {
        NumberPrinter printer = new NumberPrinter(10); // Up to 10

        Thread oddThread = new Thread(() -> printer.printOdd(), "Odd-Thread");
        Thread evenThread = new Thread(() -> printer.printEven(), "Even-Thread");

        oddThread.start();
        evenThread.start();
    }
}

// Shared class for controlling print logic
class NumberPrinter {
    private final int max;
    private int number = 1;
    private final Object lock = new Object();  // Common lock object

    public NumberPrinter(int max) {
        this.max = max;
    }

    // Method for printing odd numbers
    public void printOdd() {
        while (number <= max) {
            synchronized (lock) {
                // Only print if number is odd
                while (number % 2 == 0) {
                    waitSafely();
                }
                if (number <= max) {
                    System.out.println(Thread.currentThread().getName() + " - Odd: " + number++);
                }
                lock.notifyAll(); // Wake up waiting threads
            }
        }
    }

    // Method for printing even numbers
    public void printEven() {
        while (number <= max) {
            synchronized (lock) {
                // Only print if number is even
                while (number % 2 != 0) {
                    waitSafely();
                }
                if (number <= max) {
                    System.out.println(Thread.currentThread().getName() + " - Even: " + number++);
                }
                lock.notifyAll(); // Wake up waiting threads
            }
        }
    }

    // Safe wait method to avoid try-catch clutter
    private void waitSafely() {
        try {
            lock.wait();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Best practice to re-interrupt
        }
    }
}
