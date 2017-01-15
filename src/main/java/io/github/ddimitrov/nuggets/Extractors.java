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

import org.intellij.lang.annotations.Identifier;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;


/**
 * <p><span class="badge green">Entry Point</span> Provides utilities for
 * <a href="#encapsulation">circumventing Java encapsulation</a>,
 * as well as building blocks for <a href="#di">custom dependency injection</a>.
 * All functions provided by this class are threadsafe.</p>
 *
 * <h2 id="encapsulation">Breaking Encapsulation</h2>
 * <p>If you need to access non-public or final members of a class, you may use
 * {@link #peekField(Object, Class, String, Class) peekField(target, targetType, fieldName, valueType)} and
 * {@link #pokeField(Object, Class, String, Object)  pokeField(target, class, fieldName, value)}
 * to bypass the Java encapsulation with minimal fuss.</p>
 *
 * <p> To access static members, just pass {@code null} for target. If you need to access instance
 * fields, you may skip the {@code targetType} parameter and it will be assumed to be actual class
 * of the {@code target} parameter.</p>
 *
 * <pre><code>
 * // get access to Unsafe
 * public static final Unsafe UNSAFE = Extractors.peekField(Unsafe.class, null, "theUnsafe", Unsafe.class);
 *
 * // swap the date format of existing object
 * SimpleDateFormat sdf = Extractors.peekField(service, "dateFormat", SimpleDateFormat.class);
 * Extractors.pokeField(sdf, "pattern", "yyyy/MM/dd");
 * </code></pre>
 *
 * <p> In case you need to access  shadowed fields (i.e., field with the same name defined multiple times in
 * the inheritance hierarchy, make sure you specify {@code targetType} to be a class where the field is
 * declared (or at least visible according to the Java rules).</p>
 *
 * <p>Both {@code peek/pokeField()} are going through the full reflection API to resolve the
 * field and assert security permissions for breaking the encapsulation. If you are repeatedly accessing the
 * same fields, you can improve the performance at the expense of slightly more verbose code by using
 * {@link #getAccessibleField(Class, String, boolean) getAccessibleField(type, fieldName, checkSuperclasses)}
 * directly as follows:</p>
 *
 * <pre><code>
 * // cache these
 * private static final Field dateFormatField = Extractors.getAccessibleField(MyService.class, "dateFormat", true);
 * private static final Field formatPatternField = Extractors.getAccessibleField(SimpleDateFormat.class, "pattern", true);
 *
 * // swap the date format of existing object (fast)
 * try {
 *     SimpleDateFormat sdf = (SimpleDateFormat) dateFormatField.get(service));
 *     formatPatternField.set(sdf, "yyyy/MM/dd");
 * } catch (IllegalAccessException e) {
 *     Exceptions.rethrow(e);
 * }
 * </code></pre>
 *
 * <h2 id="di">Instantiation and injection utils</h2>
 * <p>Sometimes, especially when we write a library, we may need to provide some functionality
 * if certain library is present and provide a fall-back or even omit it, if it isn't.
 * This is easy to do by using {@link #getClassIfPresent(String) getClassIfPresent(className)}: </p>
 *
 * <pre><code>
 * templateResolver = Extractors.getClassIfPresent("groovy.lang.GroovyObject")
 *     ? new GroovyTemplateResolver()
 *     : new SimpleResolver("${", "}");
 * </code></pre>
 *
 * <p>At times we would like some kind of DI functionality, but without bringing in a full-blown
 * DI container, such as Guice, Dagger or Pico. This is when {@link #findInjectableConstructor(boolean, Constructor...)}
 * and {@link #findInjectableMethods(Method...)} come handy. Once you find the things you want to invoke,
 * you may easily assemble a signature using {@link #autowireByType(Executable, Function)} as follows:</p>
 *
 * <pre><code>
 * public class SimpleDi {
 *     private Map&lt;Class, Object&gt; instances = new HashMap&lt;&gt;();
 * 
 *     public &lt;T&gt; void bindSingleton(Class&lt;T&gt; type, T implementation)  {
 *         instances.put(type, implementation);
 *     }
 * 
 *     public &lt;T&gt; Collection&lt;T&gt; instancesOf(Class&lt;T&gt; type) {
 *         return instances.values().stream()
 *                         .filter(it -&gt; type.isAssignableFrom(it.getClass()))
 *                         .map(type::cast)
 *                         .collect(Collectors.toSet());
 *     }
 * 
 *     public &lt;T&gt; T instantiate(Class&lt;T&gt; type) throws IllegalAccessException, InvocationTargetException, InstantiationException {
 *         if (instances.containsKey(type)) { //noinspection unchecked
 *             return (T) instances.get(type);
 *         } else {
 *             instances.put(type, null); // prevent instantiation loops
 *         }
 * 
 *         Function&lt;Class&lt;?&gt;, Object&gt; paramResolver = t -&gt; Exceptions.rethrow(() -&gt; {
 *             Object defaultValue = Extractors.defaultValue(t);
 *             return defaultValue == null ? instantiate(t) : defaultValue;
 *         });
 * 
 *         if ((type.getModifiers() &amp; (Modifier.ABSTRACT | Modifier.INTERFACE)) != 0) {
 *             throw new InstantiationException("Only concrete classes supported!");
 *         }
 *         // instantiate object
 *         Constructor&lt;T&gt; constructor = Extractors.findInjectableConstructor(false, type.getConstructors());
 *         Object[] args = Extractors.autowireByType(constructor, paramResolver);
 *         T instance = constructor.newInstance(args);
 *         instances.put(type, instance);
 * 
 *         // call methods annotated with @Inject
 *         for (Method method : Extractors.findInjectableMethods(type.getMethods())) {
 *             method.invoke(instance, Extractors.autowireByType(method, paramResolver));
 *         }
 * 
 *         return instance; // fully constructed and initialized
 *     }
 * } *
 * ...
 * // usage to bootstrap an app with plugin strategies and lifecycle management
 * di = new SimpleDi();
 * xyzzy = new XyzzyServiceImpl();
 * di.bindSingleton(IXyzzyService.class, xyzzy);
 * di.bindSingleton(IFooBarStrategy.class, new FooBarLocalImpl());
 * app = di.instantiate(Application.class);
 * di.instancesOf(XyzzyListener.class).forEach(it -&gt; xyzzy.addListener(it))
 * app.run();
 * di.instancesOf(Closeable.class).forEach(Closeable::close);
 * app.shutdown();
 * </code></pre>
 */
public class Extractors {
    @Nullable
    private static final Class<? extends Annotation> INJECT_ANNOTATION = getClassIfPresent("javax.inject.Inject");
    private Extractors() {}

    /**
     * <p>Extract the value of an instance field regardless of its visibility. If there are multiple shadowed fields with the
     * given name, return the value of the first found when traversing the inheritance hierarchy starting from the
     * {@code type}, until {@code Object}. </p>
     * <p>If you are looking to extract a static field, or would like to get the value of a shadowed field, use the
     * overloaded {@link #peekField(Object, Class, String, Class)}</p>
     *
     * @param target the object containing the data.
     * @param fieldName the name of the field to extract.
     * @param fieldType the expected type of the returned value.
     *
     * @param <T> inferring the return type.
     * @return the extracted value of the requested field
     *
     * @see #getAccessibleField(Class, String, boolean)
     * @see #peekField(Object, Class, String, Class)
     */
    @Contract(pure=true)
    public static <T> T peekField(@NotNull Object target, @Identifier @NotNull String fieldName, @NotNull Class<T> fieldType) {
        return peekField(target, target.getClass(), fieldName, fieldType);
    }

    /**
     * <p>Extract the value of a field regardless of its visibility. If there are multiple shadowed fields with the
     * given name, return the value of the first found when traversing the inheritance hierarchy starting from the
     * {@code type}, until {@code Object}. </p>
     *
     * @param target the object containing the data. {@code null} if we are looking to extract a static field.
     * @param type the type from which to start the search for the field. See {@link #getAccessibleField getAccessibleField(type, name, true)} for the precise meaning.
     * @param fieldName the name of the field to extract.
     * @param fieldType the expected type of the returned value. Needs to be a boxed (non-primitive) type.
     *
     * @param <T> inferring the return type.
     * @return the extracted value of the requested field.
     * @throws IllegalArgumentException if the {@code fieldType} is a primitive type.
     *
     * @see #getAccessibleField(Class, String, boolean)
     * @see #peekField(Object, String, Class)
     */
    @Contract(pure=true)
    public static <T> T peekField(
            @Nullable Object target,
            @NotNull Class<?> type,
            @Identifier @NotNull String fieldName,
            @NotNull Class<T> fieldType
    ) {

        try {
            if (fieldType.isPrimitive()) {
                throw new IllegalArgumentException("Due to limitations of the Java Reflection API, you need to always use boxed types");
            }
            Field field = getAccessibleField(type, fieldName, true);
            return fieldType.cast(field.get(target));
        } catch (IllegalAccessException e) { return doSneakyThrow(e); }
    }

    /**
     * <p>Modify the value of an instance field regardless of its visibility or {@code final} modifier.
     * If there are multiple shadowed fields with the given name, set the value of the first found when
     * traversing the inheritance hierarchy starting from the {@code type}, until {@code Object}. </p>
     * <p>If you are looking to set a static field, or would like to set the value of a shadowed field, use the
     * overloaded {@link #pokeField(Object, Class, String, Object)}</p>
     *
     * @param target the object on which to set the field. {@code null} if we are looking to set a static field.
     * @param fieldName the name of the field to set.
     * @param value the new value to set.
     *
     * @see #getAccessibleField(Class, String, boolean)
     * @see #peekField(Object, String, Class)
     * @see #pokeField(Object, Class, String, Object)
     */
    public static void pokeField(@NotNull Object target, @Identifier @NotNull String fieldName, @Nullable Object value) {
        pokeField(target, target.getClass(), fieldName, value);
    }

    /**
     * <p>Modify the value of a field regardless of its visibility or {@code final} modifier. If there are
     * multiple shadowed fields with the given name, set the value of the first found when traversing the
     * inheritance hierarchy starting from the {@code type}, until {@code Object}. </p>
     *
     * @param target the object on which to set the field. {@code null} if we are looking to set a static field.
     * @param type the type from which to start the search for the field. See {@link #getAccessibleField getAccessibleField(type, name, true)} for the precise meaning.
     * @param fieldName the name of the field to set.
     * @param value the new value to set.
     *
     * @see #getAccessibleField(Class, String, boolean)
     * @see #peekField(Object, Class, String, Class)
     * @see #pokeField(Object, String, Object)
     */
    public static void pokeField(
            @Nullable Object target,
            @NotNull Class<?> type,
            @Identifier @NotNull String fieldName,
            @Nullable Object value
    ) {
        try {
            Field field = getAccessibleField(type, fieldName, true);
            field.set(target, value);
        } catch (IllegalAccessException e) { doSneakyThrow(e); }
    }


    /**
     * <p>Gets a {@link Field} instance that can be used to get and set values, regardless of the field visibility, final or
     * static modifiers. Subject to security policy restrictions (if you don't know what that is, don't worry - you are
     * probably safe).</p>
     * <p>Sometimes there would be multiple fields with the same name declared in parent and sub-classes. In such cases
     * make sure you specify the type of the exact class you want to get the field from.</p>
     *
     * @param type the type or a subtype of the class that holds the field.
     * @param fieldName the name of the field we want.
     * @param checkSuperclasses if {@code false}, will check only {@code type}; if {@code true},
     *                          will check {@code type} and it's super classes all the way up
     *                          to {@code Object}.
     * @return the writable field instance
     * @throws NoSuchFieldException if the field could not be found in the searched classes.
     *
     * @see #peekField(Object, String, Class)
     * @see #pokeField(Object, String, Object)
     * @see Field#setAccessible(boolean)
     */
    @Contract(pure=true) @SuppressWarnings("JavaDoc")
    public static @NotNull Field getAccessibleField(@NotNull Class<?> type, @Identifier @NotNull String fieldName, boolean checkSuperclasses) {
        try {
            Field field = type.getDeclaredField(fieldName);
            field.setAccessible(true);

            int modifiers = field.getModifiers();
            if (!Modifier.isStatic(modifiers) || !Modifier.isFinal(modifiers)) {
                return field;
            }

            // the rest of the method deals with static final fields - should be a rare use case
            // http://zarnekow.blogspot.jp/2013/01/java-hacks-changing-final-fields.html
            // http://www.cliffc.org/blog/2011/10/17/writing-to-final-fields-via-reflection
            // http://www.cliffc.org/blog/2011/10/27/final-fields-part-2
            Field modifiersField = field.getClass().getDeclaredField("modifiers");
            boolean modifiersWasAccessible = modifiersField.isAccessible();
            try {
                modifiersField.setAccessible(true);
                modifiersField.setInt(field, modifiers & ~Modifier.FINAL);
                field.set(null, field.get(null)); // force the creation of accessor
            } finally {
                modifiersField.setInt(field, modifiers);
                modifiersField.setAccessible(modifiersWasAccessible);
            }

            return field;
        } catch (NoSuchFieldException e) {
            Class<?> superclass = type.getSuperclass();
            return !checkSuperclasses || Object.class.equals(superclass)
                    ? doSneakyThrow(e)
                    : getAccessibleField(superclass, fieldName, true);
        } catch (IllegalAccessException e) { return doSneakyThrow(e); }
    }

    /**
     * Converts Java boxed classes ({@code java.lang.Void}, {@code java.lang.Integer},
     * {@code java.lang.Character}, etc.) to their primitive peers.
     * If the {@code type} is already primitive, it is returned as is.
     * Array classes are returned as-is (that may change in the future).
     *
     * @param type a class to convert
     * @return the corresponding primitive class
     */
    @Contract(pure=true)
    public static @NotNull Class<?> unboxClass(@NotNull Class<?> type) {
        if (type.isPrimitive()) return type;
        try {
            // How to find the primitive type
            // http://stackoverflow.com/questions/180097/dynamically-find-the-class-that-represents-a-primitive-java-type
            return (Class) type.getField("TYPE").get(null);
        } catch (NoSuchFieldException|IllegalAccessException e) {
            return type; // unreachable - all primitives have TYPE
        }
    }

    /**
     * Converts primitive Java classes ({@code void.class}, {@code char.class},
     * {@code int.class}) to their boxed ({@code java.lang.Void}, {@code java.lang.Character},
     * {@code java.lang.Integer}, etc.)
     * If the {@code type} is not a primitive primitive, it will be returned as is.
     * Array classes are returned as-is (that may change in the future).
     *
     * @param type a class to convert
     * @return the corresponding primitive class
     */
    @Contract(pure=true)
    public static @NotNull Class<?> boxClass(@NotNull Class<?> type) {
        if (!type.isPrimitive()) return type;

        if (boolean.class.equals(type)) return Boolean.class;
        if (byte.class.equals(type)) return Byte.class;
        if (char.class.equals(type)) return Character.class;
        if (double.class.equals(type)) return Double.class;
        if (float.class.equals(type)) return Float.class;
        if (int.class.equals(type)) return Integer.class;
        if (long.class.equals(type)) return Long.class;
        if (short.class.equals(type)) return Short.class;
        if (void.class.equals(type)) return Void.class;

        return type; // unreachable - all primitives are enumerated above
    }

    /**
     * Loads a class if it is available on classpath or returns {@code null} if not.
     * Useful for providing optional functionality if certain library is in classpath.
     *
     * @param className the name of the class to load
     *
     * @param <T> inferring the return type.
     * @return the class if present on classpath, {@code null} otherwise.
     *
     * @see Class#forName(String)
     */
    @Contract(pure=true) @SuppressWarnings("unchecked")
    public static <T> @Nullable Class<T> getClassIfPresent(@NotNull String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Provides default value for each type, such as {@code false}, zero, empty array,
     * no-args constructed instance, or {@code null} if there is no no-arg constructor
     * or it threw exception.
     *
     * @param c type
     * @param <T> inferring the return type.
     * @return default value
     */
    @Contract(pure=true)
    public static <T> @Nullable T defaultValue(@NotNull Class<T> c) {
        try {
            Constructor<?> constructor = c.getConstructor();
            if (constructor !=null) return c.cast(constructor.newInstance());
        } catch (Exception ignored) { }

        if (c.isPrimitive()) {
            @SuppressWarnings("unchecked")
            Class<T> boxedEquivalent = (Class<T>) boxClass(c);
            c = boxedEquivalent;
        }

        if (c.isArray())                            return c.cast(Array.newInstance(c.getComponentType(), 0));
        if (BigDecimal.class.isAssignableFrom(c))   return c.cast(BigDecimal.ZERO);
        if (BigInteger.class.isAssignableFrom(c))   return c.cast(BigInteger.ZERO);
        if (Boolean.class.equals(c))                return c.cast(Boolean.FALSE);
        if (Byte.class.equals(c))                   return c.cast((byte) 0);
        if (Character.class.equals(c))              return c.cast('\0');
        if (Double.class.equals(c))                 return c.cast(0d);
        if (Float.class.equals(c))                  return c.cast(0f);
        if (Integer.class.equals(c))                return c.cast(0);
        if (Long.class.equals(c))                   return c.cast(0L);
        if (Short.class.equals(c))                  return c.cast((short) 0);

        if (Map.class.isAssignableFrom(c))          return c.cast(new LinkedHashMap<>());
        if (Set.class.isAssignableFrom(c))          return c.cast(new LinkedHashSet<>());
        if (List.class.isAssignableFrom(c))         return c.cast(new ArrayList<>());
        if (Collection.class.isAssignableFrom(c))   return c.cast(new ArrayList<>());

        return null; // void, classes without default constructor, etc.
    }

    /**
     * <p>Finds the best constructor for dependency injection instantiation, based on number of arguments
     * and {@code @Injected} annotation. If at least one candidate is annotated with {@code Injected}
     * or the {@code requireAnnotation==true}, then only annotated candidates will be considered.
     * Otherwise, return the candidates with largest number of formal arguments.</p>
     * <br>
     * <p>Example: find the most suitable constructor for DI (annotated with {@code Inject}, or largest number of params):</p>
     * <pre><code>
     * Constructor&lt;FooBar&gt; fbc = Extractors.findInjectableConstructor(false, FooBar.class.getConstructors());
     * FooBar foobar = fbc.newInstance(Extractors.autowireByType(fbc, Extractors::defaultValue));
     * </code></pre>
     *
     * @param requireAnnotation if {@code true}, consider only candidates annotated with {@code @Inject}
     * @param candidates list of constructors to consider. In practice we expect an array of {@code Constructor<T>},
     *                   though the actual arg is declared as {@code Constructor<T>} so it can be used without
     *                   warnings with the return value of {@link Class#getConstructors()}.
     *
     * @param <T> inferring the return type.
     * @return the most suitable constructor (most params, or annotated with {@code @Inject})
     *
     * @see #autowireByType(Executable, Function)
     * @see #findInjectableMethods(Method...)
     */
    @Contract(pure=true)
    public static <T> @NotNull Constructor<T> findInjectableConstructor(boolean requireAnnotation, @NotNull Constructor<?>... candidates) {
        if (candidates.length==0) {
            return doSneakyThrow(new NoSuchMethodException("Need at least one candidate for injectable constructor!"));
        }

        Constructor<?>[] injectable = findInjectableCandidates(requireAnnotation, null, candidates);
        if (injectable.length>1) {
            return doSneakyThrow(new NoSuchMethodException("Ambiguous injectable constructor: " + Arrays.toString(injectable)));
        }

        if (injectable.length<1) {
            return doSneakyThrow(new NoSuchMethodException("No injectable constructor found in: " + Arrays.toString(candidates)));
        }

        @SuppressWarnings("unchecked")  // cast Constructor<?> to Constructor<T>
        Constructor<T> injectableConstructor = (Constructor<T>) injectable[0];
        return injectableConstructor;
    }

    /**
     * <p>Finds candidates for method dependency injection, based on {@code @Injected} annotation.</p>
     *
     * <p>Example: Find all methods annotated with {@code Inject} and invoke them:</p>
     * <pre><code>
     * for (Executable m : Extractors.findInjectableCandidates(foobar.getClass().geMethods())) {
     *      m.invoke(foobar, Extractors.autowireByType(m, Extractors::defaultValue))
     * }
     * </code></pre>
     *
     * @param candidates list of methods to consider.
     * @return all candidates annotated with {@code @Inject})
     *
     * @see #findInjectableConstructor
     * @see #findInjectableCandidates(boolean, Predicate, Executable[])
     * @see #autowireByType(Executable, Function)
     */
    @Contract(pure=true)
    public static @NotNull Method[] findInjectableMethods(@NotNull Method... candidates) {
        return findInjectableCandidates(true, it -> it.getParameterCount()>0, candidates);
    }


    /**
     * <p>Finds constructors or methods for dependency injection, based on the number of arguments
     * and {@code @Injected} annotation. If at least one candidate is annotated with {@code Injected}
     * or the {@code requireAnnotation==true}, then only annotated candidates will be considered.
     * Otherwise, return the candidates with largest number of formal arguments.</p>
     * <br>
     * <p>Example: find the most suitable constructor for DI (annotated with {@code Inject}, or largest number of params):</p>
     * <pre><code>
     * Constructor&lt;FooBar&gt; fbc = Extractors.findInjectableCandidates(false, FooBar.class.getConstructors());
     * FooBar foobar = fbc.newInstance(Extractors.autowireByType(fbc, Extractors::defaultValue));
     * </code></pre>
     *
     * @param requireAnnotation if {@code true}, consider only candidates annotated with {@code @Inject}
     * @param filter     if not null and returns false for a candidate, the candidate is excluded
     * @param candidates list of constructors to consider. In practice we expect an array of {@code Constructor<T>},
     *                   though the actual arg is declared as {@code Constructor<T>} so it can be used without
     *                   warnings with the return value of {@link Class#getConstructors()}.
     *
     * @param <T> inferring the constructor or method type.
     * @return the most suitable constructor (most params, or annotated with {@code @Inject})
     *
     * @see #findInjectableConstructor(boolean, Constructor[])
     * @see #findInjectableMethods(Method...)
     * @see #autowireByType(Executable, Function)
     */
    @SafeVarargs
    @Contract(pure=true)
    public static <T extends Executable> @NotNull T[] findInjectableCandidates(boolean requireAnnotation, @Nullable Predicate<T> filter, @NotNull T... candidates) {
        if (requireAnnotation&&INJECT_ANNOTATION==null) return Arrays.copyOfRange(candidates, 0, 0);

        T[] selected = Arrays.copyOf(candidates, candidates.length);
        int found = 0;
        boolean annotated = requireAnnotation;

        for (T candidate : candidates) {
            if (filter!=null && !filter.test(candidate)) continue;

            boolean selectedAnnotated = INJECT_ANNOTATION != null && candidate.getAnnotation(INJECT_ANNOTATION) != null;
            if (selectedAnnotated) {
                if (!annotated) {
                    found = 0;
                    annotated = true;
                }
                selected[found++] = candidate;
            } else if (!annotated) {
                int selectedArity = found==0 ? -1 : selected[found - 1].getParameterTypes().length;
                int candidateArity = candidate.getParameterTypes().length;
                if (selectedArity > candidateArity) continue;
                if (selectedArity < candidateArity) found=0;
                selected[found++] = candidate;
            }
        }

        return Arrays.copyOf(selected, found);
    }

    /**
     * Provide arguments to invoke a method or constructor, based on signature types and resolver function.
     * Can be used to call arguments with null params, or implement simple DI layer.
     *
     * @param ctorOrMethod the constructor or method to call.
     * @param paramResolver mapping function from type to implementation value can be as simple as {@code Map::get}.
     * @return the signature array.
     */
    @Contract(pure=true)
    public static @NotNull Object[] autowireByType(
            @NotNull Executable ctorOrMethod,
            @NotNull Function<Class<?>, Object> paramResolver
    ) {
        Class<?>[] parameterTypes = ctorOrMethod.getParameterTypes();
        Object[] params = new Object[parameterTypes.length];
        int i = 0;
        for (Class<?> type : parameterTypes) {
            params[i++] = paramResolver.apply(type);
        }
        return params;
    }

    @Contract("_->fail") @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R doSneakyThrow(@NotNull Throwable t) throws T {
        throw (T) t;
    }

    /**
     * <p>Super-type linearization is an algorithm used to obtain an ordered
     * sequence of all super-types in the presence of multiple inheritance.
     * The list should provide the following guarantees:</p>
     *
     * <ol>
     * <li>All classes in the result should be assignable from {@code type}
     *     (i.e. {@code it} is supertype of {@code type)}, and the result
     *     should contain all supertypes of {@code type}</li>
     * <li>The linearization result of any supertype should result in a subset
     *     of the subtype linearization, preserving the same relative order.</li>
     * <li>The ordering is by "specificity".</li>
     * </ol>
     *
     * <p>An informal definition of specificity ordering is <em>"given two types -
     * {@code A} and {@code B}, more specific is the type you would expect to match
     * if you had overloads with both types and an argument of type {@code type}"</em>.
     * It is a concept easy to understand intuitively, and
     * <a href="https://blogs.oracle.com/mcimadamore/entry/testing_overload_resolution">surprisingly tricky</a>
     * around the edge cases.</p>
     *
     * <p>In practice I currently use the following algorithm, which is a simple and
     * good enough approximation to the JLS rules (see the referenced link):</p>
     * <ol>
     * <li>Superclasses come first, in the order of inheritance, except for
     *     Object which comes last. (That is, if {@code type} is not an interface)</li>
     * <li>In the middle are all interfaces in breadth-first, left-to-right order.
     *     On multiple inheritances from the same interface, the first wins.</li>
     * </ol>
     * @param type the type we are linearizing
     * @return a sequence of all supertypes of the type, ordered by specificity
     * @see <a href="http://docs.oracle.com/javase/specs/jls/se8/html/jls-15.html#jls-15.12.2.5">JLS 15.12.2.5 - specificity rules for method resolution</a>
     * @see <a href="https://en.wikipedia.org/wiki/C3_linearization">C3 alorithm for linearization</a>
     */
    public static Iterable<? extends Class<?>> linearized(@NotNull Class<?> type) {
        LinkedHashSet<Class<?>> linearized = new LinkedHashSet<>(); // both ordering and uniqueness are important

        // step 1: put super-classes first, up to but excluding Object (classes take precedence to interfaces)
            for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
                linearized.add(c);
                if (type.isInterface()) break; // interfaces are handled in the next step (still we need to put the seed)
            }

        // step 2: collect all interfaces in left-to-right breadth-first fashion
        Collection<Class<?>> prev = new ArrayList<>(linearized); // what we added in the previous round
        Collection<Class<?>> curr = new ArrayList<>();           // what we are going to add on this round
        do {
            for (Class<?> t : prev) {   // add the super interfaces of all types found in the prev round
                curr.addAll(Arrays.asList(t.getInterfaces()));
            }

            curr.removeAll(linearized); // remove the types already in the resulting set
            linearized.addAll(curr);    // add the new types to the resulting set

            // prepare for the curr iteration: swap prev<->curr, clear the curr
            Collection<Class<?>> temp = curr;
            curr = prev;
            prev = temp;
            curr.clear();
        } while(!prev.isEmpty());

        // step 3: add Object last (or it would be more specific than all of then interfaces, which is counter-intuitive)
        if (!type.isInterface()) {
            linearized.add(Object.class);
        }

        return linearized;
    }

    /**
     * <p>Walks through all accessible members (fields, methods or constructors)
     * of a given type and its supertypes, regardless of their visibility or
     * modifiers. Members of derived classes are processed before the members of
     * base classes.</p>
     * <p>Each member is made {@link AccessibleObject#setAccessible(boolean) accessible}
     * before being passed to the processor, so you can  invoke operations
     * on it directly.</p>
     * <p>If the processor throws an exception, it will be rethrown, aborting the iteration.</p>
     * @param <T> the inferred type of the accessible member.
     * @param extractor a function extracting a list of accessible members
     *                  from a class. Typically one of
     *                  {@link Class#getDeclaredConstructors()},
     *                  {@link Class#getDeclaredFields()},
     *                  {@link Class#getDeclaredMethods()}
     * @param startType the class from which we should start processing
     *                  working up the inheritance hierarchy towards
     *                  the {@code stopClass}
     * @param stopType the class where we should stop processing
     *                  (if {@code null}, we will stop at {@code Object})
     * @param processor typically lambda that will process each accessible
     *                  member. If needed to access {@code o} - do it through
     *                  the closure.
     */
    public static <T extends AccessibleObject> void eachAccessible(
            @NotNull Function<Class<?>, T[]> extractor,
            Class<?> startType, @Nullable Class<Object> stopType,
            @NotNull Extractors.AccessibleMemberProcessor<@NotNull T> processor
    ) {
        Class<?> type = startType;
        try {
            Class<Object> stop = stopType == null ? Object.class : stopType;
            while (type!=null && type !=stop) {
                for (T it : extractor.apply(type)) {
                    it.setAccessible(true);
                    processor.process(it);
                }
                type = type.getSuperclass();
            }
        } catch (Exception e) {
            Exceptions.rethrow(e);
        }
    }

    /**
     * Used by traversal functions to iterate over {@code AccessibleObjects} (i.e. methods, fields, constructors)
     * @param <T> generic type of the accessible object.
     */
    @FunctionalInterface
    public interface AccessibleMemberProcessor<T extends AccessibleObject> {
        /**
         * The traversal function will call this for for each method, field, or constructor
         * @param subject the current accessible object
         * @throws Exception we can pass through any exceptions to abort the iteration.
         */
        void process(T subject) throws Exception;
    }
}
