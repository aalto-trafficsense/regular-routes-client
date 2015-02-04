package fi.aalto.trafficsense.regularroutes.util;

import com.google.common.base.Preconditions;

/**
 * Helper class that can be used to verify that operations are executed in a certain thread.
 * <p/>
 * A ThreadGlue is created with a Thread as a parameter, and verify() calls can be later used in operations to check they are executed in the same thread.
 */
public final class ThreadGlue {
    private final Thread thread;

    public ThreadGlue() {
        this(Thread.currentThread());
    }

    public ThreadGlue(Thread thread) {
        this.thread = thread;
    }

    public void verify() {
        verify(Thread.currentThread());
    }

    public void verify(Thread thread) {
        Preconditions.checkState(this.thread == thread, "call from invalid thread");
    }
}