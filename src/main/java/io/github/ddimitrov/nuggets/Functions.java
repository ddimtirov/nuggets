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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * All methods are threadsafe.
 */
public final class Functions {
    private Functions() {}

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
    public static <T, R> @NotNull Function<T, R> fallback(boolean failFast, @Nullable Predicate<R> approveResult, @NotNull Function<T, R>... functions) {
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
