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

import org.intellij.lang.annotations.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p><span class="badge green">Entry Point</span> Provides a concise and expressive way
 * for performing multiple reflective access operations, building on the {@code Extractors}
 * class. As a reflection proxy has a final association with a single object, its API provides
 * the (currently unused) opportunity for optimizations, such as memoizing methods annotated
 * with {@code Contract(@pure=true)} and caching accessors and method/var handles.</p>
 *
 */
public class ReflectionProxy {
    private final @NotNull Object delegate;
    private final @NotNull Class<?> type;

    /**
     * Constructor for derived classes (normally you should use {@link #wrap(Object)}).
     * @param delegate the object whose members we would access reflectively.
     * @param type the type used for member resolution in case of shadowed members.
     */
    protected ReflectionProxy(@NotNull Object delegate, @NotNull Class<?> type) {
        this.delegate = type.cast(delegate);
        this.type = type;
    }

    /**
     * Wraps an object in reflection proxy.
     * @param o an object to  be wrapped.
     * @return a reflection proxy for {@code o};
     *         if {@code o} is {@code null}, {@code null} will be returned;
     *         if {@code o} is a reflection proxy it will be returned without further wrapping
     */
    @Contract(value = "null->null;!null->!null", pure = true)
    public static @Nullable ReflectionProxy wrap(@Nullable Object o) {
        return o==null || o instanceof ReflectionProxy
                ? (ReflectionProxy) o
                : new ReflectionProxy(o, o.getClass());
    }

    /**
     * Returns the wrapped object and casts it to the specified type.
     *
     * @param type the expected type
     * @param <V> a type inference generic parameter
     * @return the wrapped object
     */
    @SuppressWarnings("unchecked")
    @Contract(pure = true)
    public <V> @NotNull V unwrap(@NotNull Class<V> type) {
        return (V) Extractors.boxClass(type).cast(delegate);
    }

    /**
     * Returns the wrapped object
     *
     * @return the wrapped object
     */
    public @NotNull Object unwrap() {
        return delegate;
    }

    /**
     * Changes the {@code type}, used to resolve shadowed members.
     * @param type the new type, to be used to resolve shadowed members.
     * @return an adapted reflection proxy instance
     */
    @Contract(pure = true)
    public @NotNull ReflectionProxy resolvingAtType(@NotNull Class<?> type) {
        return new ReflectionProxy(delegate, type);
    }

    /**
     * <p>Invokes a (potentially private) method on the wrapped object and returns the result
     * wrapped in a new reflection proxy.</p>
     *
     * <p>Known deficiency - if a static method is shadowed by an instance method in the same
     * type, the instance method will be preferred and there is no way to invoke the static
     * one using this class.</p>
     *
     * @param name the name of the method to invoke
     * @param args the actual arguments for the method
     * @return the {@link #wrap(Object) wrapped} method return value
     */
    public /*@Nullable*/ ReflectionProxy invoke(@NotNull @Identifier String name, @Nullable Object... args) {
        return wrap(Extractors.invokeMethod(delegate, type, name, Object.class, args));
    }

    /**
     * <p>Gets a field and returns the value wrapped in a new reflection proxy.</p>
     *
     * <p>Known deficiency - if a static field is shadowed by an instance field in the same
     * type, the instance field will be preferred and there is no way to access the static
     * one using this class.</p>
     *
     * @param name the name of the field to get
     * @return the {@link #wrap(Object) wrapped} value
     */
    public /*@Nullable*/ ReflectionProxy get(@NotNull @Identifier String name) {
        return wrap(Extractors.peekField(delegate, type, name, Object.class));
    }

    /**
     * <p>Sets a field.</p>
     *
     * <p>Known deficiency - if a static field is shadowed by an instance field in the same
     * type, the instance field will be preferred and there is no way to access the static
     * one using this class.</p>
     *
     * @param name the name of the field to set
     * @param value the value to assign
     */
    public void set(@NotNull @Identifier String name, @Nullable Object value) {
        Extractors.pokeField(delegate, type, name, value);
    }

    /**
     * Transform this reflection proxy to another reflection proxy - useful for chaining when
     * the desired operation is not get or invoke.
     *
     * @param c function taking a reflection proxy as argument and returning object,
     *          which will be {@link #wrap(Object) wrapped} automaticlaly as needed.
     *
     * @return the <em>wrapped</em> result of {@code c}
     */
    public /*@Nullable*/ ReflectionProxy map(@NotNull Function<ReflectionProxy, Object> c) {
        return wrap(c.apply(this));
    }

    /**
     * Do something with this reflection proxy in the middle of a method-call chain.
     * @param c the operations to perform on this reflection proxy.
     * @return this instance
     */
    public @NotNull ReflectionProxy tap(@NotNull Consumer<ReflectionProxy> c) {
        c.accept(this);
        return this;
    }

    @Override
    public int hashCode() {
        return Objects.hash(delegate);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReflectionProxy)) return false;
        ReflectionProxy that = (ReflectionProxy) o;
        return Objects.equals(delegate, that.delegate) && Objects.equals(type, that.type);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder().append("rpx*").append(delegate).append(": ").append(delegate.getClass().getSimpleName());
        if (!delegate.getClass().equals(type)) sb.append(" proxied as ").append(type.getSimpleName());
        return sb.toString();
    }
}
