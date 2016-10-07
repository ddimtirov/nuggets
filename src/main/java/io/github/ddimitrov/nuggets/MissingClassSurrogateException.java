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

/**
 * <p>Exception used by {@link Exceptions#parseStackTrace(CharSequence)} to represent class
 * which is in the trace, but cannot be found on the classpath.</p>
 * <p>The main goal is that this exception prints and behaves as the original class in all
 * aspects possible. This allows us to roundtrip stacktraces (i.e. parse, edit and serialize
 * again). For debug purposes, sometimes it is useful to be able to distinguish missing
 * classes in the string representation - this can be achieved by settind the {@link #indicator}
 * to a non-empty string</p>
 */
public class MissingClassSurrogateException extends Exception {
    /**
     * Prepended to the string representation of the exception
     * (both in the cases of {@code toString()} and {@code printStackTrace()}).
     * Useful in debug scenarios where we want to identify which exceptions are
     * "real" and which are surrogates - see {@code ExceptionsParsingSpec} for details.
     */
    public static volatile @NotNull String indicator="";
    private static final long serialVersionUID = 6202295075232560188L;
    private final @NotNull String originalExceptionClassName;

    /** Limited access constructor used by {@link Exceptions#parseStackTrace(CharSequence)} */
     MissingClassSurrogateException(@NotNull String originalExceptionClassName) {
        this.originalExceptionClassName = originalExceptionClassName;
    }

    @Override
    public synchronized Throwable fillInStackTrace() { return this; }

    @Override
    public String toString() {
        String className = getClass().getName();
        return indicator + super.toString().replace(className, originalExceptionClassName);
    }
}
