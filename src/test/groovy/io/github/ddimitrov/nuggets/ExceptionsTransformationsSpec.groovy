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
        def niceStacktrace = '''java.lang.Exception: error message
                             \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_0(ExceptionsTransformationsSpec.groovy)
                             \tat java.util.ArrayList.forEach(ArrayList.java)
                             \tat java.util.ArrayList.forEach(ArrayList.java)
                             '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when: 'an exception is thrown'
        if (System.nanoTime()) throw new Exception("error message") // use a bogus condition to fool the control flow analysis

        then: 'it comes with a hefty stacktrace'
        Exception e = thrown()
        e.stackTrace.length > 50

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
        def niceStacktrace = '''java.lang.Exception: error message
                             \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_1(ExceptionsTransformationsSpec.groovy)
                             \tat java.util.ArrayList.forEach(ArrayList.java)
                             \tat java.util.ArrayList.forEach(ArrayList.java)
                             '''.replaceAll(STACKTRACE_LITERAL_PRETTYPRINT, '')

        when: 'an exception is thrown'
        if (System.nanoTime()) throw new Exception("error message") // use a bogus condition to fool the control flow analysis

        then: 'it comes with a hefty stacktrace'
        Exception eo = thrown()
        eo.stackTrace.length > 80

        when: 'if we we specify the following transform as the default for this thread and its children'
        Exceptions.transformOnRethrow()
                .filterStackFramesForClass(~/org\.spockframework\.(runtime|util)\..*/)
                .filterStackFramesForClass(~/(sun|java\.lang)\.reflect\..*/)
                .filterStackFramesForClassPrefix('org.junit.')
                .filterStackFramesForClassPrefix('org.codehaus.groovy.')
                .filterStackFramesForClassPrefix('org.apache.groovy.')
                .filterStackFramesForClassPrefix('com.intellij.') // IDEA only
                .filterStackFramesForClassPrefix('org.gradle.')   // Gradle only (from here on)
                .filterStackFramesForClassPrefix('java.lang.Thread')
                .filterStackFramesForClassPrefix('java.util.concurrent.ThreadPoolExecutor')
                .filterStackFramesForClassPrefix('com.sun.proxy.')
                .done()

        Exceptions.rethrow(new Exception("error message"))

        then: 'any of the Exceptions.rethrow() methods automatically applies the default transform'
        Exception te = thrown()
        te.stackTrace.length == 3
        cleanString(te)==niceStacktrace

        when: 'we need to bypass the default transform, we can use Exceptions.rethrowTransformed(e, true) without specifying transform'
        Exceptions.rethrowTransformed(new Exception("error message"), true).done()

        then: 'we get the full stack trace'
        Exception oet = thrown()
        oet.stackTrace.length > 80

        when: 'if we want to add an extra transform before the default is applied, use Exceptions.rethrowTransformed(e, false)'
        Exceptions.rethrowTransformed(new Exception("error message"), false)
                .filterStackFramesForClassPrefix('io.github.ddimitrov.nuggets')
                .filterStackFramesForClassPrefix('java.util.ArrayList')
                .done()

        then: 'we get the trimmed stack trace, filtered with our extra transform'
        Exception aet = thrown()
        cleanString(aet) == niceStacktrace.split('\n')[0] + '\n'

        when: 'we set the default transform again (in this case, we just clear it)'
        Exceptions.transformOnRethrow().done()
        Exceptions.rethrow(new Exception("error message"))

        then: 'we get the full stack trace'
        Exception ret = thrown()
        ret.stackTrace.length > 80
    }

    def "use the recommended transforms in the beginning of your main() method"() {
        setup: 'by calling Exceptions.setupTransformOnRethrowDefaultConfig(), you set reasonable defaults'
        Exceptions.setupTransformOnRethrowDefaultConfig()
        
        def cleanYetInformativeStackTrace='''java.lang.Exception: error message
                                            \tat io.github.ddimitrov.nuggets.ExceptionsTransformationsSpec.$spock_feature_0_2(ExceptionsTransformationsSpec.groovy)
                                            \tat org.spockframework.util.ReflectionUtil.invokeMethod(ReflectionUtil.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.lambda$new$0(MethodInfo.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invokeRaw(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invoke(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.runFeatureMethod(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.IterationNode.execute(IterationNode.java)
                                            \tat org.spockframework.runtime.SimpleFeatureNode.execute(SimpleFeatureNode.java)
                                            \tat org.spockframework.runtime.SimpleFeatureNode.execute(SimpleFeatureNode.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java)
                                            \tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java)
                                            \tat org.spockframework.runtime.SpockNode.sneakyInvoke(SpockNode.java)
                                            \tat org.spockframework.runtime.IterationNode.lambda$around$0(IterationNode.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.lambda$createMethodInfoForDoRunIteration$5(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invokeRaw(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invoke(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.runIteration(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.IterationNode.around(IterationNode.java)
                                            \tat org.spockframework.runtime.SimpleFeatureNode.lambda$around$0(SimpleFeatureNode.java)
                                            \tat org.spockframework.runtime.SpockNode.sneakyInvoke(SpockNode.java)
                                            \tat org.spockframework.runtime.FeatureNode.lambda$around$0(FeatureNode.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.lambda$createMethodInfoForDoRunFeature$4(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invokeRaw(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invoke(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.runFeature(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.FeatureNode.around(FeatureNode.java)
                                            \tat org.spockframework.runtime.SimpleFeatureNode.around(SimpleFeatureNode.java)
                                            \tat org.spockframework.runtime.SimpleFeatureNode.around(SimpleFeatureNode.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$9(NodeTestTask.java)
                                            \tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.executeRecursively(NodeTestTask.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.execute(NodeTestTask.java)
                                            \tat java.util.ArrayList.forEach(ArrayList.java)
                                            \tat org.junit.platform.engine.support.hierarchical.SameThreadHierarchicalTestExecutorService.invokeAll(SameThreadHierarchicalTestExecutorService.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$6(NodeTestTask.java)
                                            \tat org.junit.platform.engine.support.hierarchical.ThrowableCollector.execute(ThrowableCollector.java)
                                            \tat org.junit.platform.engine.support.hierarchical.NodeTestTask.lambda$executeRecursively$8(NodeTestTask.java)
                                            \tat org.spockframework.runtime.SpockNode.sneakyInvoke(SpockNode.java)
                                            \tat org.spockframework.runtime.SpecNode.lambda$around$0(SpecNode.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.lambda$createMethodInfoForDoRunSpec$0(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.model.MethodInfo.invoke(MethodInfo.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invokeRaw(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.invoke(PlatformSpecRunner.java)
                                            \tat org.spockframework.runtime.PlatformSpecRunner.runSpec(PlatformSpecRunner.java)
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
        // difficulties to make it work reliably on Windows/Appveyor vs my laptop, so screw EOLs - they've been tested elsewhere
        def normalizeEol = { String it -> it.replaceAll(~/[\n\r]+/, '\n').replace('\n', System.lineSeparator()) }

        setup: "Read this one from a file, as it is too big to quote"
        def hairyStacktrace = normalizeEol(getClass().getResource('ConfigSlurper-stacktrace.txt').text.replace('        ', '\t'))
        def hairyException = Exceptions.parseStackTrace(hairyStacktrace)

        expect: "the exception object is properly parsed"
        hairyStacktrace.split(System.lineSeparator()).length > 0
        hairyException.stackTrace.length > 0
        normalizeEol(Exceptions.toStackTraceString(hairyException))==normalizeEol(hairyStacktrace)

        when: "transformed with the default transformer"
        Exceptions.rethrowTransformed(hairyException, true)
                .filterPresetReflection()
                .filterPresetGroovyMop()
                .filterPresetGroovyInternals()
                .filterPresetGroovyScripts()
                .build()
        
        then: "get rid of the pesky 'script14794455002432071560243.groovy' with no line number immediately followed by the one with line number"
        Exception e = thrown()
        assert normalizeEol(Exceptions.toStackTraceString(e)) == normalizeEol('''\
        groovy.lang.MissingMethodException: No signature of method: groovy.util.ConfigObject.recManager() is applicable for argument types: (script14794455002432071560243$_run_closure2$_closure4) values: [script14794455002432071560243$_run_closure2$_closure4@34f6515b]
        \tat script14794455002432071560243$_run_closure2.doCall(script14794455002432071560243.groovy:13)
        \tat groovy.util.ConfigSlurper$_parse_closure5.doCall(ConfigSlurper.groovy:256)
        \tat script14794455002432071560243.run(script14794455002432071560243.groovy:9)
        \tat groovy.util.ConfigSlurper$_parse_closure5.doCall(ConfigSlurper.groovy:268)
        \tat groovy.util.ConfigSlurper.parse(ConfigSlurper.groovy:286)
        \tat groovy.util.ConfigSlurper.parse(ConfigSlurper.groovy:170)
        '''.stripIndent())
    }
    def "suppressed exceptions are filtered with the same settings as the top exception"() {
        setup: 'a transformation unwrapping RuntimeException (RTE) and stripping the whole stack-trace'
        def transformer = new ExceptionTransformerBuilder()
                .unwrapThese(RuntimeException)
                .replaceStackTrace { new StackTraceElement[0] } // remove stacks to reduce verbosity
                .build()

        when: 'we transform RTE(causedBy: suppressor); suppressor being RTE(suppressed: [ISE, ISE(causedBy: RTE(causedBy: ISE))])'
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

        then: 'we expect all RTEs bellow the top-one to be filtered out'
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
        def startOfSpock = lines.findIndexOf { it =~ /PlatformSpecRunner\.runSpec/ }
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
