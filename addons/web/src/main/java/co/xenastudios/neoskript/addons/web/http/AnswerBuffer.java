/*
 * Copyright (c) 2026 Xena Studios
 * SPDX-License-Identifier: GPL-3.0-only
 */
package co.xenastudios.neoskript.addons.web.http;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One answer's bytes, in a single array that grows as they arrive, never past the answer limit, and charged to the
 * engine's budget for answers in flight as it grows: one script's flood of big answers can't take more than the budget
 * between them. What it has is released from the budget once the answer is handed over or given up.
 */
final class AnswerBuffer {

    /** The memory all answers in flight may take between them. */
    static final class Budget {
        private final long max;
        private final AtomicLong used = new AtomicLong();

        Budget(long max) {
            this.max = max;
        }

        boolean reserve(long bytes) {
            while (true) {
                long now = used.get();
                if (now + bytes > max) {
                    return false;
                }
                if (used.compareAndSet(now, now + bytes)) {
                    return true;
                }
            }
        }

        void release(long bytes) {
            used.addAndGet(-bytes);
        }

        long used() {
            return used.get();
        }

        long max() {
            return max;
        }
    }

    private final long max;
    private final Budget budget;
    private final String what;
    private byte[] data = new byte[0];
    private int size;
    private long reserved;
    private boolean released;

    /**
     * A buffer.
     *
     * @param max the most it may hold
     * @param budget what it's charged to
     * @param what the answer, for errors
     * @param expected how big the answer says it is, or -1
     */
    AnswerBuffer(long max, Budget budget, String what, long expected) {
        this.max = max;
        this.budget = budget;
        this.what = what;
        if (expected > max) {
            throw HttpEngine.tooBig(max, what);
        }
        if (expected > 0) {
            grow((int) expected);
        }
    }

    /** Adds bytes, growing to hold them, or throws if that would pass the limit or the budget. */
    synchronized void write(byte[] bytes, int offset, int length) {
        if (released) {
            return;
        }
        long needed = (long) size + length;
        if (needed > max) {
            throw HttpEngine.tooBig(max, what);
        }
        if (needed > data.length) {
            grow((int) Math.min(max, Math.max(needed, Math.max(8192, (long) data.length * 2))));
        }
        System.arraycopy(bytes, offset, data, size, length);
        size += length;
    }

    /** Adds what's left in a byte buffer. */
    synchronized void write(java.nio.ByteBuffer buffer) {
        if (released) {
            return;
        }
        int length = buffer.remaining();
        long needed = (long) size + length;
        if (needed > max) {
            throw HttpEngine.tooBig(max, what);
        }
        if (needed > data.length) {
            grow((int) Math.min(max, Math.max(needed, Math.max(8192, (long) data.length * 2))));
        }
        buffer.get(data, size, length);
        size += length;
    }

    private void grow(int capacity) {
        long more = capacity - (long) data.length;
        if (!budget.reserve(more)) {
            throw new WebException("The answer from " + what + " doesn't fit in what's left of the "
                    + budget.max() / (1024 * 1024) + " MB all answers in flight may take (max-response-memory-megabytes"
                    + " in NeoSkriptWeb's config.yml), so it was cut off");
        }
        reserved += more;
        data = Arrays.copyOf(data, capacity);
    }

    synchronized byte[] array() {
        return data;
    }

    synchronized int size() {
        return size;
    }

    /** Releases it from the budget; the bytes stay with whoever holds them. Safe to call more than once. */
    synchronized void release() {
        if (!released) {
            released = true;
            budget.release(reserved);
            reserved = 0;
        }
    }
}
