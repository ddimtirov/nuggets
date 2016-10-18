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
import org.intellij.lang.annotations.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

/** Extra API to make Groovy usage nicer */
public class NuggetsExtensions {
    private NuggetsExtensions() { }

    /**
     * Manipulate the exception contents on ad-hoc basis
     * @param self the target of this extension method
     * @param transformationSpec a spec closure that will be run on {@link ExceptionTransformerBuilder} delegate.
     * @return the transformed exception - typically we would throw or log that.
     */
    public static Throwable transform(Throwable self, @DelegatesTo(ExceptionTransformerBuilder.class) Closure<?> transformationSpec) {
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

}
