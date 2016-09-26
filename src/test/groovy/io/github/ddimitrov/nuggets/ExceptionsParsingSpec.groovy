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
    @Unroll
    def "roundtrip #useCase"(useCase, Throwable throwable) {
        setup:
        def stacktrace = Exceptions.toStacktraceString(throwable)

        when:
        def reversed = Exceptions.parseStacktrace(stacktrace)

        then:
        Exceptions.toStacktraceString(reversed)==stacktrace

        where:
        useCase           | throwable
        'plain exception' | new Exception()
        'plain exception with text' | new Exception(" foo bar ")
        'plain exception with multi-line text' | new Exception(" foo \n bar ")
        'plain exception with no stack' | new StacklessException(" foo \n bar ")
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
}

@InheritConstructors
class StacklessException extends Exception {
    @Override synchronized Throwable fillInStackTrace() { return this }
}