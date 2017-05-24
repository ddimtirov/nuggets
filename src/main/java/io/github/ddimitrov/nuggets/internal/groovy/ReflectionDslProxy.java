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

package io.github.ddimitrov.nuggets.internal.groovy;

import groovy.lang.GroovyInterceptable;
import groovy.lang.GroovyObjectSupport;
import io.github.ddimitrov.nuggets.ReflectionProxy;
import org.intellij.lang.annotations.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * A more idiomatic Groovy DSL to manipulate objects by reflection.
 */
public class ReflectionDslProxy extends GroovyObjectSupport implements GroovyInterceptable {
    private final ReflectionProxy delegate;

    ReflectionDslProxy(ReflectionProxy delegate) {
        this.delegate = delegate;
    }

    @Override
    public Object invokeMethod(@NotNull @Identifier String name, Object args) {
        if (args instanceof Object[]) {
            Object[] ar = (Object[]) args;
            boolean invokeDirect;
            switch (name) {
                case "asType"  : invokeDirect = ar.length == 1 && ar[0] instanceof Class; break;
                case "getAt"   : invokeDirect = ar.length == 1 && ar[0] instanceof Class; break;
                case "equals"  : invokeDirect = ar.length == 1; break;
                case "hashCode": invokeDirect = ar.length == 0; break;
                case "toString": invokeDirect = ar.length == 0; break;
                default: invokeDirect = false;
            }
            if (!invokeDirect) {
                return new ReflectionDslProxy(delegate.invoke(name, ar));
            }
        }
        return super.invokeMethod(name, args);
    }

    /**
     * Gets the value of a field and wraps it in reflection proxy
     * @param propertyName the field name
     * @return the {@link ReflectionProxy#wrap(Object) wrapped} field value
     */
    @Override
    public @NotNull ReflectionDslProxy getProperty(@NotNull @Identifier String propertyName) {
        return new ReflectionDslProxy(delegate.get(propertyName));
    }

    /**
     * Sets the value of a field
     * @param propertyName the name of the field to set
     * @param newValue the value to set
     */
    @Override
    public void setProperty(@NotNull @Identifier String propertyName, Object newValue) {
        delegate.set(propertyName, newValue);
    }

    /**
     * Adapts the reflection proxy to use resolution type (used to resolve shadowed members).
     * @param resolutionType the desired type to use for member resoolution
     * @return an adapted reflection proxy instance
     */
    public @NotNull ReflectionDslProxy getAt(Class<?> resolutionType) {
        return new ReflectionDslProxy(delegate.resolvingAtType(resolutionType));
    }

    /**
     * {@link ReflectionProxy#unwrap(Class) Unwraps} an reflection proxy
     * @param type the desired result type
     * @param <T> generic parameter for type inference
     * @return the unwrapped reflection proxy value
     */
    public <T> T asType(Class<T> type) {
        return delegate.unwrap(type);
    }

    /**
     * The DSL reflection proxy returns the unwrapped to string without any
     * indication of decoration, so it can easily be concatenated in string
     * templates without unwrapping.
     * @return the unwrapped to string
     */
    @Override
    public String toString() {
        return delegate.unwrap().toString();
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ReflectionDslProxy
                ? delegate.equals(((ReflectionDslProxy) o).delegate)
                : delegate.equals(o);
    }
}
