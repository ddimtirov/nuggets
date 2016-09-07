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

/**
 * Functional interface for a void, parameterless lambda.
 * Same as {@link Runnable}, but can throw exceptions.
 *
 * @see Exceptions#rethrow(ThrowingRunnable, Object)
 * @see Exceptions#rethrow(ThrowingRunnable)
 */
@FunctionalInterface
public interface ThrowingRunnable {
    /**
     * Execute a parameter-less no-results action.
     * @throws Throwable if the action failed.
     */
    void run() throws Throwable;
}
