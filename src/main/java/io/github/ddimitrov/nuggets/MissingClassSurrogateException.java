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
 * Created by dimit on 2016-10-04.
 */
class MissingClassSurrogateException extends Exception {
    public static volatile @NotNull String indicator="";
    private static final long serialVersionUID = 6202295075232560188L;
    private final @NotNull String originalExceptionClassName;

    public MissingClassSurrogateException(@NotNull String originalExceptionClassName) {
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
