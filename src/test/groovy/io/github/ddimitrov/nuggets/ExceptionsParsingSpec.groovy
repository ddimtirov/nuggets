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

@Title("Exceptions :: Parse from stack trace")
@Subject(Exceptions)
@SuppressWarnings("GroovyAccessibility")
class ExceptionsParsingSpec extends Specification {
    void setup() {
        assert Exceptions.TRANSFORMER.get()==null : "Some of the previous tests did not clean up the global exception transformer"
    }

    @Unroll
    def "roundtrip #useCase"(useCase, Throwable throwable) {
        setup:
        def stacktrace = Exceptions.toStacktraceString(throwable)
        def stacktraceMissingException = stacktrace.replaceAll(~/Exception\b/, 'MissingException')

        when: 'Parsing an exception'
        def reversed = Exceptions.parseStacktrace(stacktrace)

        then: 'We get an exception with the same class, message and stacktrace'
        Exceptions.toStacktraceString(reversed)==stacktrace
        reversed.class == throwable.class

        when: 'Parsing an exception that has a class not available to the current JVM'
        def reversedMissingException = Exceptions.parseStacktrace(stacktraceMissingException)

        then: 'We get an instance of ThrowableClassNotFoundException with the same message, stack, toString() and printStackTrace()'
        Exceptions.toStacktraceString(reversedMissingException)==stacktraceMissingException
        reversedMissingException.class == ThrowableClassNotFoundException

        where:
        useCase                                | throwable
        'plain exception'                      | new Exception()
        'plain exception with text'            | new Exception(" foo bar ")
        'plain exception with multi-line text' | new Exception(" foo \n bar ")
        'plain exception with no stack'        | new StacklessException(" foo \n bar ")
        'exception with cause'                 | new Exception("main exception", new Error("the real cause"))
        'exception with deep cause'            | new Exception("main exception", deepException(5, new RuntimeException("Deep exception")))
        'exception with deep stackless cause'  | new StacklessException("main exception", deepException(5, new RuntimeException("Deep exception")))
        'exception with multi-level cause'     | new Exception("main exception", new Error('foobar', deepException(5, new RuntimeException("Deep exception"))))
        'exception w/ multi-level cause no msg'| new Exception(new Error(deepException(5, new RuntimeException("Deep exception"))))
        'exception with circular cause'        | new Exception("main", circularException(5, new RuntimeException("loop")))
    }

    def "parse single stack frame"(String line) {
        when:
        def sf = Exceptions.parseStackFrame(line.indexOf('\tat ') + 4, line)

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

    private static Throwable deepException(int i=5, Throwable t) {
        if (i==0) throw t
        try {
            return deepException(i - 1, t)
        } catch (e) {
            return e
        }
    }

    private static Throwable circularException(int i, Throwable loop, Throwable prev=null) {
        def cause = new Exception("looped cause " + i)
        if (prev==null) {
            loop.initCause(cause)
            return circularException(i-1, loop, cause)
        }

        if (i<=0) {
            prev.initCause(loop)
            return loop
        }

        prev.initCause(cause)
        return circularException(i-1, loop, cause)
    }
}

@InheritConstructors
class StacklessException extends Exception {
    @Override synchronized Throwable fillInStackTrace() { return this }
}