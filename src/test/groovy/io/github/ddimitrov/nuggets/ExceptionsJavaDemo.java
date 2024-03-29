/*
 *    Copyright 2016 by Dimitar Dimitrov
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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.ddimitrov.nuggets.Exceptions.*;

public class ExceptionsJavaDemo {
    /**
     * <p>Demonstrates how to rethrow a checked exception with minimum fuss,
     * without declaring or wrapping it.</p>
     *
     * <p>To prevent "missing return statement" errors, we can use the result
     * of {@code rethrow()} as return value.</p>
     */
    public static long quietFileSize(Path f) {
        try {
            return Files.size(f); // throws IOException
        } catch (IOException e) {
            return rethrow(e);
        }
    }

    /**
     * <p>Demonstrates how to add extra information to the exception's message without
     * wrapping. This method is a debug interceptor that can wrap a method and note the
     * start and end of operation as well as capturing the context in the message of any
     * exception that gets thrown.</p>
     *
     * <p>Typical usage is when we have code like this:</p>
     * <pre><code>
     * foo.lookup(1, "abc, 123)
     *    .getContext()
     *    .setBoo(fl)
     * </code></pre>
     *
     * <p>To inspect the result of the lookup, we can use {@code traceCall()} as follows:</p>
     * <pre><code>
     * traceCall("lookup(1, "abc, 123)", () -> foo.lookup(1, "abc, 123))
     *    .getContext()
     *    .setBoo(fl)
     * </code></pre>
     *
     * <p>As Java lacks extension methods, if we want to trace the getContxt(), we need to break
     * down the expression like this:</p>
     * <pre><code>
     * Xyz xyz = foo.lookup(1, "abc, 123);
     * traceCall("xyz[1, abc, 123].context", () -> xyz.getContext())
     *    .setBoo(fl)
     * </code></pre>
     *
     * <p>If we could have added {@code traceCall()} a extension method of {@code Object}
     * we would have been able to implement an overload achieving this:</p>
     * <pre><code>
     * foo.lookup(1, "abc, 123)
     *    .traceCall("xyz[1, abc, 123].context", xyz -> xyz.getContext())
     *    .setBoo(fl)
     * </code></pre>
     */
    public static <T> T traceCall(String description, Callable<T> callable) {
        try {
            T result = callable.call();  // throws Exception
            System.out.println("SUCCESS: " + description + " -> " + result);
            return result;
        } catch (Exception e) {
            return rethrow(e, "FAILED " + description + ": ");
        }
    }

    /**
     * Demonstrates how to use throwing methods (i.e. {@link Files#size(Path) from
     * within a non-throwing lambda (i.e. {@link Stream#filter(Predicate)}.
     */
    public static Collection<Path> findLongerThan(Collection<Path> c, int scale) {
        return c.stream()
                // the rethrow decorator reinterprets the IOException thrown by Files.size() as unchecked
                .filter(it -> rethrow(() -> Files.size(it) > scale))
                .collect(Collectors.toList());
    }

    public static Collection<Path> assureAllFresh(Collection<Path> c) {
        c.forEach(it -> rethrowR(() -> Files.createFile(it)));
        return c;
    }

    public static <T> T assureFresh(Path f, T retval) {
        return rethrow(() -> Files.createFile(f), retval);
    }

    public static boolean rethrowing() {
        Object value = new Object();
        CompletableFuture<Object> f = CompletableFuture.completedFuture(value);
        Runnable runnable = rethrowingR(f::get);
        Supplier<Object> supplier = rethrowingC(f::get);
        runnable.run();
        return supplier.get().equals(value);
    }
}
