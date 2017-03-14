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

package io.github.ddimitrov.nuggets.internal.groovy;

import groovy.lang.Closure;
import groovy.lang.DelegatesTo;
import io.github.ddimitrov.nuggets.ExceptionTransformerBuilder;
import io.github.ddimitrov.nuggets.Extractors;
import io.github.ddimitrov.nuggets.Functions;
import io.github.ddimitrov.nuggets.Ports;
import org.intellij.lang.annotations.Identifier;
import org.intellij.lang.annotations.Pattern;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/** Extra API to make Groovy usage nicer */
public class NuggetsExtensions {
    private NuggetsExtensions() { }

    /**
     * Manipulate the exception contents on ad-hoc basis
     * @param self the target of this extension method
     * @param transformationSpec a spec closure that will be run on {@link ExceptionTransformerBuilder} delegate.
     * @return the transformed exception - typically we would throw or log that.
     */
    public static @NotNull Throwable transform(@NotNull Throwable self, @NotNull @DelegatesTo(ExceptionTransformerBuilder.class) Closure<?> transformationSpec) {
        ExceptionTransformerBuilder builder = new ExceptionTransformerBuilder();
        transformationSpec.setDelegate(builder);
        transformationSpec.call();
        Function<Throwable, Throwable> transformation = builder.build();
        return transformation.apply(self);
    }


    /**
     * <p>Extract the value of a static field regardless of its visibility. If there are multiple shadowed fields with the
     * given name, return the value of the first found when traversing the inheritance hierarchy starting from the
     * {@code type}, until {@code Object}. </p>
     * <p>You can use this method to easily get {@code Unsafe} and cache it for further usage, like this:</p>
     * <pre><code>
     * public static final def UNSAFE = Unsafe.peekField("theUnsafe") as Unsafe
     * </code></pre>
     *
     * @param self the class whose field we are peeking
     * @param fieldName the name of the field to extract.
     *
     * @return the extracted value of the requested field.
     * @throws IllegalArgumentException if the {@code fieldType} is a primitive type.
     *
     * @see Extractors#peekField(Object, Class, String, Class)
     */
    public static Object peekField(@NotNull Class<?> self, @NotNull @Identifier String fieldName) {
        return Extractors.peekField(null, self, fieldName, Object.class);
    }

    /**
     * <p>Extract the value of a field regardless of its visibility. If there are multiple shadowed fields with the
     * given name, return the value of the first found when traversing the inheritance hierarchy starting from the
     * {@code type}, until {@code Object}. </p>
     *
     * @param self the class whose field we are peeking
     * @param fieldName the name of the field to extract.
     * @param value the desired new value
     *
     * @see Extractors#pokeField(Object, Class, String, Object)
     */
    public static void pokeField(@NotNull Class<?> self, @NotNull @Identifier String fieldName, Object value) {
        Extractors.pokeField(null, self, fieldName, value);
    }

    private static final java.util.regex.Pattern validName = java.util.regex.Pattern.compile(Functions.VALID_NAME_PATTERN);
    /**
     * Decorates a closure with a static {@code toString()} implementation.
     * Because the actual value is supplied statically, the closure description is fixed - if you
     * need more control, check {@link #named(Closure, Supplier)}.
     *
     * @param self the {@code Closure} instance
     * @param name the name or description of the closure
     * @param <T> the closure return type
     * @return the decorated closure
     */
    public static <T> @NotNull Closure<T> named(
            @NotNull Closure<T> self,
            @NotNull @Pattern(Functions.VALID_NAME_PATTERN) String name
    ) {
        if (!validName.matcher(name).matches()) { // can not be covered when generating IDEA assertions
            throw new IllegalArgumentException("Name should be non-empty and cannot start or end with whitespace!");
        }
        class NamedClosure extends DelegatedClosure<T> {
            private static final long serialVersionUID = 3793550532851315223L;
            public NamedClosure() { super(self); }
            @Override public String toString() { return name; }
        }
        return new NamedClosure();
    }

    /**
     * Decorates a closure with a dynamic, user-controlled {@code toString()} implementation.
     * Because the actual value is supplied by supplier, the closure description can reflect
     * up-to-date status.
     *
     * @param self the {@code Closure} instance
     * @param nameSupplier code to calculate the current name of the closure
     * @param <T> the closure return type
     * @return the decorated closure
     */
    public static <T> @NotNull Closure<T> named(@NotNull Closure<T> self, @NotNull Supplier<?> nameSupplier) {
        class DescribedClosure extends DelegatedClosure<T> {
            private static final long serialVersionUID = -3060677360371749062L;
            public DescribedClosure() { super(self); }
            @Override public String toString() { return nameSupplier.get().toString(); }
        }
        return new DescribedClosure();
    }

    /**
     * Array index notation for getting the ports corresponding to a registered ID.
     * @param self the {@code Ports} instance
     * @param id registered port ID
     * @return the allocated port
     */
    public static int getAt(@NotNull Ports self, @NotNull String id) {
        return self.port(id);
    }

    /**
     * Similar to {@link Ports#withPorts(int, Consumer)}, except that you can use
     * the closure delegate and avoid repeating {@code it} for every declaration.
     *
     * <pre><code>
     * ports.withPortSpec(5000) {
     *     id "foo"
     *     id "bar" offset 1
     *     id "baz"
     * }
     * </code></pre>
     *
     * @param self the {@code Ports} instance
     * @param portBaseHint the desired starting port for the allocated port range
     * @param spec a closure as shown in the example.
     * @return the ports instance for chaining
     */
    public static @NotNull Ports withPortSpec(
            @NotNull Ports self,
            int portBaseHint,
            @NotNull @DelegatesTo(value = Ports.PortsSpecBuilder.class, strategy = Closure.DELEGATE_FIRST) Closure<?> spec
    ) {
        Ports.PortsSpecBuilder delegate = self.new PortsSpecBuilder();
        spec.setDelegate(delegate);
        spec.setResolveStrategy(Closure.DELEGATE_FIRST);
        spec.call(self);
        delegate.flush();
        return self.freeze(portBaseHint);
    }
}

