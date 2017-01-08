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

import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.*;

/**
 * All methods are threadsafe.
 */
public final class Functions {
    private Functions() {}

    public static final String VALID_NAME_PATTERN = "\\S(?:.*\\S)?";
    private static final java.util.regex.Pattern validName = java.util.regex.Pattern.compile(VALID_NAME_PATTERN);
    private static void checkName(@NotNull String name) {
        if (!validName.matcher(name).matches()) {
            throw new IllegalArgumentException("Name should be non-empty and cannot start or end with whitespace!");
        }
    }

    /**
     * Decorates a supplier with a {@code toString()}, so that it can be printed and logged.
     * The resulting supplier behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the supplier to decorate.
     * @param <T> the generic type of the decorated supplier return value.
     * @return a supplier that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T> @NotNull Supplier<T> sup(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull Supplier<T> delegate
    ) {
        checkName(name);
        class NamedSupplier implements Supplier<T> {
            @Override public T get() { return delegate.get(); }
            @Override public String toString() { return name; }
        }
        return new NamedSupplier();
    }

    /**
     * Decorates a consumer with a {@code toString()}, so that it can be printed and logged.
     * The resulting consumer behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the consumer to decorate.
     * @param <T> the generic type of the decorated consumer parameter.
     * @return a consumer that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T> @NotNull Consumer<T> con(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull Consumer<T> delegate
    ) {
        checkName(name);
        class NamedConsumer implements Consumer<T> {
            @Override public void accept(T t) { delegate.accept(t); }
            @Override public String toString() { return name; }
        }
        return new NamedConsumer();
    }

    /**
     * Decorates a consumer with a {@code toString()}, so that it can be printed and logged.
     * The resulting consumer behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the consumer to decorate.
     * @param <T1> the generic type of the decorated consumer left parameter.
     * @param <T2> the generic type of the decorated consumer right parameter.
     * @return a consumer that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T1, T2> @NotNull BiConsumer<T1, T2> con(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull BiConsumer<T1, T2> delegate
    ) {
        checkName(name);
        class NamedBiConsumer implements BiConsumer<T1, T2> {
            @Override public void accept(T1 t1, T2 t2) { delegate.accept(t1, t2); }
            @Override public String toString() { return name; }
        }
        return new NamedBiConsumer();
    }

    /**
     * Decorates a predicate with a {@code toString()}, so that it can be printed and logged.
     * The resulting predicate behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the predicate to decorate.
     * @param <T> the generic type of the decorated predicate parameter.
     * @return a predicate that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T> @NotNull Predicate<T> pre(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull Predicate<T> delegate
    ) {
        checkName(name);
        class NamedPredicate implements Predicate<T> {
            @Override public boolean test(T t) { return delegate.test(t); }
            @Override public String toString() { return name; }
        }
        return new NamedPredicate();
    }

    /**
     * Decorates a predicate with a {@code toString()}, so that it can be printed and logged.
     * The resulting predicate behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the predicate to decorate.
     * @param <T1> the generic type of the decorated predicate left parameter.
     * @param <T2> the generic type of the decorated predicate right parameter.
     * @return a predicate that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T1, T2> @NotNull BiPredicate<T1, T2> pre(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull BiPredicate<T1, T2> delegate
    ) {
        checkName(name);
        class NamedBiPredicate implements BiPredicate<T1, T2> {
            @Override public boolean test(T1 t1, T2 t2) { return delegate.test(t1, t2); }
            @Override public String toString() { return name; }
        }
        return new NamedBiPredicate();
    }

    /**
     * Decorates a function with a {@code toString()}, so that it can be printed and logged.
     * The resulting function behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the function to decorate.
     * @param <T> the generic type of the decorated function parameter.
     * @param <R> the generic type of the decorated return value.
     * @return a function that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T, R> @NotNull Function<T, R> fun(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull Function<T, R> delegate
    ) {
        checkName(name);
        class NamedFunction implements Function<T, R> {
            @Override public R apply(T t) { return delegate.apply(t); }
            @Override public String toString() { return name; }
        }
        return new NamedFunction();
    }

    /**
     * Decorates a function with a {@code toString()}, so that it can be printed and logged.
     * The resulting function behaves in any other way like the {@code delegate}.
     *
     * @param name the string to be returned on {@code toString()}.
     *             Needs to start and end with a letter, can not be empty.
     * @param delegate the function to decorate.
     * @param <T1> the generic type of the decorated function left parameter.
     * @param <T2> the generic type of the decorated function right parameter.
     * @param <R> the generic type of the decorated return value.
     * @return a function that behaves like {@code delegate}, but when printed prints as {@code name}
     */
    public static <T1, T2, R> @NotNull BiFunction<T1, T2, R> fun(
            @NotNull @Pattern(VALID_NAME_PATTERN) String name,
            @NotNull BiFunction<T1, T2, R> delegate
    ) {
        checkName(name);
        class NamedBiFunction implements BiFunction<T1, T2, R> {
            @Override public R apply(T1 t1, T2 t2) { return delegate.apply(t1, t2); }
            @Override public String toString() { return name; }
        }
        return new NamedBiFunction();
    }
        /**
         * <p>Tries multiple functions in the specified order and returns the first result that passes a predicate,
         * optionally ignoring exceptions. Throws {@link DispatchException} if none of the fallback functions returned a
         * good result, wrapping all fallback failures as {@link Throwable#getSuppressed() suppressed}.
         * If {@code failFast} was specified and any of the fallbacks throws an exception, it is immediately rethrown.</p>
         *
         * <p><em><strong>Design notes:</strong> Ideally we would like this combinator to be a macro method, applicable to
         * any functional interface that returns a value (non-void). We have decided to dispatch {@link Function Functions},
         * as these show up as the most common functional interfaces in the wild.
         *
         * Alternative design is to dispatch {@link Supplier Suppliers}, and force the caller to tunnel the functional
         * parameters using closures, but that makes the more common case uglier.
         *
         * Going to the other extreme, we could have dispatched {@link BiFunction BiFunctions}, and accommodated the
         * {@code Function} use-case by requiring the client to adapt the result using partial application.</em></p>
         *
         * @param failFast if {@code true}, as soon as a function throws exception, we'll rethrow it and skip trying the
         *                 remaining fallback functions. If {@code false}, when a function throws an exception we just
         *                 continue with the remaining fallback functions. If we try all exceptions and didn't find a result
         *                 all these exceptions will be rethrown as {@link Throwable#suppressedExceptions}
         *
         * @param approveResult a predicate that decides whether we shall return the result of a function or try the next
         *                      one. If all functions are tried and none matched the predicate, this method will throw
         *                      {@link DispatchException}
         *
         * @param functions a functions to be tried one after another until one returns a result that matches the
         *                  {@code approveResult} predicate. Exceptions are swallowed if {@code failFast} is {@code true}
         *                  and rethrown if {@code false}.
         *
         * @param <T> the function argument generic type
         * @param <R> the function return generic type
         *
         * @return a function that dispatches through the {@code fallbacks}, based on the strategy determined by
         *         {@code failFast} and {@code approveResult}
         *
         * @throws DispatchException if {@code failFast==false} or none of the results matched the predicate.
         *         If {@code failFast==true} we would rethrow immediately if any of the {@code fallbacks} throws exception.
         */
    @SafeVarargs
    public static <T, R> @NotNull Function<T, R> fallback(boolean failFast, @Nullable Predicate<@Nullable R> approveResult, @NotNull Function<@Nullable T, @Nullable R>... functions) {
        if (functions.length==0) throw new IllegalArgumentException("Need to provide at least one function!");

        if ((failFast || functions.length==1) && approveResult==null) {
            // same as just rinning the first function: if it succeeds - return the result; if it fails - rethrow
            return functions[0];
        }

        return t -> {
            List<Throwable> suppressed = new ArrayList<>(functions.length);
            for (Function<T, R> function : functions) {
                try {
                    R result = function.apply(t);
                    if (approveResult != null && !approveResult.test(result)) {
                        suppressed.add(new DispatchException.InvalidResult(result));
                    } else {
                        return result;
                    }
                } catch (Throwable e) {
                    if (failFast) {
                        return Exceptions.rethrow(e);
                    }
                    suppressed.add(e);
                }
            }

            String verb = approveResult == null
                    ? "successfully run any of"
                    : failFast ? "approve result" : "approve successful result of";

            return Exceptions.rethrow(new DispatchException("failed to " + verb + " " + functions.length + " fallback fuctions!", suppressed));
        };
    }
}
