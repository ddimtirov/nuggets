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

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Exception used to indicate that a functional dispatch failed. Typically it would preserve the exceptions from the
 * attempted invocations in its {@link #getSuppressed()} list.
 */
public class DispatchException extends RuntimeException {
    private static final long serialVersionUID = 5373643855785093889L;

    /**
     * Creates an empty instance.
     */
    public DispatchException() {
        this(null, null);
    }

    /**
     * Creates an initialized instance.
     * @param message human-readable summary of the error.
     * @param suppressed the exceptions from failed invocations.
     */
    public DispatchException(@Nullable String message, @Nullable List<Throwable> suppressed) {
        super(message);
        if (suppressed!=null) {
            suppressed.forEach(this::addSuppressed);
        }
    }

    /**
     * An exception that denotes that there was a problem with the invocation result.
     */
    public static class InvalidResult extends RuntimeException {
        private static final long serialVersionUID = 521114059105260507L;
        /**
         * The invalid result object.
         */
        public final Object subject;

        /**
         * Creates an initialized instance.
         *
         * @param subject the invalid result, or a human readable description.
         */
        public InvalidResult(Object subject) {
            super(String.valueOf(subject));
            this.subject = subject;
        }

        @Override
        public synchronized Throwable fillInStackTrace() {
            return this;
        }

        @Override
        public String toString() {
            return "invalid result \"" + subject + "\"";
        }
    }
}
