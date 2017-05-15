/*
 *    Copyright 2017 by Dimitar Dimitrov
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package io.github.ddimitrov.nuggets;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p><span class="badge green">Entry Point</span> Introspects the
 * running threads and provides access to their runnable delegates,
 * can take a snapshot list of all running threads.
 * Useful for whitebox testing as well as ensuring orderly shutdown.
 * </p>
 * <p>An instance of this class corresponds to a {@link ThreadGroup}
 * instance, providing access to its contained threads.</p>
 */
public class Threads {
    private final ThreadGroup threadGroup;

    /**
     * Creates a new instance, introspecting the threads in {@code threadGroup}
     * @param threadGroup the thread group to introspect.
     *
     * @see #getSiblingThreads()
     * @see #getAllThreads()
     */
    public Threads(@NotNull ThreadGroup threadGroup) {
        this.threadGroup = threadGroup;
    }

    /**
     * Creates an instance introspecting all threads in the JVM.
     *
     * @return an instance introspecting the top-level thread group
     *
     * @see #Threads(ThreadGroup)
     * @see #getSiblingThreads()
     * @see #getParentGroupThreads()
     */
    @Contract(pure = true)
    public static @NotNull Threads getAllThreads() {
        ThreadGroup threadGroup = Thread.currentThread().getThreadGroup();
        while (threadGroup.getParent()!=null) threadGroup = threadGroup.getParent();
        return new Threads(threadGroup);
    }

    /**
     * Creates an instance introspecting all threads in the same
     * thread group with the current thread.
     *
     * @return an instance introspecting the current thread group
     *
     * @see #Threads(ThreadGroup)
     * @see #getAllThreads()
     * @see #getParentGroupThreads()
     */
    @Contract(pure = true)
    public static @NotNull Threads getSiblingThreads() {
        return new Threads(Thread.currentThread().getThreadGroup());
    }

    /**
     * Creates an instance introspecting all threads in the parent
     * thread group of the thread group wrapped by this instance.
     *
     * @return an instance introspecting the parent thread group
     *         or {@code null} if this is the top group
     *
     * @see #Threads(ThreadGroup)
     * @see #getAllThreads()
     * @see #getSiblingThreads()
     */
    @Contract(pure = true)
    public @Nullable Threads getParentGroupThreads() {
        ThreadGroup parent = threadGroup.getParent();
        return parent==null ? null : new Threads(parent);
    }

    /**
     * <p>Returns a best effort list of all thread groups contained by
     * the wrapped thread group.</p>
     *
     * <p>Note that as thread groups are created and die asynchronously,
     * there is a natural race condition. We guarantee that this method
     * will not fail and each element of the data has been valid at some
     * point in time, but possibly not at the same point in time.</p>
     *
     * @param recurse {@code true} to return thread groups contained in
     *                child thread groups, {@code false} to return only
     *                direct children
     * @return list of thread groups.
     *
     * @see #Threads(ThreadGroup)
     * @see #getParentGroupThreads()
     */
    public @NotNull List<ThreadGroup> containedGroups(boolean recurse) {
        ThreadGroup[] all = null;
        int actual = 0;
        while(all==null || all.length!=actual) {
            all = new ThreadGroup[threadGroup.activeGroupCount()];
            actual = threadGroup.enumerate(all);
        }

        List<ThreadGroup> groups = new ArrayList<>(Arrays.asList(all));
        groups.removeIf(Objects::isNull);
        if (!recurse) groups.removeIf(it -> {
            ThreadGroup parentGroup = it.getParent();
            return parentGroup==null || !parentGroup.equals(threadGroup);
        });
        return groups;
    }

    /**
     * <p>Returns a best effort list of all threads contained by the
     * wrapped thread group.</p>
     *
     * <p>Note that as threads are created and die asynchronously, there
     * is a natural race condition. We guarantee that this method will
     * not fail and each element of the data has been valid at some
     * point in time, but possibly not at the same point in time.</p>
     *
     * @param recurse {@code true} to return threads contained in child
     *                thread groups, {@code false} to return only threads
     *                having this thread-group as a parent.
     * @return list of thread groups.
     *
     * @see #getAll()
     */
    public @NotNull List<Thread> containedThreads(boolean recurse) {
        Thread[] all = null;
        int actual = 0;
        while(all==null || all.length!=actual) {
            all = new Thread[threadGroup.activeCount()];
            actual = threadGroup.enumerate(all);
        }

        List<Thread> threads = new ArrayList<>(Arrays.asList(all));
        threads.removeIf(Objects::isNull);
        if (!recurse) threads.removeIf(it -> {
            ThreadGroup parentGroup = it.getThreadGroup();
            return parentGroup==null || !parentGroup.equals(threadGroup);
        });
        return threads;
    }

    /**
     * <p>Returns a best effort list of all runnables contained by the
     * wrapped thread group.</p>
     *
     * <p>Note that as threads are created and die asynchronously, there
     * is a natural race condition. We guarantee that this method will
     * not fail and each element of the data has been valid at some
     * point in time, but possibly not at the same point in time.</p>
     *
     * @return list of thread runnables.
     *
     * @see #containedThreads(boolean)
     * @see #getByName()
     * @see #getWithUniqueName()
     */
    public @NotNull List<Runnable> getAll() {
        return containedThreads(true).stream().map(Threads::extractRunnable).collect(Collectors.toList());
    }

    /**
     * <p>Returns a best effort multi-map of thread-names to runnables
     * for all threads contained by the wrapped thread group. If there
     * are more than one threads with the same name, their runnables are
     * put in a list.</p>
     *
     * <p>Example:</p>
     * <pre><code>
     *     threads.getByName().get("SEDA-PROCESSOR").forEach(SedaProcessor::stop);
     *     assert threads.getByName().get("ComponentX").get(0).getFoo() == 42;
     * </code></pre>
     *
     * <p>Note that as threads are created and die asynchronously, there
     * is a natural race condition. We guarantee that this method will
     * not fail and each element of the data has been valid at some
     * point in time, but possibly not at the same point in time.</p>
     *
     * @return list of thread runnables.
     *
     * @see #containedThreads(boolean)
     * @see #getAll()
     * @see #getWithUniqueName()
     */
    public @NotNull Map<String, List<Runnable>> getByName() {
        return containedThreads(true).stream().collect(Collectors.groupingBy(
                Thread::getName,
                Collectors.mapping(Threads::extractRunnable, Collectors.toList())
        ));
    }

    /**
     * <p>Returns a best effort map of thread-names to runnables for all
     * uniquely named threads contained by the wrapped thread group. If
     * there are more than one threads with the same name, their runnables
     * are ignored.</p>
     *
     * <p>Example:</p>
     * <pre><code>
     *     assert threads.getByName().get("ComponentX").getFoo() == 42;
     * </code></pre>
     *
     * <p>Note that as threads are created and die asynchronously, there
     * is a natural race condition. We guarantee that this method will
     * not fail and each element of the data has been valid at some
     * point in time, but possibly not at the same point in time.</p>
     *
     * @return list of thread runnables.
     *
     * @see #containedThreads(boolean)
     * @see #getAll()
     * @see #getByName()
     */
    public @NotNull Map<String, Runnable> getWithUniqueName() {
        Map<String, Runnable> runnables = new HashMap<>();
        getByName().forEach((name, runnablesForName) -> {
            if (runnablesForName.size()!=1) return; // ambiguous or missing
            runnables.put(name, runnablesForName.get(0));
        });
        return runnables;
    }

    /**
     * Extracts the runnable that the thread was created with, or
     * returns the thread itself if it was created with {@code null}
     * runnable.
     *
     * @param thread the thread we are interested in
     * @return the runnable whose {@code run()} method implements the
     *         logic for this thread.
     */
    @Contract(pure = true)
    public static @NotNull Runnable extractRunnable(@NotNull Thread thread) {
        Runnable threadTarget = Extractors.peekField(thread,  Thread.class, "target", Runnable.class);
        return threadTarget == null ? thread : threadTarget;
    }
}
