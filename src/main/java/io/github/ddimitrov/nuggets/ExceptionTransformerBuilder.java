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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * <p>Builds a transformer function manipulating exception messages, stack traces and nesting structure
 * (used internally by {@link Exceptions}).</p>
 * <p> Most of the methods of this class return the current instance, so they can be chained. We call these
 * <em>configuration methods</em> and their purpose is to define the behaviour of transforming function.
 * Calling configuration method after the transforming function is built is an error.</p>
 *
 * <p>The {@link #build()} method creates a transforming function and returns it. The {@link #done()} method
 * creates the transforming function and passes it to an action callback specified at construction time - this
 * is useful when a service wants to allow the client to configure a managed transformer.
 * We call these <em>builder methods</em>.</p>
 *
 * <p>As a simple rule, when we call methods multiple times, the configuration methods are accumulated, while the
 * builder methods are idempotent. Notable exception is {@link #replaceMessage(Function)} that can be called only
 * once.</p>
 *
 * <p>This class should be used by a single thread. The transforming function, that is returned from {@link #build()}
 * is threadsafe only if all the function-classes passed to the configuration methods are threadsafe.</p>
 *
 * <p>Example:</p>
 *
 * <pre><code>
 * Function&lt;Throwable, Throwable&gt; cleanup = new ExceptionTransformerBuilder()
 *           .replaceStackTrace(ExceptionTransformerBuilder.FILTER_GROOVY_SYNTHETIC)
 *           .filterStackFramesForClass(Pattern.compile("^(java.lang.reflect|sun.reflect).*"))
 *           .filterStackFramesForClass(Pattern.compile("^(org.codehaus.groovy|org.gradle).*"))
 *           .unwrapThese(InvocationTargetException.class)
 *           .unwrapWhen(ex -&gt; ex.getMessage() == null &amp;&amp; ex.getClass().equals(RuntimeException.class))
 *           .build();
 * // somewhere in a top-level exception handler
 *      ....
 * } catch (Exception e) {
 *     logger.error("something really bad happened, cleanup.apply(e));
 * }
 *
 * </code></pre>
 *
 * @see Exceptions#transformOnRethrow()
 * @see Exceptions#rethrowTransformed(Throwable, boolean)
 */
public class ExceptionTransformerBuilder {
    private final @NotNull Function<Throwable, Throwable> transformFunction = this::transform;
    private final @Nullable Consumer<Function<Throwable, Throwable>> buildCompleteAction;

    private @Nullable Function<Throwable, String> messageMapper;
    private @NotNull Predicate<Throwable> unwrap = it -> false;
    private @NotNull Function<StackTraceElement[], StackTraceElement[]> stackMapper = Function.identity();
    private @NotNull Function<@Nullable StackTraceElement, @Nullable StackTraceElement> frameMapper = Function.identity();
    private boolean done;

    /**
     * <p>Create a builder for exception transformer function, allows to clean up and enrich a stack trace
     * and get rid of insignificant stack frames, so troubleshooters can focus on what matters.</p>
     *
     * <p>To use, call the configuring methods by daisy-chaining them, and finally call {@link #build()}
     * to get the transformer function. If you call a configuring method after that, you would get an
     * {@code IllegalStateException.}</p>
     */
    public ExceptionTransformerBuilder() {
        this(null);
    }

    /**
     * <p>Create a configurer for exception transformer function used by a service component.
     * In the typical use-case, the service will use this constructor and return the builder
     * to the client, who will call the configuring methods by daisy-chaining them, and finally
     * call {@link #done()}, which will build the result and invoke {@code #buildCompleteAction}.
     * The {@code #buildCompleteAction} is specified by the service and is not visible to the client
     * - typically it just assigns the passed-in transformer function to a field or a thread-local.</p>
     *
     * <p>To use, to publish the transformer function. If you call a configuring method after that,
     * you would get an {@code IllegalStateException.}</p>
     *
     * @param buildCompleteAction specifies what to do with the function once built. Invoked when
     *                            the client calls {@link #build()} or {@link #done()}
     *
     * @see ExceptionTransformerBuilder#ExceptionTransformerBuilder()
     */
    public ExceptionTransformerBuilder(@Nullable Consumer<Function<Throwable, Throwable>> buildCompleteAction) {
        this.buildCompleteAction = buildCompleteAction;
    }

    /**
     * <p>Creates the configured exception-transformer function and passes it to the
     * {@code buildCompleteAction} if one was specified at construction time.
     * Unlike {@link #done()} this method also returns the transformer function.</p>
     *
     * <p>Once this method has been called, any call to a method other than {@code #build()}
     * or {@link #done()} will result in exception.</p>
     *
     * <p>Calling this method multiple times has no effect and it will always return the same value.</p>
     * @return the configured exception-transformer function
     */
    public @NotNull Function<Throwable, Throwable> build() {
        if (!done && buildCompleteAction != null) buildCompleteAction.accept(transformFunction);
        done = true;
        return transformFunction;
    }

    /**
     * <p>Creates the configured exception-transformer function and passes it to the
     * {@code buildCompleteAction} if one was specified at construction time.
     * Unlike {@link #build()} this method does NOT return the transformer function,
     * but always returns {@code null}, inferring the return type.</p>
     *
     * <p>Once this method has been called, any call to a method other than {@link #build()}
     * or {@code #done()} will result in exception.</p>
     *
     * <p>Calling this method multiple times has no further effects.</p>
     *
     * @param <T> inferring the return type.
     *
     * @return the configured exception-transformer function
     */
    public @Nullable <T> T done() {
        build();
        return null;
    }

    /**
     * If configured, will replace the {@link Throwable#getMessage()} of the thrown
     * exception with the mapped value.
     *
     * @param messageMapper provide a new message for the passed-in exception.
     * @return the builder instance for chaining
     * @throws IllegalStateException if a messageMapper has already been specified or
     *                               the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder replaceMessage(@NotNull Function<Throwable, String> messageMapper) {
        if (done) throw new IllegalStateException("Can not change state once you have called build()!");
        if (this.messageMapper != null) throw new IllegalStateException("ReplaceMessage mapper already specified");
        this.messageMapper = messageMapper;
        return this;
    }

    /**
     * Specifies predicate to remove wrapped exceptions. Useful when using multiple-layers,
     * where each layer wraps and rethrows an exception. If this method is called multiple
     * times, the specified predicates are OR-ed together.
     *
     * @param unwrap if this predicate returns {@code true}, the tested exception will be removed
     *               from the {@link Throwable#getCause()} nesting.
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder unwrapWhen(@NotNull Predicate<Throwable> unwrap) {
        if (done) throw new IllegalStateException("Can not change state once you have called build()!");
        this.unwrap = this.unwrap.or(unwrap);
        return this;
    }

    /**
     * Specifies to remove wrapped exceptions of certain classes. Useful when using multiple-layers,
     * where each layer wraps and rethrows an exception. If this method is called multiple
     * times, the specified conditions are OR-ed together.
     *
     * @param exceptionClasses throwable is removed only if its class matches exactly one
     *                         of these specifief classes.
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    @SafeVarargs
    public final @NotNull ExceptionTransformerBuilder unwrapThese(@NotNull Class<? extends Throwable>... exceptionClasses) {
        Collection<Class<? extends Throwable>> ecs = Arrays.asList(exceptionClasses);
        return unwrapWhen(t -> ecs.contains(t.getClass()));
    }

    /**
     * Transforms the full stack, allowing to remove frames, add new frames, or putting extra information in
     * existing frames as follows:
     * <ul>
     *     <li>to remove a frame, just set the array cell to {@code null}</li>
     *     <li>to add a new frame, allocate a bigger array, copy what you need and add your custom frames where needed</li>
     *     <li>to decorate a frame, instantiate a new frame with the data of existing one and modify what you need</li>
     * </ul>
     *
     * If configured multiple times, the stack mappers are applied in the specified order. That means that some of them
     * may see {@code null} frames, leftovers from the previous.
     *
     * @param stackMapper the mapping function, as described above
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder replaceStackTrace(@NotNull Function<StackTraceElement[], @NotNull StackTraceElement[]> stackMapper) {
        if (done) throw new IllegalStateException("Can not change state once you have called build()!");
        this.stackMapper = this.stackMapper.andThen(stackMapper);
        return this;
    }

    /**
     * Transforms the stack frames one-by-one, allowing to remove frames, or putting extra information in existing
     * frames as follows:
     * <ul>
     *     <li>to remove a frame, just return {@code null}</li>
     *     <li>to decorate a frame, return a new frame, copying and modifying the data of existing one as needed</li>
     *     <li>if you need to add a new frame, use {@link #replaceStackTrace(Function)}</li>
     * </ul>
     *
     * If configured multiple times, the frame mappers are applied in the specified order.
     *
     * @param frameMapper the mapping function, as described above
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder replaceStackFrame(@NotNull Function<@Nullable StackTraceElement, @Nullable StackTraceElement> frameMapper) {
        if (done) throw new IllegalStateException("Can not change state once you have called build()!");
        this.frameMapper = this.frameMapper.andThen(frameMapper);
        return this;
    }

    /**
     * Removes stack frames not matching the predicate.
     *
     * If configured multiple times, the frame filters are applied in the specified order.
     *
     * @param frameFilter return {@code true} if we want to keep this frame; {@code false} otherwise.
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterStackFrame(@NotNull Predicate<StackTraceElement> frameFilter) {
        return replaceStackFrame(frame -> frame!=null && frameFilter.test(frame) ? frame : null);
    }

    /**
     * Removes stack frames whose {@link StackTraceElement#getClassName()} matches the specified pattern.
     *
     * @param classPattern classes of stack frames to remove.
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterStackFramesForClass(@NotNull Pattern classPattern) {
        return filterStackFrame(frame -> !classPattern.matcher(frame.getClassName()).matches());
    }

    /**
     * Removes stack frames whose {@link StackTraceElement#getClassName()} matches the specified prefix.
     *
     * @param classPrefix prefix of fully qualified classes of stack frames to remove.
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterStackFramesForClassPrefix(@NotNull String classPrefix) {
        return filterStackFrame(frame -> !frame.getClassName().startsWith(classPrefix));
    }

    /**
     * The actual transformation logic. Because it is an instance method and uses the class fields,
     * it is fairly easy to debug and inspect.
     *
     * @param t the exception we want to transform.
     * @return the transformed exception.
     */
    @Contract(value="null->null;!null->!null", pure=true)
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    private @Nullable Throwable transform(@Nullable Throwable t) {
        if (t == null) return null;

        Throwable cause = t.getCause();
        if (cause!=null && cause != t) {
            Throwable transformedCause = transform(cause);
            Extractors.pokeField(t, Throwable.class, "cause", transformedCause);
            if (unwrap.test(t)) return transformedCause;
        }

        @SuppressWarnings("unchecked")
        List<Throwable> suppressedExceptions = Extractors.peekField(t, Throwable.class, "suppressedExceptions", List.class);
        for (int i = 0; i < suppressedExceptions.size(); i++) {
            suppressedExceptions.set(i, transform(suppressedExceptions.get(i)));
        }
        if (!suppressedExceptions.isEmpty()) suppressedExceptions.removeIf(Objects::isNull);

        // map stack frames
        StackTraceElement[] stack = t.getStackTrace();

        // replace elements with null to remove, may return new (larger) array with synthetic frames
        StackTraceElement[] transformedStack = stackMapper.apply(stack);

        StackTraceElement[] filtered = Arrays.stream(transformedStack)
                                             .map(frameMapper)
                                             .filter(Objects::nonNull) // remove any nulls returned by frame mapper
                                             .toArray(StackTraceElement[]::new);

        Extractors.pokeField(t, Throwable.class, "stackTrace", filtered);

        // map message
        if (messageMapper!=null) {
            Extractors.pokeField(t, Throwable.class, "detailMessage", messageMapper.apply(t));
        }

        return t;
    }

    /**
     * Removes stack frames related to Java Reflection.
     *
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterPresetReflection() {
        return filterStackFramesForClass(Pattern.compile("^(java\\.lang\\.reflect|sun\\.reflect)\\..*"));
    }

    /**
     * Removes stack frames related to Groovy implementation (keeping the actual API classes).
     *
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterPresetGroovyInternals() {
        return filterStackFramesForClassPrefix("org.codehaus.groovy");
    }

    /**
     * Removes stack frames related to Groovy Meta-Object Protocol.
     *
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterPresetGroovyMop() {
        return filterStackFramesForClass(Pattern.compile("^groovy\\.lang\\.(?:(?:Expando|Delegating)?MetaClass(?:Impl)?|MetaMethod)$"));
    }

    /**
     * Removes stack frames related to Groovy Scripts bytecode generation.
     *
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder filterPresetGroovyScripts() {
        return replaceStackTrace(ExceptionTransformerBuilder::deleteAdjacentFramesWithoutLineNumbers);
    }

    /**
     * Unwraps exception that don't provide useful information (i.e. softening wrappers, or useless
     * wrapping when crossing layers).
     *
     * @return the builder instance for chaining
     * @throws IllegalStateException if the transform function has already been built.
     */
    public @NotNull ExceptionTransformerBuilder unwrapPresetCommonWrappers() {
        return unwrapThese(InvocationTargetException.class)
                .unwrapWhen(ex -> ex.getMessage() == null && ex.getClass().equals(RuntimeException.class));

    }


    /**
     * Removes a some extraneous Groovy frames, added by some Groovy utils, for example, exceptions from Groovy's
     * {@code ConfigSlurper} often look like this:
     *
     * <pre>
     * groovy.lang.MissingMethodException: No signature of method: groovy.util.ConfigObject.recManager() is applicable for argument types: (script14794455002432071560243$_run_closure2$_closure4) values: [script14794455002432071560243$_run_closure2$_closure4@34f6515b]
     *     at org.codehaus.groovy.runtime.ScriptBytecodeAdapter.unwrap(ScriptBytecodeAdapter.java:58)
     *     ...
     *     at script14794455002432071560243$_run_closure6.doCall(script14794455002432071560243.groovy:42)
     *     at script14794455002432071560243$_run_closure6.doCall(script14794455002432071560243.groovy)
     *     ...
     *     at script14794455002432071560243$_run_closure2.doCall(script14794455002432071560243.groovy:13)
     *     at script14794455002432071560243$_run_closure2.doCall(script14794455002432071560243.groovy)
     *     ...
     *     at groovy.util.ConfigSlurper.parse(ConfigSlurper.groovy:170)
     * </pre>
     *
     * Notice the duplicated stack frames with and without a line number. In this case the frame
     * without line number is not useful ans is better stripped.
     *
     * @param stack the raw stack frames
     * @return the transformed stack frames
     */
    @Contract("_->_")
    public static @NotNull StackTraceElement[] deleteAdjacentFramesWithoutLineNumbers(StackTraceElement[] stack) {
        for (int i = 0; i < stack.length; i++) {
            StackTraceElement frame = stack[i];
            if (frame==null) continue;

            if (frame.getLineNumber() < 0) {
                boolean sameAsPrev = i != 0 && stack[i - 1] != null &&
                        Objects.equals(stack[i - 1].getClassName(), frame.getClassName()) &&
                        Objects.equals(stack[i - 1].getMethodName(), frame.getMethodName());
                boolean sameAsNext = i < stack.length - 1 && stack[i + 1] != null &&
                        Objects.equals(stack[i + 1].getClassName(), frame.getClassName()) &&
                        Objects.equals(stack[i + 1].getMethodName(), frame.getMethodName());
                if (sameAsPrev || sameAsNext) {
                    stack[i] = null;
                }
            }

            boolean syntheticTrampoline = "callCurrent".equals(frame.getMethodName())
                    && frame.getClassName().contains("$")
                    && frame.getFileName() == null;

            if (syntheticTrampoline) {
                stack[i] = null;
            }
        }

        return stack;
    }
}

/*  IMPLEMENTATION NOTES:

    We could achieve better separation of concerns at the expense of complexity if
    we introduced 3 interfaces as follows:

        interface ExtensionTransformerSpec {
            ...all config methods except build() and done()
        }

        interface ExtensionTransformerBuilder extends ExtensionTransformerSpec {
            Finction<,> build()
        }

        interface ExtensionTransformerConfigurer extends ExtensionTransformerSpec {
            T done()
        }

    Then, the classes that use a `buildCompleteAction` would return `ExtensionTransformerConfigurer`
    while if anybody needs a custom transformer, they would get an `ExtensionTransformerBuilder`
    from a factory method in `Exceptions`. This class would be changed to package local scope.

    For now we keep it like this with the downside that the existence of both build()
    and done() methods can be confusing.
 */
