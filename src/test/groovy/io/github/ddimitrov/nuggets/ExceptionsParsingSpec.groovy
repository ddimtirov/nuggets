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

import groovy.transform.InheritConstructors
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title
import spock.lang.Unroll

import java.util.function.Supplier

@Title("Exceptions :: Parse from stack trace")
@Subject(Exceptions)
@SuppressWarnings("GroovyAccessibility")
class ExceptionsParsingSpec extends Specification {
    public static final String EOL = System.lineSeparator()

    void setup() {
        assert Exceptions.TRANSFORMER.get()==null : "Some of the previous tests did not clean up the global exception transformer"
    }

    @Unroll
    def "roundtrip #useCase"(useCase, Throwable throwable) {
        setup:
        def stacktrace = Exceptions.toStackTraceString(throwable)
        def stacktraceMissingException = stacktrace.replaceAll(~/Exception\b/, 'MissingException')

        when: 'Parsing an exception'
        def reversed = Exceptions.parseStackTrace(stacktrace)

        then: 'We get an exception with the same class, message and stacktrace'
        Exceptions.toStackTraceString(reversed)==stacktrace
        reversed.class == throwable.class

        when: 'Parsing an exception that has a class not available to the current JVM'
        def reversedMissingException = Exceptions.parseStackTrace(stacktraceMissingException)

        then: 'We get an instance of MissingClassSurrogateException with the same message, stack, toString() and printStackTrace()'
        Exceptions.toStackTraceString(reversedMissingException)==stacktraceMissingException
        reversedMissingException.class == MissingClassSurrogateException

        where:
        useCase                                | throwable
        'plain exception'                      | new Exception()
        'plain exception with text'            | new Exception(" foo bar ")
        'plain exception with multi-line text' | new Exception(" foo $EOL bar ")
        'plain exception with no stack'        | new StacklessException(" foo $EOL bar ")
        'exception with cause'                 | new Exception("main exception", new Error("the real cause"))
        'exception with deep cause'            | new Exception("main exception", deepException(5) { new RuntimeException("Deep exception") })
        'exception with deep stackless cause'  | new StacklessException("main exception", deepException(5) { new RuntimeException("Deep exception") })
        'exception with multi-level cause'     | new Exception("main exception", new Error('foobar', deepException(5) { new RuntimeException("Deep exception") } ))
        'exception w/ multi-level cause no msg'| new Exception(new Error(deepException(5) { new RuntimeException("Deep exception")}))
        'exception with circular cause'        | new Exception("main", addCircularCauses(5, new RuntimeException("loop")))
        'exception with single suppressed'     | addSuppressed(1, new RuntimeException("main"))
        'exception with five suppressed'       | addSuppressed(5, new RuntimeException("main"))
    }


    def "unknown exception classes are parsed with same to string unless marked"() {
        given:
        def stacktrace = """\
            Result: com.acme.FunkyException: From outer space
            \tat java.math.BigDecimal.divide(BigDecimal.java:1742)
            \tat console.run(console.txt:25)
            \tat com.intellij.rt.execution.CommandLineWrapper.main(CommandLineWrapper.java:48)
            """.stripIndent().replace("\n", EOL)

        when: 'we parse an unknown exception class'
        def reversed = Exceptions.parseStackTrace(stacktrace)

        then: 'we substitute with a surrogate exception'
        reversed.class == MissingClassSurrogateException

        and: 'the printStackTrace() representation will be the same as the parsed trace'
        Exceptions.toStackTraceString(reversed) == stacktrace

        when: 'we set a global indicator prefix to MissingClassSurrogateException'
        MissingClassSurrogateException.indicator = 'MISSING: '

        then: 'all the surrogate exceptions will be prefixed with the indicator'
        reversed.toString() == 'MISSING: ' + stacktrace.split(EOL)[0]
        Exceptions.toStackTraceString(reversed) == 'MISSING: ' + stacktrace

        when: 'we clear the global indicator'
        MissingClassSurrogateException.indicator = ''

        then: 'all will become again identical to the parsed'
        Exceptions.toStackTraceString(reversed) == stacktrace
    }

    def "reverse particularly hairy stack"() {
        def hairyStack = '''\
        java.lang.Throwable
        \tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:1)
        \tat console.run(console.txt:25)
        \tat com.intellij.rt.execution.CommandLineWrapper.main(CommandLineWrapper.java:48)
        \tSuppressed: java.lang.RuntimeException
        \t\tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:2)
        \t\t... 1 more
        \tSuppressed: java.lang.IllegalArgumentException: java.lang.Error
        \t\tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:3)
        \t\t... 1 more
        \tCaused by: foo.bar.funkyMonkey
        \t\t... 1 more
        \tSuppressed: java.lang.IllegalArgumentException
        \t\tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:4)
        \t\t... 1 more
        Caused by: java.lang.Exception
        \tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:5)
        \t... 1 more
        '''.stripIndent().replace("\n", EOL)

        when: def reversed = Exceptions.parseStackTrace(hairyStack)
        then: Exceptions.toStackTraceString(reversed) == hairyStack
    }

    def "parse single stack frame"(String line) {
        when:
        def sf = Exceptions.parseStackFrame(line, line.indexOf('\tat ') + 4)

        then:
        sf.toString()==line.split("\tat ")[1]

        where:
        line << ''' \tat java.math.BigDecimal.divide(BigDecimal.java)
                    \tat org.codehaus.groovy.runtime.typehandling.BigDecimalMath.divideImpl(BigDecimalMath.java:68)
                    \tat org.codehaus.groovy.runtime.dgmimpl.NumberNumberDiv$NumberNumber.invoke(NumberNumberDiv.java:323)
                    \tat ideaGroovyConsole.run(ideaGroovyConsole.groovy:4)
                    \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                    \tat java.lang.reflect.Method.invoke(Method.java:498)
                    \tat org.codehaus.groovy.runtime.callsite.PogoMetaMethodSite$PogoCachedMethodSite.invoke(PogoMetaMethodSite.java:169)
                    \tat console.run(console.txt:25)
                    \tat org.codehaus.groovy.tools.GroovyStarter.rootLoader(GroovyStarter.java:109)
                    \tat org.codehaus.groovy.tools.GroovyStarter.<init>(GroovyStarter.java:131)
                    \tat sun.reflect.NativeMethodAccessorImpl.invoke0(Native Method)
                '''.split('\n').findAll { it.trim() }

    }

    private static Throwable deepException(int i=5, Supplier<Throwable> t) {
        if (i==0) throw t.get()
        try {
            return deepException(i - 1, t)
        } catch (e) {
            return e
        }
    }

    private static Throwable addCircularCauses(int i, Throwable loop, Throwable prev=null) {
        def cause = new Exception('looped cause ' + i)
        if (prev==null) {
            loop.initCause(cause)
            return addCircularCauses(i-1, loop, cause)
        }

        if (i<=0) {
            prev.initCause(loop)
            return loop
        }

        prev.initCause(cause)
        return addCircularCauses(i-1, loop, cause)
    }

    private static Throwable addSuppressed(int i, Throwable t) {
        i.times { t.addSuppressed(new Exception("Suppressed $it")) }
        return t
    }

}

@InheritConstructors
class StacklessException extends Exception {
    @Override synchronized Throwable fillInStackTrace() { return this }
}