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

// NOTE: this class overuses nested classes, because I find I often need to copy/paste it to proprietary projects.
package io.github.ddimitrov.nuggets;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static io.github.ddimitrov.nuggets.Exceptions.rethrow;
import static java.util.Objects.requireNonNull;

/**
 * <p><span class="badge green">Entry Point</span> Provides utilities for
 * ad-hoc coordination of port usage for distributed systems in tests and
 * lab environments.</p>
 *
 * <p> For example, when you do integration testing for a distributed system,
 * and you want to run a couple of tests in parallel you need to make sure that
 * none of the test environments shares ports and file paths with the others.</p>
 *
 * <p>To achieve that, you can use the {@code Ports} class to allocate the ports
 * and tweak the app configuration in application-specific manner.
 * See the user manual for details and more use-cases.</p>
 *
 * <p>This class has explicit lifecycle as follows:</p>
 * <pre><code>
 *     Ports p = new Ports(...);
 *     // CONFIGURE: call withExporter(...), reservePort(id[, offset]), withPorts(...) as needed
 *     p.freeze(...);
 *     // USE: call getBasePort() and port(id)
 *     p.close();
 *     // DEAD: any method call would result in exception
 * </code></pre>
 */
public class Ports implements AutoCloseable {

    private final List<String> claimedOffsets = Collections.synchronizedList(new ArrayList<>());
    private final Set<String> dynamicOffsets = new HashSet<>();
    private final Set<Exporter> exporters = new HashSet<>();
    private final @NotNull Registrar registrar;
    private final @NotNull Supplier<Integer> dynamicPortFinder;
    private volatile int basePort = -1;
    private volatile boolean closed;

    /**
     * <p>Creates new ports allocator with the specified registrar
     * and default dynamic port allocation strategy.</p>
     *
     * <p>The default port allocation strategy assigns offsets to the
     * dynamic port ID's starting from the base port of the block and
     * skipping all explicitly assigned offsets.</p>
     *
     * @param registrar defines a strategy for allocating a port block.
     */
    public Ports(@NotNull Registrar registrar) {
        this(registrar, null);
    }

    /**
     * <p>Creates new ports allocator with the specified registrar
     * and default dynamic port allocation strategy.</p>
     *
     * @param registrar defines a strategy for allocating a port block.
     * @param dynamicPortFinder defines a strategy for dynamically allocating
     *        ports within a block. If {@code null}, a default strategy assigns
     *        offsets to the dynamic port ID's starting from the {@link #basePort base port}
     *        of the block and skipping all explicitly assigned offsets.
     */
    public Ports(@NotNull Registrar registrar, @Nullable Supplier<Integer> dynamicPortFinder) {
        this.registrar = registrar;
        this.dynamicPortFinder = dynamicPortFinder!=null
                ? dynamicPortFinder
                : () -> {
                    int gapIdx = claimedOffsets.indexOf(null);
                    return gapIdx>=0 ? gapIdx : claimedOffsets.size();
                };
    }

    /**
     * <p>Registers an {@link Exporter} that would be used to publish/validate
     * the allocated ports. Can throw {@link PortVetoException} to reject a
     * port and force the port allocator to use another block</p>
     *
     * <p>If more than one exporter is registered, exporters will be called in
     * their registration order.</p>
     *
     *
     * <p>If the implementation of this interface also implements
     * {@link AutoCloseable}, the {@code close()} method will be called after
     * all offsets have been exported and successfully validated, after
     * the allocation is confirmed successful.</p>
     *
     * @param exporter a callback to publish or validate an allocated port.
     * @return {@code this} instance for chaining.
     *
     * @see Exporter#batching(Consumer)
     */
    public @NotNull Ports withExporter(@NotNull Exporter exporter) {
        if (basePort>=0) throw new IllegalStateException("Already frozen!");
        if (closed) throw new IllegalStateException("Already closed!");
        exporters.add(exporter);
        return this;
    }

    /**
     * Registers a port ID at desired offset within the block.
     * Should be called as many times as needed before {@link #freeze(int)}.
     *
     * @param id the ID of the port we are registering.
     *           Should be unique.
     * @param offset the desired offset for the finally allocated port.
     *               Should be unique.
     *
     * @return {@code this} instance for chaining.
     *
     * @throws IllegalArgumentException if any of the arguments is not unique
     *         within the scope of {@code this} instance.
     * @throws IllegalArgumentException if this port allocator has already been
     *         {@link #freeze(int) frozen} or {@link #close() closed}
     *
     * @see #reservePort(String)
     * @see #withPorts(int, Consumer)
     */
    public @NotNull Ports reservePort(@NotNull String id, int offset) {
        if (basePort>=0) throw new IllegalStateException("Port allocation already finalized!");
        if (closed) throw new IllegalStateException("Already closed!");

        if (Exporter.BASE_PORT_ID.equals(id)) throw new IllegalArgumentException("Port ID can not be empty");
        if (dynamicOffsets.contains(id)) throw new IllegalArgumentException("Port ID '" + id + "' already registered as dynamic (requested " + offset + ")");

        int pos = claimedOffsets.indexOf(id);
        if (pos>=0 && pos!=offset) throw new IllegalArgumentException("Port ID '" + id + "' already registered at offset: " + pos + " (requested " + offset + ")");

        while (offset>=claimedOffsets.size()) claimedOffsets.add(null); // pad the list so we don't get an IOBE
        String old = claimedOffsets.set(offset, requireNonNull(id));

        if (old!=null && !old.equals(id)) {
            claimedOffsets.set(offset, old); // EAFP to avoid TOCTOU issues
            throw new IllegalArgumentException("Clashing port reservations for offset " + offset + ": old='" + old + "', new='" + id + "'");
        }
        return this;
    }

    /**
     * Registers a port ID at automatically chosen offset within the block.
     * Should be called as many times as needed before {@link #freeze(int)}.
     *
     * @param id the ID of the port we are registering. Should be unique.
     *
     * @return {@code this} instance for chaining.
     *
     * @throws IllegalArgumentException if any of the ID is not unique
     *         within the scope of {@code this} instance.
     * @throws IllegalArgumentException if this port allocator has already been
     *         {@link #freeze(int) frozen} or {@link #close() closed}
     *
     * @see #reservePort(String, int)
     * @see #withPorts(int, Consumer)
     */
    public @NotNull Ports reservePort(@NotNull String id) {
        if (basePort>=0) throw new IllegalStateException("Port allocation already finalized!");
        if (closed) throw new IllegalStateException("Already closed!");

        if (Exporter.BASE_PORT_ID.equals(id)) throw new IllegalArgumentException("Port ID can not be empty");

        int pos = claimedOffsets.indexOf(id);
        if (pos>=0) throw new IllegalArgumentException("Port ID '" + id + "' already registered at offset " + pos + " (requested dynamic offset)");

        dynamicOffsets.add(id);
        return this;
    }

    /**
     * Configures port IDs and freezes this allocator in one step. Uses a builder based DSL (see example).
     * Should be called only once, IMPORTANT: internally it calls {@code freeze(basePortHint)}.
     *
     * <pre><code>
     * p.withPorts(5000, register -&gt; {
     *     register.id("foo");
     *     register.id("bar").offset(1);
     *     register.id("baz").offset(2);
     *     register.id("qux");
     * });
     * assert p.getBasePort()==5000; // p is ready to use
     * </code></pre>
     *
     * @param spec a closure configuring the builder (see example above).
     * @param basePortHint the desired base port - the actual base port may
     *                     differ if there is a conflict.
     *
     * @return {@code this} instance in {@link #freeze(int) frozen} state.
     *
     * @throws IllegalArgumentException if any of the ID's and offsets are conflicting.
     * @throws IllegalArgumentException if this port allocator has already been
     *         {@link #freeze(int) frozen} or {@link #close() closed}
     *
     * @see #reservePort(String)
     * @see #reservePort(String, int)
     * @see #freeze(int)
     */
    public @NotNull Ports withPorts(int basePortHint, @NotNull Consumer<SpecIdBuilder> spec) {
        PortsSpecBuilder builder = new PortsSpecBuilder();
        spec.accept(builder);
        builder.flush();
        return freeze(basePortHint);
    }

    /**
     * Uses the {@code registrar} and {@code dynamicPortFinder} to allocate
     * actual ports for each registered {@code portId} and transitions the
     * internal state to USE.
     * @param basePortHint the desired base port - the actual base port may
     *                     differ if there is a conflict.
     * @return this instance, on which now we can call {@link #port(String)}
     *         and {@link #getBasePort()}
     */
    public @NotNull Ports freeze(int basePortHint) {
        if (closed) throw new IllegalStateException("Already closed!");
        if (this.basePort>=0) throw new IllegalStateException("Port range already decided!");

        try {
            int allocatedBasePort = registrar.lock(basePortHint);
            if (allocatedBasePort<=0) {
                throw new IllegalStateException("Failed to reserve a port range!");
            }

            for (String dynamicId : dynamicOffsets) {
                int freePort = dynamicPortFinder.get();

                while (freePort>=claimedOffsets.size()) claimedOffsets.add(null); // pad the list so we don't get an IOBE
                String old = claimedOffsets.set(freePort, dynamicId);
                if (old!=null) {
                    claimedOffsets.set(freePort, old); // undo - better undo than check to avoid TOCTOU errors
                    throw new IllegalArgumentException("The dynamic port finder caused conflict! Clashing port reservations for offset " + freePort + ": old='" + old + "', new='" + dynamicId + "'");
                }
            }

            for (Exporter exporter : exporters) {
                exporter.export(Exporter.BASE_PORT_ID, allocatedBasePort);
                for (int portOffset = 0; portOffset < claimedOffsets.size(); portOffset++) {
                    String id = claimedOffsets.get(portOffset);
                    if (id!=null) {
                        exporter.export(id, allocatedBasePort+portOffset);
                    }
                }
            }
            exporters.stream().filter(it -> it instanceof AutoCloseable)
                     .forEach(it -> rethrow(((AutoCloseable) it)::close));
            this.basePort = allocatedBasePort;
            return this;
        } catch (PortVetoException e) {
            return freeze(e.port + 1);
        }
    }

    /**
     * The lowest port of the allocated block or {@code IllegalStateException}
     * if {@link #freeze(int)} has not been called yet.
     *
     * @return the lowest port of the allocated block.
     */
    public int getBasePort() {
        if (closed) throw new IllegalStateException("Already closed!");
        if (basePort==-1) throw new IllegalStateException("Port range not allocated yet!");
        return basePort;
    }

    /**
     * Look up a port for ID.
     * @param id the port ID, which we shall look up.
     * @return the port corresponding to the {@code id}
     */
    public int port(@NotNull String id) {
        if (basePort<=0) throw new IllegalStateException("Port allocation not finished yet! Perhaps somebody forgot to call freeze()?");
        if (closed) throw new IllegalStateException("Already closed!");

        int offset = claimedOffsets.indexOf(id);
        return offset>=0
                ? basePort + offset
                : rethrow(new NoSuchElementException("No registered port for id: '" + id + "'"));
    }

    /**
     * <p>Releases all resources and transitions the internal state, so that
     * a call to any methods other than {@code close()} will result in
     * {@code IllegalStateException}.</p>
     *
     * <p>This method is idempotent.</p>
     */
    @Override
    public void close() {
        if (basePort>=0) registrar.close();
        basePort = 0;
        closed = true;
    }

    /** Builder interface used to provide constrained, typesafe DSL. */
    public interface SpecOffsetBuilder {
        /**
         * Specify port offset for an ID specified by preceding builder step.
         * @param offset the port offset
         * @see PortsSpecBuilder#id(String)
         */
        void offset(int offset);
    }

    /** Builder interface used to provide constrained, typesafe DSL. */
    public interface SpecIdBuilder {

        /**
         * Specify a port ID. If followed by a call to
         * {@link SpecOffsetBuilder#offset(int)},
         * then this ID would be with explicit offset,
         * otherwise it would be dynamic.
         *
         * @param id the port ID
         * @return {@code this} instance for chaining.
         */
        @NotNull Ports.SpecOffsetBuilder id(@NotNull String id);
    }

    /**
     * Typesafe builder implementation, used by extensions for specifying ports and offsets.
     */
    public class PortsSpecBuilder implements SpecIdBuilder, SpecOffsetBuilder {
        private String id;
        private Integer offset;

        @Override
        public @NotNull Ports.SpecOffsetBuilder id(@NotNull String id) {
            flush();
            this.id = id;
            return this;
        }

        @Override
        public void offset(int offset) {
            this.offset = offset;
            flush();
        }

        /**
         * Needs to be called after finishing building the configuration,
         * or the last configured port ID may be lost.
         */
        public void flush() {
            if (offset != null && id == null) {
                throw new IllegalArgumentException("Ambiguous port offset in spec: " + offset);
            }
            if (id == null) return;
            if (offset == null) {
                reservePort(id);
            } else {
                reservePort(id, offset);
            }
            offset = null;
            id = null;
        }
    }

    /**
     * <p>A hook notified about allocated ports, and optionally validating them.
     * Can be used to update configs, set system properties, validate port
     * availability, etc.</p>
     *
     * <p>If the implementation of this interface also implements
     * {@link AutoCloseable}, the {@code close()} method will be called after
     * all offsets have been exported and successfully validated, after
     * the allocation is confirmed successful.</p>
     */
    @FunctionalInterface
    public interface Exporter {
        /**
         * The base port will be passed with empty-string ID.
         * It is easier to check for empty string, but if you want to be explicit, you may use this constant.
         */
        String BASE_PORT_ID = "";

        /**
         * Called for each allocated port in the range, as well as for the base port.
         * Note that in event of {@code PortVetoException}, you would receive the
         * base port and port ID's more than once - the last value is the actual.
         *
         * @param id the port ID (empty string means base port)
         * @param port the allocated port
         * @throws PortVetoException to indicate that this block is bad and
         *         request reallocation of different block.
         */
        void export(@NotNull String id, int port) throws PortVetoException;

        /**
         * <p>A convenient way to receive the allocated ports (without the base port)
         * in one shot, rather than getting them one by one. Cannot do validation.</p>
         *
         * <p>If you need the base port maping during export, you may use the
         * {@link BatchExporter} directly and pass {@code true} in the
         * constructor.</p>
         *
         * @param publisher a consumer that does something with a map
         *                  mapping portId to port.
         *
         * @return the exporter aggregating the port mappings and
         *         on successful allocation calling the publishing
         *         consumer.
         *
         * @see BatchExporter
         */
        static Exporter batching(Consumer<Map<String, Integer>> publisher) {
           return new BatchExporter(false, publisher);
        }
    }

    /**
     * A strategy for allocating a port block.
     */
    public interface Registrar extends AutoCloseable {
        /**
         * Returns the actual {@code basePort} of the newly allocated block,
         * or throws exception.
         *
         * @param basePortHint the desired {@code basePort} to allocate port
         *        from. If zero, that means that the registrar implementation
         *        is free to choose.
         *
         * @return the base port of the newly allocated block.
         */
        int lock(int basePortHint);

        /**
         * Releases any resources related to this port block.
         * After calling this method, the in the allocated block
         * should not be considered reserved.
         */
        @Override void close();
    }

    /**
     * A simple block-based allocation strategy, trying to bind a port at a
     * fixed offset from {@code portBase} and on failure, retrying again
     * at a new {@code portBase}, one block-size up.
     */
    public static class BlockRegistrar implements Registrar {
        /**
         * The address of the interface we want to use for locking.
         */
        public final @NotNull InetAddress bindAddress;

        /**
         * The desired number of ports in the block (subsequent port offsets
         * should be in the [0..rangeSize-1] range).
         */
        public final int rangeSize;

        /**
         * The offset of the lock port, in the range [-1..rangeSize].
         * Keep in mind that if you choose zero, you need to register
         * the lock port, or it will clash with dynamically registered
         * ports.
         */
        public final int lockOffset;

        /**
         * If {@code true} this registrar will put extra effort to make sure
         * that the allocated {@code basePort==basePortHint+rangeSize*N}
         * (where N is an integer number)
         */
        public boolean alignToBasePortHint;
        private @Nullable ServerSocket locked;

        /**
         *
         * @param bindAddress the address of the interface we want to use for locking.
         * @param rangeSize the desired number of ports in the block
         *        (subsequent port offsets should be in the [0..rangeSize-1] range).
         * @param lockOffset the offset of the lock port, in the range [-1..rangeSize].
         *        See {@link #lockOffset the field} for details.
         */
        public BlockRegistrar(@NotNull InetAddress bindAddress, int rangeSize, int lockOffset) {
            if (lockOffset < -1) throw new IllegalArgumentException("Lock offset " + lockOffset + " should be >= -1");
            if (lockOffset > rangeSize) throw new IllegalArgumentException("Lock offset " + lockOffset + " should be <= " + rangeSize + " (rangeSize)");
            if (rangeSize<=0) throw new IllegalArgumentException("rangeSize: " + rangeSize);
            this.bindAddress = bindAddress;
            this.lockOffset = lockOffset;
            this.rangeSize = rangeSize;
        }

        /**
         * @param basePortHint {@inheritDoc} Negative value forces it to retry one
         *        block up from the negated port.
         */
        @Override
        public synchronized int lock(int basePortHint) {
            if (Math.abs(basePortHint)>0xFFFF) {
                throw new IllegalArgumentException("basePortHint: " + basePortHint);
            }

            int lastBasePort;
            if (locked!=null) {
                lastBasePort = locked.isBound() ? locked.getLocalPort() - lockOffset : -1;
                rethrow(locked::close);
            } else {
                lastBasePort = -1;
            }

            int rangeStep = lockOffset<0 || lockOffset>=rangeSize
                    ? rangeSize+1
                    : rangeSize;

            int basePort;
            if (basePortHint>0) basePort=basePortHint;
            else if (basePortHint<0) basePort = -basePortHint + rangeStep;
            else if (basePortHint==0 && lastBasePort>0) basePort = lastBasePort + rangeStep;
            else {
                throw new IllegalArgumentException("basePortHint: " + basePortHint + ", lastBasePort: " + lastBasePort);
            }

            if (alignToBasePortHint && lastBasePort>0) {
                int misalignment = Math.abs(lastBasePort - basePort) % rangeStep;
                if (misalignment>0) {
                    basePort += rangeStep - misalignment;
                }
            }

            int lastPortInRange = basePort + rangeSize - 1;
            if (basePort<=0 || lastPortInRange>0xFFFF) {
                rethrow(new IOException("basePort: " + basePort));
            }

            int lockPort = basePort+lockOffset;
            if (lockPort<=0 || lockPort>0xFFFF) {
                rethrow(new IOException("lockPort: " + lockPort));
            }
            try {
                locked = new ServerSocket();
                locked.bind(new InetSocketAddress(bindAddress, lockPort));
                return lockPort-lockOffset;
            } catch (IOException e) {
                return lock(-basePort);
            }
        }

        @Override
        public synchronized void close() {
            if (locked !=null) rethrow(locked::close);
        }
    }

    /**
     * <p>A convenient way to receive all port mappings in one shot, rather
     * than getting them one by one. Cannot do validation.</p>
     *
     * <p>This exporter internally aggregates all observed port mappings and
     * calls the publishing consumer once on successful allocation.</p>
     *
     * @see Exporter#batching(Consumer)
     */
    private static class BatchExporter implements Exporter, AutoCloseable {
        private final Map<String, Integer> batched = new HashMap<>();
        private boolean withBasePort;
        private final Consumer<Map<String, Integer>> publisher;

        /**
         * Creates an aggregating exporter, calling {@code publisher} once
         * all mappings are finalized.
         *
         * @param withBasePort if {@code true}, the map passed to the
         *        {@code publisher} includes the {@code basePort} mapped to
         *        an empty string.
         *
         * @param publisher typically a closure that does something with the
         *        ports (i.e. creates config file, calls management APIs, etc.)
         */
        public BatchExporter(boolean withBasePort, Consumer<Map<String, Integer>> publisher) {
            this.withBasePort = withBasePort;
            this.publisher = publisher;
        }

        @Override
        public void export(@NotNull String id, int port) {
            if (!withBasePort && id.isEmpty()) return;
            batched.put(id, port);
        }

        @Override
        public void close() {
            publisher.accept(batched);
        }
    }
}
