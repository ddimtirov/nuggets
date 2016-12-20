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

import spock.lang.Specification

import java.text.SimpleDateFormat
import java.util.function.Function

class FunctionsSpec extends Specification {

    def "fallback dispatch Java API"() {
        given:
        def parser = FunctionsJavaUsage.parseAnything().&apply

        expect:
        parser('2016 12 15') instanceof String
        parser('2016, 12, 15') instanceof List
        parser('2016-12-15') instanceof Date
        parser('2016.12') instanceof Double
        parser('2016') instanceof Long
    }

    def "fallback dispatch with validation predicate"() {
        given:
        def parser = FunctionsJavaUsage.parsePositiveNumber().&apply

        expect:
        parser('2012')==2012
        parser('2016.5')==2016.5

        when: parser('foobar')
        then:
        DispatchException e = thrown()
        e.message =~ /failed to approve successful result of \d+ fallback fuctions!/
        e.suppressed.size() == 2
        e.suppressed.every { it instanceof NumberFormatException }

        when:
        parser('-3')

        then:
        DispatchException e2 = thrown()
        e2.message =~ /failed to approve successful result of \d+ fallback fuctions!/
        e2.suppressed.size() == 2
        e2.suppressed.every { it instanceof DispatchException.InvalidResult }
        e2.suppressed*.toString() == ['invalid result "-3"', 'invalid result "-3.0"']
    }

    def "use failFast to search for mapped value matching a predicate"() {
        given:
        def f5 = FunctionsJavaUsage.ifMappedGreaterThan(5).&apply

        expect:
        f5(10)==10
        f5("10")==20
        f5("${->10}")==10

        when: f5(1)
        then:
        DispatchException de = thrown()
        de.message ==~ /failed to approve result \d+ fallback fuctions!/
        de.suppressed.length==3
        de.suppressed.every { it instanceof DispatchException.InvalidResult }

        when: f5("abc")
        then: "the exception from the last parser gets rethrown without wrappin, we lose the invalid results info"
        NumberFormatException e = thrown()
        e.message ==~ /For input string: "abc"/

    }

    def "failFast, no-predicate fallback dispatch of single function is a no-op"() {
        given:
        Function<?,?> f = {}

        expect:
        Functions.fallback(true, null, f)==f
    }

    def "fallback dispatch Java API usage from Groovy"() { // we should probably introduce idiomatic Groovy API unifying all dispatch styles
        given: 'the same as FunctionsJavaUsage.parseAnything()'
        def failFast = false
        def validator = null

        def parser = Functions.<String, Object>fallback(failFast, validator, // fixme - casting to function all over the place is not nice
                Long.&parseLong as Function,
                Double.&parseDouble as Function,
                { new SimpleDateFormat("yyyy-MM-dd").parse(it) } as Function,
                { String[] arr = it.trim().split("\\s*,\\s*")
                    assert arr.length!=1: "not an array: " + it
                    return arr as List
                } as Function,
                { s -> s } as Function
        ).&apply

        expect:
        parser('2016 12 15') instanceof String
        parser('2016, 12, 15') instanceof List
        parser('2016-12-15') instanceof Date
        parser('2016.12') instanceof Double
        parser('2016') instanceof Long
    }

}
