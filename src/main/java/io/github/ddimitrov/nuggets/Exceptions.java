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

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.compile;

/**
 * <p>Utility class providing methods for dealing with <a href="https://kotlinlang.org/docs/reference/exceptions.html#checked-exceptions">
 * checked exceptions</a> and <a href="https://dzone.com/articles/filtering-stack-trace-hell">
 * huge stacktraces</a>. Typically, you would want to import statically the family of {@code rethrow(...)}
 * methods, and use the others through explicit class reference.</p>
 *
 * <p>All methods in this class are designed to be threadsafe.</p>
 *
 * <h2>String conversion</h2>
 * <p>Every once in a while we need to format an exception without actually printing it.
 * That is where {@link Exceptions#toStacktraceString(Throwable)} comes useful.</p>
 *
 * <h2>About Checked Exceptions</h2>
 * <p>The theory says that we should either handle a checked exception by performing recovery/compensating action,
 * or let it propagate up the stack. The problem is that often there is no sensible way to handle the exception, and
 * while propagating sounds nice, for checked exceptions it also requires the methods at the top of the stack to
 * declaring every checked exception that the invoked method may call. In the end, more often than not, they end up
 * throwing {@code Exception} and not bothering with the details.</p>
 *
 * <p>Another popular approach is to wrap the checked exception in an unchecked one and rethrow. While that saves
 * us the need to add a new exception to the declaration of every method up the call chain, it adds another unnecessary
 * level of "caused by" exception, pushing the real exception further down in the log output.</p>
 *
 * <p>With this class, we propose a third approach - take advantage from the fact that the checked/unchecked exception
 * difference is enforced by the Javac compiler and not by the Java VM. This means that if we could throw a checked
 * exception from a method that does not declare it, we can cast away the "checkedness". To that end we add a set of
 * methods facilitating the following use cases:</p>
 *
 * <ul>
 * <li><a href="#uc1">Use Case 1: Rethrowing without declaring a checked exception</a></li>
 * <li><a href="#uc2">Use Case 2: Enriching exception message with local information</a></li>
 * <li><a href="#uc3">Use Case 3: Using methods throwing checked exceptions from non-throwing lambdas</a></li>
 * <li><a href="#uc4">Use Case 4: Transforming rethrown exceptions</a></li>
 * <li><a href="#uc5">Use Case 5: Setting default transformation for rethrown exceptions</a></li>
 * </ul>
 *
 * <h2 id="uc1">Use Case 1: Rethrowing without declaring a checked exception</h2>
 * <p>In the most simple case we would just want to throw a checked exception as if it were unchecked.
 * Here is example of using the {@link #rethrow(Throwable)} to achieve this:</p>
 *
 * <pre><code>
 * FooBar quietlyCreateFooBar() {
 *     try {
 *         return new FooBar();
 *     } catch (Exception e) {
 *         Exceptions.rethrow(e);
 *         return null; // compiler doesn't know this line is actually unreachable, so we need return statement
 *     }
 * }
 * </code></pre>
 *
 * <p>As the {@code rethrow()} method returns unbound generic type, it can be used as return value, regardless
 * of the type, transforming the snippet above to the slightly shorter:</p>
 *
 * <pre><code>
 * FooBar quietlyCreateFooBar() {
 *     try {
 *         return new FooBar();
 *     } catch (Exception e) {
 *         return Exceptions.rethrow(e);
 *     }
 * }
 * </code></pre>
 *
 *
 * <h2 id="uc2">Use Case 2: Enriching exception message with local information</h2>
 * <p>Often, when a lower application layer throws exception, there is some extra piece of information
 * that could help in debugging the issue. The standard way of handling that is by wrapping the exception
 * in another exception and rethrowing. We suggest a lighter alternative of using {@link #rethrow(Throwable, String)}
 * to prepend a string to the text of the original exception and rethrow as unchecked.</p>
 *
 * <pre><code>
 * FooBar quietlyCreateFooBar(String templateId) {
 *     try {
 *         return new FooBar(templates.get(templateId));
 *     } catch (Exception e) {
 *         String layerInfo = "TemplateId=" + templateId + ":";
 *         return Exceptions.rethrow(e, layerInfo);
 *     }
 * }
 * </code></pre>
 *
 * <p>Think a bit before you choose between message enrichment and wrapping. When in doubt, use enrichment.</p>
 *
 *
 * <h2 id="uc3">Use Case 3: Using methods throwing checked exceptions from non-throwing lambdas</h2>
 * <p>Many APIs designed for Java8 lambdas make use of the {@code java.util.function.*} classes.
 * This creates a problem when the lambda implementation throws exception. Consider the following
 * code trying to map files to their sizes.</p>
 *
 * <p>Consider the streams API - a method to find all files larger than specified size would typically look as follows</p>
 *
 * <pre><code>
 * Collection&lt;File&gt; findLargerThan(Collection&lt;File&gt; files, long size) {
 *     return files.stream()
 *                 .filter(it -&gt; {
 *                     try {
 *                          return Files.size(f.toPath()) &gt; size;
 *                     } catch (IOException e) {
 *                          throw new RuntimeException(e);
 *                     }
 *                 })
 *                 .collect(Collectors.toList());
 * }
 * </code></pre>
 *
 * <p>By using {@link #rethrow(Callable)} the same can be rewritten more compactly as:</p>
 *
 * <pre><code>
 * Collection&lt;File&gt; findLargerThan(Collection&lt;File&gt; files, long size) {
 *      return files.stream()
 *                  .filter(it -&gt; rethrow(() -&gt; Files.size(it.toPath()) &gt; size))
 *                  .collect(Collectors.toList());
 * }
 * </code></pre>
 *
 * <p id="uc3a">There is another overload of {@link #rethrow(ThrowingRunnable)} that will be automatically
 * inferred if the supplied lambda does not throw exception. Consider the following example :</p>
 *
 * <pre><code>
 * void processFile(File f) throws Exception {
 *      do something exceptional...
 * }
 * File processSafely(File f) {
 *      rethrow(() -&gt; processFile(f));
 *      return f;
 * }
 * </code></pre>
 *
 * <p id="uc3b">Often, we want to return use or return a particular value if the lambda finished without exception.
 * In the case above, we want to return {@code f} - this is accommodated by one final overload
 * {@link #rethrow(ThrowingRunnable, Object)}, shaving one line and improving the readability from the previous example:</p>
 *
 * <pre><code>
 * File processSafely(File f) {
 *      return rethrow(() -&gt; processFile(f), f);
 * }
 * </code></pre>
 *
 * <h2 id="uc4">Use Case 4: Transforming rethrown exceptions</h2>
 * <p>The {@link ExceptionTransformerBuilder} API allows us fine grained control over the exceptions we throw.
 * Use {@link #rethrowTransformed(Throwable, boolean)} to setup an ad-hoc transformation for rethrown exception.</p>
 *
 * <pre><code>
 * FooBar quietlyCreateFooBar() {
 *     try {
 *         return dependencyInjector.instantiate(FooBar.class);
 *     } catch (Exception e) {
 *         return rethrowTransformed(e, true) // true means we want to ignore any default transforms (rather than adding to them)
 *                .replaceMessage(t -&gt; t.getMessage() + " while creating foobar") // add extra info
 *                .unwrapThese(InvocationTargetException, ExecutionException)     // remove unnecessary levels of indirection
 *                .done();
 *     }
 * }
 * </code></pre>
 *
 * <h2 id="uc5">Use Case 5: Setting default transformation for rethrown exceptions</h2>
 * <p>While {@code rethrowTransformed()} gives us a lot of flexibility, sometimes we just want to set and forget
 * an exception cleaning transformation for the whole application. That is what {@link #transformOnRethrow()} is
 * about. If you configure a transformer with this method it will be applied automatically on every exception
 * rethrown on the current thread and its children. That is, unless the application was started with {@code <b>-D</b>nuggets.Exceptions.transform-on-rethrow=false}
 * command line parameter.</p>
 *
 * <p>You would usually want to call this method in the very beginning from your main method (which makes sure it would
 * apply for all application threads). The method can be called any time and the transformation applies for the current
 * thread and any future child threads.</p>
 *
 * <pre><code>
 * Exceptions.transformOnRethrow()
 *           .replaceStackTrace(ExceptionTransformerBuilder.FILTER_GROOVY_SYNTHETIC)
 *           .filterStackFramesForClass(Pattern.compile("^(java.lang.reflect|sun.reflect).*"))
 *           .filterStackFramesForClass(Pattern.compile("^(org.codehaus.groovy|org.gradle).*"))
 *           .unwrapThese(InvocationTargetException.class)
 *           .unwrapWhen(ex -&gt; ex.getMessage() == null &amp;&amp; ex.getClass().equals(RuntimeException.class))
 *           .done();
 * </code></pre>
 *
 * <p id="uc5a">The library even has a shortcut for the above, that you can apply by calling {@link #setupTransformOnRethrowDefaultConfig()}</p>
 */
public class Exceptions {
    private static final boolean TRANSFORM_ON_RETHROW = Boolean.parseBoolean(System.getProperty("nuggets.Exceptions.transform-on-rethrow", "true"));
    private static final InheritableThreadLocal<Function<Throwable, Throwable>> TRANSFORMER = new InheritableThreadLocal<>();
    public static final Pattern STRACE_MORE = Pattern.compile("\t\\.\\.\\. (\\d+) more");

    private Exceptions() {}

    /**
     * <p>Configures a transformer that would be used for every rethrown exception,
     * unless a System property {@code nuggets.Exceptions.transform-on-rethrow}
     * is not set explicitly to {@code false}</p>
     *
     * <p>This method sets up reasonable defaults that may change form version to
     * version of this library. If you need more control, use {@link #transformOnRethrow()}
     * instead.</p>
     *
     * @see #transformOnRethrow()
     * @see <a href="#uc5a">Use Case 5 for example usage</a>
     */
    public static void setupTransformOnRethrowDefaultConfig() {
        transformOnRethrow()
                .filterStackFramesForClass(compile("^(java.lang.reflect|sun.reflect).*"))
                .filterStackFramesForClass(compile("^(org.codehaus.groovy|org.gradle).*"))
                .unwrapThese(InvocationTargetException.class)
                .unwrapWhen(ex -> ex.getMessage() == null && ex.getClass().equals(RuntimeException.class))
                .build();
    }

    /**
     * <p>Configures a transformer that would be used for every rethrown exception,
     * unless a System property {@code nuggets.Exceptions.transform-on-rethrow}
     * is not set explicitly to {@code false}</p>
     *
     * <p>The transformer is thread-local and inherited from children threads.
     * This means that you'd better set it early in the application main thread.
     * It is acceptable to redefine the transformer multiple times (i.e. worker
     * threads wanting to clean up the thread-pool stack-frames)</p>
     *
     * <p>Keep in mind that this transformer is not automatically applied to the
     * argument of {@link #toStacktraceString(Throwable)}, so one can easily dump the full
     * stack if needed.</p>
     *
     * <p>If you are not sure what transformations to specify, you may want to just
     * call {@link #setupTransformOnRethrowDefaultConfig()} at application startup,
     * which would configure reasonable defaults.</p>
     *
     * @return a builder to configure the cleanup. Call {@link ExceptionTransformerBuilder#done()} to install.
     *
     * @see #setupTransformOnRethrowDefaultConfig()
     * @see <a href="#uc5">Use Case 5 for example usage</a>
     */
    public static @NotNull ExceptionTransformerBuilder transformOnRethrow() {
        return new ExceptionTransformerBuilder(TRANSFORMER::set);
    }

    /**
     * <p>Rethrows exception, but not before cleaning it up with ad-hoc specified transformation.
     * The transformation will be applied <em>before</em> OR <em>instead-of</em> the
     * the default transformation, depending on the value of the {@code suppressDefaultTransform}
     * parameter.</p>
     *
     * @param t an exception to format and rethrow.
     *
     * @param suppressDefaultTransform if {@code false} the default transformation, specified
     *                                 by {@link #transformOnRethrow()} will be applied AFTER the
     *                                 transform specified by this method. Otherwise, ONLY the
     *                                 this transform will be applied (the default will be suppressed).
     *
     * @return a builder that would rethrow the formatted {@code t} when the
     *        {@link ExceptionTransformerBuilder#done()} method is called.
     *
     * @see ExceptionTransformerBuilder
     * @see #transformOnRethrow()
     * @see #rethrow(Throwable, String)
     * @see #rethrow(Throwable)
     * @see <a href="#uc4">Use Case 4 for example usage</a>
     */
    public static @NotNull ExceptionTransformerBuilder rethrowTransformed(@NotNull Throwable t, boolean suppressDefaultTransform) {
        return suppressDefaultTransform
                ? new ExceptionTransformerBuilder(transform -> doSneakyThrow(transform.apply(t)))
                : new ExceptionTransformerBuilder(transform -> rethrow(transform.apply(t)));
    }

    /**
     * <p>Adapter allowing to use throwing methods from void lambdas of non-throwing interfaces.
     * Unlike {@link #rethrow(ThrowingRunnable)} this overload returns a value and can be used
     * in {@code return} statement. For usage examples, see {@link #rethrow(Callable)}.</p>
     *
     * @param throwingRunnable typically a parameterless lambda inside a capturing lambda of the desired type
     * @param returnValueOnSuccess value to return if no exception is thrown.
     *
     * @param <T> inferring the return type.
     * @return the {@code returnValueOnSuccess} if the {@code throwingRunnable} completed without throwing exception.
     * @throws Throwable thrown from the {@code throwingRunnable} (undeclared)
     *
     * @see #rethrow(Callable)
     * @see #rethrow(ThrowingRunnable)
     * @see <a href="#uc3b">Use Case 3 for example usage</a>
     */
    @SuppressWarnings("JavaDoc") // declaring we throw an exception not in the signature
    @Contract(pure=true)
    public static <T> @Nullable T rethrow(@NotNull ThrowingRunnable throwingRunnable, @Nullable T returnValueOnSuccess) {
        try {
            throwingRunnable.run();
            return returnValueOnSuccess;
        } catch (Throwable e) {
            return rethrow(e);
        }
    }

    /**
     * <p>Adapter allowing to use throwing methods from void lambdas of non-throwing interfaces.
     * For usage examples, see {@link #rethrow(Callable)}.</p>
     *
     * @param throwingRunnable typically a parameterless lambda inside a capturing lambda of the desired type
     * @throws Throwable thrown from the {@code throwingSupplier} (undeclared)
     *
     * @see #rethrow(Callable)
     * @see #rethrow(ThrowingRunnable, Object)
     * @see <a href="#uc3a">Use Case 3 for example usage</a>
     */
    @SuppressWarnings({"JavaDoc", "ResultOfMethodCallIgnored"}) // declaring we throw an exception not in the signature
    public static void rethrow(@NotNull ThrowingRunnable throwingRunnable) {
        rethrow(throwingRunnable, null);
    }

    /**
     * <p>Adapter allowing to use throwing methods from lambdas of non-throwing interfaces.</p>
     *
     * @param throwingSupplier typically a parameterless lambda inside a capturing lambda of the desired type
     * @param <T> inferred return type
     * @return the return value of the {@code throwingSupplier}
     * @throws Throwable thrown from the {@code throwingSupplier} (undeclared)
     *
     * @see #rethrow(ThrowingRunnable)
     * @see <a href="#uc3">Use Case 3 for example usage</a>
     */
    @SuppressWarnings("JavaDoc") // declaring we throw an exception not in the signature
    public static <T> T rethrow(@NotNull Callable<T> throwingSupplier) {
        try {
            return throwingSupplier.call();
        } catch (Exception e) {
            return rethrow(e);
        }
    }

    /**
     * <p>Rethrows a checked exception as unchecked, even if it is not added to the method
     * signature. Also allows to prepend some text to the exception message (useful when we
     * want to capture some context without wrapping the exception).</p>
     *
     * <p>For more examples on the usage, see {@link #rethrow(Throwable) }</p>
     *
     * @param t an exception to rethrow as unchecked.
     * @param prependedReason text to prepend to the exception's message
     * @return dummy value, allowing this method to be used as a value of return statement.
     * @param <T> return type - inferred by the compiler, depending on the context expectations.
     * @throws Throwable thrown from the {@code throwingSupplier} (undeclared)
     *
     * @see Throwable#getMessage()
     * @see #rethrow(Throwable)
     * @see <a href="#uc2">Use Case 2 for example usage</a>
     */
    @Contract("_,_->fail") @SuppressWarnings("JavaDoc") // declaring we throw an exception not in the signature
    public static <T> T rethrow(@NotNull Throwable t, @Nullable String prependedReason) {
        if (prependedReason!=null && !prependedReason.trim().isEmpty()) {
            Extractors.pokeField(t, Throwable.class, "detailMessage", prependedReason + t.getMessage());
        }
        return rethrow(t);
    }

    /**
     * <p>Rethrows a checked exception as unchecked, even if it is not added to the method
     * signature. Used when we don't want to recover from an exception and propagate it
     * straight to the top-level exception handler.</p>
     *
     * <p>The return type is inferred, allowing us to use this method as return expression
     * regardless of the return type and take advantage of the compiler reachability analysis.</p>
     *
     * @param t an exception to rethrow as unchecked.
     * @return dummy value, allowing this method to be used as a value of return statement.
     * @param <T> return type - inferred by the compiler, depending on the context expectations.
     * @throws Throwable thrown from the {@code throwingSupplier} (undeclared)
     *
     * @see #rethrow(Throwable, String)
     * @see <a href="#uc1">Use Case 1 for example usage</a>
     */
    @Contract("_->fail") @SuppressWarnings("JavaDoc") // declaring we throw an exception not in the signature
    public static <T> T rethrow(@NotNull Throwable t) {
        if (TRANSFORM_ON_RETHROW) {
            Function<Throwable, Throwable> transformer = TRANSFORMER.get();
            if (transformer!=null) {
                t = transformer.apply(t);
            }
        }
        //noinspection RedundantTypeArguments
        return Exceptions.<RuntimeException, T>doSneakyThrow(t);
    }

    /**
     * Uses a mix of type inference and generics to cast away the "checkedness" of exception.
     *
     * @param t an exception to rethrow as unchecked.
     * @param <T> reinterpret-casting the desired throwing type.
     * @param <R> inferring the return type.
     * @throws T a type system loophole, using generic cast at the caller to make javac treat the exception
     *           as {@code RuntimeException}.
     *
     * @see <a href="http://stackoverflow.com/questions/31316581/a-peculiar-feature-of-exception-type-inference-in-java-8">
     *     Sneaky throw using Java 8 type inference</a>
     */
    @Contract("_->fail") @SuppressWarnings("unchecked")
    private static <T extends Throwable, R> R doSneakyThrow(@NotNull Throwable t) throws T {
        throw (T) t;
    }

    /**
     * Conveniently formats the full stacktrace as a String.
     *
     * @param t the exception to format.
     * @return a string representing the output of {@link Throwable#printStackTrace()}
     */
    public static @NotNull String toStacktraceString(@NotNull Throwable t) {
        StringWriter sw = new StringWriter();
        try(PrintWriter pw = new PrintWriter(sw)) {
            t.printStackTrace(pw);
        }
        return sw.getBuffer().toString();
    }

    public static StackTraceElement parseStackFrame(int startIdx, CharSequence line) {
        int classEnd = -1;
        int methodEnd = -1;
        int fileEnd = -1;
        int lineEnd = -1;

scan_loop:
        for (int i = startIdx; i < line.length(); i++) {
            switch (line.charAt(i)) {
                case '.':
                    if (methodEnd<0) classEnd = i;
                    break;
                case '(':
                    if (classEnd >= 0 && fileEnd<0) methodEnd = i;
                    break;
                case ':':
                    if (methodEnd >= 0) fileEnd = i;
                    break;
                case ')':
                    if (fileEnd >= 0) {
                        lineEnd = i;
                    } else {
                        fileEnd = i;
                    }
                    break scan_loop;
                default: break; // make findbugs happy
            }
        }


        String declaringClass = classEnd<0  ? null : line.subSequence(startIdx   , classEnd ).toString();
        String methodName     = methodEnd<0 ? null : line.subSequence(classEnd +1, methodEnd).toString();
        String fileName       = fileEnd<0   ? null : line.subSequence(methodEnd+1, fileEnd  ).toString();
        String lineNumberStr  = lineEnd<0   ? null : line.subSequence(fileEnd  +1, lineEnd  ).toString();

        if (declaringClass==null || methodName==null) {
            throw new IllegalArgumentException("Malformed stack frame string (can't parse class and method): " + line);
        }

        int lineNumber = lineNumberStr == null ? -1 : Integer.parseInt(lineNumberStr);
        if (fileName!=null) {
            switch (fileName) {
                case "Native Method" : lineNumber=-2; break;
                case "Unknown Source": fileName=null; break;
                default: break; // make findbugs happy
            }
        }

        return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }

    public static Throwable parseStacktrace(CharSequence text) {
        String eol = System.lineSeparator();
        String[] lines = text.toString().split(eol);
        try {
            return parseStacktraceInternal(eol, 0, new ArrayDeque<>(10), lines);
        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            return rethrow(e);
        }
    }

    private static Throwable parseStacktraceInternal(String eol, int start, Deque<Throwable> caused, String[] lines) throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException {
        int i = start;

        String[] classMessage = lines[i++].split(": ", 2);
        String className = classMessage[0];
        Class<Object> type = Extractors.getClassIfPresent(className);
        Throwable t = type == null
                ? new ThrowableClassNotFoundException(className)
                : (Throwable) type.getConstructor().newInstance();
        StringBuilder messageAccumulator = new StringBuilder(lines[0].length()); // one line messages are by far the most common case
        if (classMessage.length > 1) messageAccumulator.append(classMessage[1]);

        while (i < lines.length) {
            if (lines[i].startsWith("\tat ")) break; // this is for the next section
            messageAccumulator.append(eol).append(lines[i++]);
        }
        if (messageAccumulator.length()>0) {
            String message = messageAccumulator.toString();
            Extractors.pokeField(t, "detailMessage", message);
        }

        List<StackTraceElement> stackTraceAccumulator = new ArrayList<>(lines.length-i); // preallocate to avoid multiple resizing passes
        while (i < lines.length) {
            if (!lines[i].startsWith("\tat ")) break;
            stackTraceAccumulator.add(parseStackFrame(4, lines[i++]));
        }
        if (i < lines.length) {
            Matcher matcher = STRACE_MORE.matcher(lines[i]);
            if (matcher.matches()) {
                i++;
                String duplicateFramesStr = matcher.group(1);
                int duplicateFrames = Integer.parseInt(duplicateFramesStr);
                StackTraceElement[] causedStackTrace = caused.peekLast().getStackTrace();
                stackTraceAccumulator.addAll(Arrays.asList(Arrays.copyOfRange(
                        causedStackTrace,
                        causedStackTrace.length-duplicateFrames,
                        causedStackTrace.length
                )));
            }
        }

        StackTraceElement[] stackTrace = stackTraceAccumulator.toArray(new StackTraceElement[0]);
        Extractors.pokeField(t, "stackTrace", stackTrace);

        // TODO add suppressed

/*
        System.out.printf("lines[%d/%d]==", lines.length, i);
        if (i<lines.length) {
            System.out.printf("%s (%s)%n", lines[i], lines[i].startsWith("Caused by: "));
        } else {
            System.out.println("IOBE");
        }
*/
        Throwable cause;
        if (i<lines.length && lines[i].startsWith("\t[CIRCULAR REFERENCE:") && lines[i].endsWith("]")) {
            String loopedToString = lines[i].replaceFirst("^\t\\[CIRCULAR REFERENCE:", "").replaceFirst("]$", "");
            cause = caused.stream()
                          .filter(it -> Objects.equals(loopedToString, it.toString()))
                          .findFirst().orElseThrow(() -> new IllegalArgumentException("Can not resolve circular cause: " + loopedToString));
        } else if (i<lines.length && lines[i].startsWith("Caused by: ")) {
            String original = lines[i];
            lines[i] = lines[i].replaceFirst("Caused by: ", "");
            caused.addLast(t);
            cause = parseStacktraceInternal(eol, i, caused, lines);
            caused.removeLast();
            lines[i] = original;
        } else {
            cause = null;
        }
        Extractors.pokeField(t, "cause", cause);

        // TODO: support nesting
        return t;
    }
}

class ThrowableClassNotFoundException extends Exception {
    public static volatile @NotNull String indicator="";
    private final @NotNull String originalExceptionClassName;

    public ThrowableClassNotFoundException(@NotNull String originalExceptionClassName) {
        this.originalExceptionClassName = originalExceptionClassName;
    }

    @Override
    public String toString() {
        String className = getClass().getName();
        return indicator + super.toString().replace(className, originalExceptionClassName);
    }
}