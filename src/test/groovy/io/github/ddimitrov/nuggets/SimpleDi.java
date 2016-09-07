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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class SimpleDi {
    private Map<Class<?>, Object> instances = new HashMap<>();

    public <T> void bindSingleton(Class<T> type, T implementation)  {
        instances.put(type, implementation);
    }

    public <T> Collection<T> instantiated(Class<T> type) {
        return instances.values().stream()
                        .filter(it -> type.isAssignableFrom(it.getClass()))
                        .map(type::cast)
                        .collect(Collectors.toSet());
    }

    public <T> T instantiate(Class<T> type) throws IllegalAccessException, InvocationTargetException, InstantiationException {
        if (instances.containsKey(type)) {
            @SuppressWarnings("unchecked")
            T inferred = (T) instances.get(type);
            return inferred;
        } else {
            instances.put(type, null); // prevent instantiation loops
        }

        Function<Class<?>, Object> paramResolver = t -> Exceptions.rethrow(() -> {
            Object defaultValue = Extractors.defaultValue(t);
            return defaultValue == null ? instantiate(t) : defaultValue;
        });

        if ((type.getModifiers() & (Modifier.ABSTRACT | Modifier.INTERFACE)) != 0) {
            throw new InstantiationException("Only concrete classes supported!");
        }
        // instantiate object
        Constructor<T> constructor = Extractors.findInjectableConstructor(false, type.getConstructors());
        Object[] args = Extractors.autowireByType(constructor, paramResolver);
        T instance = constructor.newInstance(args);
        instances.put(type, instance);

        // call methods annotated with @Inject
        for (Method method : Extractors.findInjectableMethods(type.getMethods())) {
            method.invoke(instance, Extractors.autowireByType(method, paramResolver));
        }

        return instance; // fully constructed and initialized
    }
}
