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

import io.github.ddimitrov.nuggets.internal.groovy.NuggetsExtensions
import spock.lang.Specification

import java.text.SimpleDateFormat
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Function

class FunctionsSpec extends Specification {
    private static final NAME_TO_VALIDATOR_RESULT = [
            [null, NullPointerException],
            ['', IllegalArgumentException],
            ['   ', IllegalArgumentException],
            ['foo', null],
            ['f&o', null],
            ['f o', null],
            [' f o', IllegalArgumentException],
            ['f o ', IllegalArgumentException],
    ]

    def "named functions from Java"() {
        when:
        def sequence = FunctionsJavaUsage.createNamedSequence("sequence")
        def eatIt = FunctionsJavaUsage.createNamedConsumer("eat it")
        def eatTwo = FunctionsJavaUsage.createNamedBiConsumer("eat two")
        def isOdd = FunctionsJavaUsage.createNamedParityTester("is odd")
        def isFactor = FunctionsJavaUsage.createNamedFactorTester("is factor")
        def doubler = FunctionsJavaUsage.createNamedDoubler("doubler")
        def multiplier = FunctionsJavaUsage.createNamedMultiplier("multiplier")

        then:
        sequence.toString() == "sequence"
        eatIt.toString() == "eat it"
        eatTwo.toString() == "eat two"
        isOdd.toString() == "is odd"
        isFactor.toString() == "is factor"
        doubler.toString() == "doubler"
        multiplier.toString() == "multiplier"

        and:
        sequence.get()==0 & sequence.get()==1 & sequence.get()==2
        eatIt.accept("foo")==null & eatTwo.accept('foo', 'bar')==null
        isOdd.test(13) & !isOdd.test(12)
        isFactor.test(12, 4) & !isFactor.test(12, 5)
        doubler.apply(3)==6 & doubler.apply(7)==14
        multiplier.apply(3, 3)==9 & multiplier.apply(3, 7)==21
    }

    def "names need to be trimmed and non-empty"(String name, Class<? extends Exception> ex, boolean testGroovy) {
        expect:
        try {
            def f = testGroovy ? {}.named(name)
                    : FunctionsJavaUsage.createNamedSequence(name)
            assert ex==null & f.toString()==name
        } catch(Throwable e) {
            assert ex!=null && e
        }

        where:
        //noinspection GroovyAssignabilityCheck
        [name, ex, testGroovy] << NAME_TO_VALIDATOR_RESULT.collectMany { [
                it + [true],
                it + [false]
        ]}.iterator()
    }

    @SuppressWarnings(["GroovyAccessibility", "GroovyAssignabilityCheck"])
    def "test name-check exception Java"(String name, Class<? extends Exception> ex) {
        when:
        Functions.checkName(name)

        then:
        thrown(IllegalArgumentException)

        where: // only non-null names, or the Jetbrains annotation assertions will trigger in IDEA
        [name, ex] << NAME_TO_VALIDATOR_RESULT.findAll { it[0]!=null && it[1]!=null }.iterator()
    }

    @SuppressWarnings(["GroovyUnusedAssignment", "GroovyAssignabilityCheck"])
    def "test name-check exception Groovy"(String name, Class<? extends Exception> ex) {
        expect:
        try {
            def x = NuggetsExtensions.named(null, name)
        } catch (AssertionError e) { // when IDEA generates its assertions
            assert e.message ==~ ~/Argument 1 for @Pattern parameter of .*NuggetsExtensions\.named does not match pattern .*/
        } catch (e) {
            assert e.class==ex
        }

        where: // only non-null names, or the Jetbrains annotation assertions will trigger in IDEA
        [name, ex] << NAME_TO_VALIDATOR_RESULT.findAll { it[0]!=null && it[1]!=null }.iterator()
    }

    def "named closures from Groovy"() {
        given:
        def i = new AtomicInteger()
        def doubledSequence = { -> i.getAndIncrement()*2 }

        when: def namedClosure = doubledSequence.named("doubled sequence")
        then: namedClosure.toString() == "doubled sequence"

        when: 'we pass in a supplier, it is evaluated every time, providing up-to-date description'
        def describedClosure = doubledSequence.named { "doubled sequence (next ${i.get()*2})" }

        then: "the generator's toString() lets us know what is the next value"
        describedClosure.toString()=="doubled sequence (next 0)"
        describedClosure.toString()=="doubled sequence (next 0)"
        describedClosure()==0
        describedClosure.toString()=="doubled sequence (next 2)"
        describedClosure()==2
        describedClosure.toString()=="doubled sequence (next 4)"
        describedClosure.toString()=="doubled sequence (next 4)"
        describedClosure()==4
        describedClosure.toString()=="doubled sequence (next 6)"
    }


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
