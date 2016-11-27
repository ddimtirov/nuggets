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

package io.github.ddimitrov.nuggets

import spock.lang.IgnoreIf
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ExecutionException
// note: due to the nature of assertions, this test is extremely fussy - read below for how to use it
//
// Always run this test as a part of a suite (i.e. don't run directly)
//
// Always append new tests at the end of the class - changes in the middle, esp introducing new closures
// have a high chance to cause a false alarm as the closure-class names change.
@Title("Exceptions :: Cleanup and stacktrace manipulations")
@IgnoreIf({ !Exceptions.TRANSFORM_ON_RETHROW })
@Subject(ExceptionsTransformationsSpec)
@SuppressWarnings("GroovyAccessibility")
class ExceptionsTransformationsSpec extends Specification {
    static final STACKTRACE_LITERAL_PRETTYPRINT = ~/ +((?=\tat |\t?Caused by: |\t?Suppressed: )|$)/

    void setup() {
        assert Exceptions.TRANSFORMER.get()==null : "Some of the previous tests did not clean up the global exception transformer"
    }

    void cleanup() {
        Exceptions.TRANSFORMER.set(null)
    }

    def "Rethrow transformed can be used to trim the stacktrace"() {
        setup:
        def bigUglyStackTrace = '''java.lang.Exception: error message
                                \tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
                                \tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java)
                                \tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java)
                                \tat java.lang.reflect.Constructor.newInstance(Constructor.java)
                                \tat org.codehaus.groovy.reflection.CachedConstructor.invoke(CachedConstructor.java)
                                \tat org.codehaus.groovy.runtime.callsite.ConstructorSite$ConstructorSiteNoUnwrapNoCoerce.callConstructor(ConstructorSite.java)
                                \tat org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCallConstructor(CallSiteArray.java)
                                \tat org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java)
                                \tat org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java)
                                \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_0(ExceptionsTransformationsSpec.groovy)
                                \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                                \tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java)
                                \tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java)
                                \tat java.lang.reflect.Method.invoke(Method.java)
                                \tat org.spockframework.util.ReflectionUtil.invokeMethod(ReflectionUtil.java)
                                \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeatureMethod(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$6.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.initializeAndRunIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runSimpleFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$5.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeatures(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunSpec(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$1.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runSpec(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.run(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.Sputnik.run(Sputnik.java)
                                '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        def niceStacktrace = '''java.lang.Exception: error message
                             \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_0(ExceptionsTransformationsSpec.groovy)
                             '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when: 'an exception is thrown'
        if (System.nanoTime()) throw new Exception("error message") // use a bogus condition to fool the control flow analysis

        then: 'it comes with a hefty stacktrace'
        Exception e = thrown()
        cleanString(e) == bigUglyStackTrace

        when: 'we specify the following transformations and rethrow'
        Exceptions.rethrowTransformed(e, true)
                .filterStackFramesForClass(~/org\.spockframework\.(runtime|util)\..*/)
                .filterStackFramesForClass(~/(sun|java\.lang)\.reflect\..*/)
                .filterStackFramesForClassPrefix('org.junit.')
                .filterStackFramesForClassPrefix('org.codehaus.groovy.')
                .filterStackFramesForClassPrefix('com.intellij.') // IDEA only
                .filterStackFramesForClassPrefix('org.gradle.')   // Gradle only (from here on)
                .filterStackFramesForClassPrefix('java.lang.Thread')
                .filterStackFramesForClassPrefix('java.util.concurrent.ThreadPoolExecutor')
                .filterStackFramesForClassPrefix('com.sun.proxy.')
                .done()

        then: 'we can isolate only the relevant business code'
        Exception te = thrown()
        cleanString(te)==niceStacktrace
    }

    def "We can set a global transform, applied by default for all Exception.rethrow() methods"() {
        setup:
        def bigUglyStackTrace = '''java.lang.Exception: error message
                                \tat sun.reflect.NativeConstructorAccessorImpl.newInstance0(Native Method)
                                \tat sun.reflect.NativeConstructorAccessorImpl.newInstance(NativeConstructorAccessorImpl.java)
                                \tat sun.reflect.DelegatingConstructorAccessorImpl.newInstance(DelegatingConstructorAccessorImpl.java)
                                \tat java.lang.reflect.Constructor.newInstance(Constructor.java)
                                \tat org.codehaus.groovy.reflection.CachedConstructor.invoke(CachedConstructor.java)
                                \tat org.codehaus.groovy.runtime.callsite.ConstructorSite$ConstructorSiteNoUnwrapNoCoerce.callConstructor(ConstructorSite.java)
                                \tat org.codehaus.groovy.runtime.callsite.CallSiteArray.defaultCallConstructor(CallSiteArray.java)
                                \tat org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java)
                                \tat org.codehaus.groovy.runtime.callsite.AbstractCallSite.callConstructor(AbstractCallSite.java)
                                \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_1(ExceptionsTransformationsSpec.groovy)
                                \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                                \tat sun.reflect.NativeMethodAccessorImpl.invoke(NativeMethodAccessorImpl.java)
                                \tat sun.reflect.DelegatingMethodAccessorImpl.invoke(DelegatingMethodAccessorImpl.java)
                                \tat java.lang.reflect.Method.invoke(Method.java)
                                \tat org.spockframework.util.ReflectionUtil.invokeMethod(ReflectionUtil.java)
                                \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeatureMethod(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$6.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.initializeAndRunIteration(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runSimpleFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$5.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeature(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runFeatures(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.doRunSpec(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner$1.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.runSpec(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.BaseSpecRunner.run(BaseSpecRunner.java)
                                \tat org.spockframework.runtime.Sputnik.run(Sputnik.java)
                                '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        def niceStacktrace = '''java.lang.Exception: error message
                             \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_1(ExceptionsTransformationsSpec.groovy)
                             '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when: 'an exception is thrown'
        if (System.nanoTime()) throw new Exception("error message") // use a bogus condition to fool the control flow analysis

        then: 'it comes with a hefty stacktrace'
        Exception eo = thrown()
        cleanString(eo) == bigUglyStackTrace

        when: 'if we we specify the following transform as the default for this thread and its children'
        Exceptions.transformOnRethrow()
                .filterStackFramesForClass(~/org\.spockframework\.(runtime|util)\..*/)
                .filterStackFramesForClass(~/(sun|java\.lang)\.reflect\..*/)
                .filterStackFramesForClassPrefix('org.junit.')
                .filterStackFramesForClassPrefix('org.codehaus.groovy.')
                .filterStackFramesForClassPrefix('com.intellij.') // IDEA only
                .filterStackFramesForClassPrefix('org.gradle.')   // Gradle only (from here on)
                .filterStackFramesForClassPrefix('java.lang.Thread')
                .filterStackFramesForClassPrefix('java.util.concurrent.ThreadPoolExecutor')
                .filterStackFramesForClassPrefix('com.sun.proxy.')
                .done()

        Exceptions.rethrow(new Exception("error message"))

        then: 'any of the Exceptions.rethrow() methods automatically applies the default transform'
        Exception te = thrown()
        cleanString(te)==niceStacktrace

        when: 'we need to bypass the default transform, we can use Exceptions.rethrowTransformed(e, true) without specifying transform'
        Exceptions.rethrowTransformed(new Exception("error message"), true).done()

        then: 'we get the full stack trace'
        Exception oet = thrown()
        cleanString(oet) == bigUglyStackTrace

        when: 'if we want to add an extra transform before the default is applied, use Exceptions.rethrowTransformed(e, false)'
        Exceptions.rethrowTransformed(new Exception("error message"), false)
                .filterStackFramesForClassPrefix('io.github.ddimitrov.nuggets')
                .done()

        then: 'we get the trimmed stack trace, filtered with our extra transform'
        Exception aet = thrown()
        cleanString(aet) == niceStacktrace.split('\n')[0] + '\n'

        when: 'we set the default transform again (in this case, we just clear it)'
        Exceptions.transformOnRethrow().done()
        Exceptions.rethrow(new Exception("error message"))

        then: 'we get the full stack trace'
        Exception ret = thrown()
        cleanString(ret) == bigUglyStackTrace
    }

    def "use the recommended transforms in the beginning of your main() method"() {
        setup: 'by calling Exceptions.setupTransformOnRethrowDefaultConfig(), you set reasonable defaults'
        Exceptions.setupTransformOnRethrowDefaultConfig()
        def cleanYetInformativeStackTrace='''java.lang.Exception: error message
                                            \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_2(ExceptionsTransformationsSpec.groovy)
                                            \tat org.spockframework.util.ReflectionUtil.invokeMethod(ReflectionUtil.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runFeatureMethod(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.doRunIteration(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner$6.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runIteration(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.initializeAndRunIteration(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runSimpleFeature(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.doRunFeature(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner$5.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runFeature(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runFeatures(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.doRunSpec(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner$1.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invokeRaw(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.invoke(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.runSpec(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.BaseSpecRunner.run(BaseSpecRunner.java)
                                            \tat org.spockframework.runtime.Sputnik.run(Sputnik.java)
        '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when: Exceptions.rethrow(new Exception("error message"))

        then: 'the exception is cleaned up from garbage, such as reflection, bridge methods, Groovy dispatch, etc.'
        Exception e = thrown()
        cleanString(e) == cleanYetInformativeStackTrace
    }

    def "remove stacktrace and replace message with human redable analysis"() {
        setup:
        def summary // in real life make this threadsafe
        Exceptions.transformOnRethrow()
                .replaceStackTrace { summary=it.length; new StackTraceElement[0] }
                .replaceMessage { "insightful summary ($summary frames truncated)" as String }
                .done()

        when: Exceptions.rethrow(new Exception("error message"))

        then:
        Exception e = thrown()
        cleanString(e) == "java.lang.Exception: insightful summary ($summary frames truncated)\n" as String
    }

    def "builders are not recyclable (each builder can build only one transformer)"() {
        setup:
        def builder = new ExceptionTransformerBuilder().unwrapThese(RuntimeException)

        when: 'we call done() or build() multiple times'
        def result1 = builder.build()
        def result2 = builder.build()

        then: 'they are idempotent (only the first time something happens)'
        result1 == result2

        when: 'we call configuration method after the buoilder is done'
        builder.unwrapThese(InvocationTargetException)

        then: 'exception is thrown'
        //noinspection GroovyUnusedAssignment
        IllegalStateException e = thrown()
    }

    def "use unwrap to remove useless exceptions added by wrap-and-rethrow's"() {
        setup:
        def transformer = new ExceptionTransformerBuilder()
                .unwrapThese(InvocationTargetException, ExecutionException)
                .unwrapWhen { it.class==RuntimeException && (!it.message || it.message==it.cause.toString()) }
                .replaceStackTrace { new StackTraceElement[0] } // remove stacks to reduce verbosity
                .build()

        when:
        def unwrapped = transformer.apply(new RuntimeException(      // filtered
                new IllegalStateException("bad bad state",           // kept
                        new RuntimeException("with message",         // kept for the message
                                new InvocationTargetException(       // filtered out
                                        new Exception("root cause"), // kept
                                        "useless wrapper"
                                )
                        )
                )
        ))

        then:
        Exceptions.toStackTraceString(unwrapped).replace(System.lineSeparator(), '\n') ==
                '''java.lang.IllegalStateException: bad bad state
                   Caused by: java.lang.RuntimeException: with message
                   Caused by: java.lang.Exception: root cause
                '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when:
        def unwrappedRoot = transformer.apply(new RuntimeException(// filtered
                new RuntimeException("with message",               // kept for the message
                        new RuntimeException()                     // kept as root
                )
        ))

        then:
        Exceptions.toStackTraceString(unwrappedRoot).replace(System.lineSeparator(), '\n') ==
                '''java.lang.RuntimeException: with message
                   Caused by: java.lang.RuntimeException
                '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')
    }

    def "filter out useless stack frames inserted by the ConfigSlurper"() {
        setup: "Read this one from a file, as it is too big to quote"
        def hairyStacktrace = getClass().getResource('ConfigSlurper-stacktrace.txt').text.replace('        ', '\t')
        def hairyException = Exceptions.parseStackTrace(hairyStacktrace)

        expect: "the exception onject is properly parsed"
        Exceptions.toStackTraceString(hairyException)==hairyStacktrace

        when: "transformed with the default transformer"
        Exceptions.rethrowTransformed(hairyException, true)
                .filterPresetReflection()
                .filterPresetGroovyMop()
                .filterPresetGroovyInternals()
                .filterPresetGroovyScripts()
                .build()

        then: "get rid of the pesky 'script14794455002432071560243.groovy' with no line number immediately followed by the one with line number"
        Exception e = thrown()
        Exceptions.toStackTraceString(e)=='''\
        groovy.lang.MissingMethodException: No signature of method: groovy.util.ConfigObject.recManager() is applicable for argument types: (script14794455002432071560243$_run_closure2$_closure4) values: [script14794455002432071560243$_run_closure2$_closure4@34f6515b]
        \tat script14794455002432071560243$_run_closure2.doCall(script14794455002432071560243.groovy:13)
        \tat groovy.util.ConfigSlurper$_parse_closure5.doCall(ConfigSlurper.groovy:256)
        \tat script14794455002432071560243.run(script14794455002432071560243.groovy:9)
        \tat groovy.util.ConfigSlurper$_parse_closure5.doCall(ConfigSlurper.groovy:268)
        \tat groovy.util.ConfigSlurper.parse(ConfigSlurper.groovy:286)
        \tat groovy.util.ConfigSlurper.parse(ConfigSlurper.groovy:170)
        '''.stripIndent().replace("\n", System.lineSeparator())
    }
    def "suppressed exceptions are filtered as with the same settings nad can not be suppressed (bad idea IMHO)"() {
        setup:
        def transformer = new ExceptionTransformerBuilder()
                .unwrapThese(RuntimeException)
                .replaceStackTrace { new StackTraceElement[0] } // remove stacks to reduce verbosity
                .build()

        when:
        def suppressor = new RuntimeException()
        suppressor.addSuppressed(new IllegalStateException())
        suppressor.addSuppressed(new IllegalStateException(
                new RuntimeException( // unwrapped but shows in the default message
                        new IllegalStateException()
                )
        ))

        def transformed = transformer.apply(new RuntimeException('foobar', // unwrapped
                new RuntimeException(                                      // unwrapped
                        suppressor
                )
        ))

        then:
        Exceptions.toStackTraceString(transformed).replace(System.lineSeparator(), '\n') ==
                '''java.lang.RuntimeException
                   \tSuppressed: java.lang.IllegalStateException
                   \tSuppressed: java.lang.IllegalStateException: java.lang.RuntimeException: java.lang.IllegalStateException
                   \tCaused by: java.lang.IllegalStateException
                '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

    }

    /** Formatting to get more readable tests and stable assertions */
    static String cleanString(Exception e) {
        def es = Exceptions.toStackTraceString(e)
                .replace(System.lineSeparator(), '\n')  // use \n regardless of the system line separator
                .replaceAll(~/:\d+\)/, ')')             // strip line numbers

        def lines = es.split('\n')
        def startOfSpock = lines.findIndexOf { it =~ /Sputnik\.run/ }
        return startOfSpock<0 ? es : lines.take(startOfSpock + 1).join('\n') + '\n'
    }
    
    def "use as Groovy extension method"() {
        when:
        def e = new IllegalArgumentException().transform {
            int summary = 0 // used by various closures
            unwrapThese                      RuntimeException, InvocationTargetException
            unwrapWhen                      { it instanceof RuntimeException && (it.message || it.message==it.cause.toString()) }
            replaceStackTrace               { summary=it.size(); return it.size()%2 ? new StackTraceElement[0] : it }
            replaceMessage                  { "$it.message // insightful summary (${->summary} frames truncated)" as String }
            filterStackFramesForClass       ~/org\.spockframework\.(runtime|util)\..*/
            filterStackFramesForClass       ~/(sun|java\.lang)\.reflect\..*/
            filterStackFramesForClassPrefix "org.junit."
            filterStackFramesForClassPrefix "org.codehaus.groovy."
        }

        then:
        e.message=~/insightful summary/
        with(e.stackTrace*.toString()) {
            !grep(~/org\.codehaus\.groovy\..*/)
            !grep(~/.*\.reflect\..*/)
        }
    }
}
