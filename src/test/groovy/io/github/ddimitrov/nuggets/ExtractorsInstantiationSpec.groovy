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
import spock.lang.Subject
import spock.lang.Title

import javax.sql.RowSet
import java.lang.reflect.Constructor

@Title("Extractors :: Instantiation and injection")
@Subject(Extractors)
class ExtractorsInstantiationSpec extends Specification {
    def "load class if present - detect optional parts"() {
        when:
        def groovyObject = Extractors.getClassIfPresent('groovy.lang.GroovyObject')
        def thymeleaf = Extractors.getClassIfPresent('org.thymeleaf.Thymeleaf')

        then: // we have Groovy on the classpath, but not Thymeleaf
        groovyObject!=null
        thymeleaf==null
    }

    def "converting between boxed and unboxed classes"() {
        expect: 'boxing converts any primitive to wrapper class'
        Extractors.boxClass(int)       == Integer
        Extractors.boxClass(void)      == Void
        Extractors.boxClass(Integer)   == Integer
        Extractors.boxClass(Object)    == Object

        and: 'unboxing converts any wrapper class to primitive'
        Extractors.unboxClass(int)     == int
        Extractors.unboxClass(Void)    == void.class
        Extractors.unboxClass(Integer) == int
        Extractors.unboxClass(Object)  == Object
    }

    @SuppressWarnings(["GrEqualsBetweenInconvertibleTypes", "GroovyAssignabilityCheck"])
    def "supplying default value for type"() {
        expect: 'for numbers return zero'
        Extractors.defaultValue(byte) == (byte) 0
        Extractors.defaultValue(short) == (short) 0
        Extractors.defaultValue(int) == 0
        Extractors.defaultValue(long) == 0L
        Extractors.defaultValue(float) == 0f
        Extractors.defaultValue(double) == 0d
        Extractors.defaultValue(Byte) == (byte) 0
        Extractors.defaultValue(Short) == (short) 0
        Extractors.defaultValue(Integer) == 0
        Extractors.defaultValue(Long) == 0L
        Extractors.defaultValue(Float) == 0f
        Extractors.defaultValue(Double) == 0d
        Extractors.defaultValue(BigDecimal) == BigDecimal.ZERO
        Extractors.defaultValue(BigInteger) == BigInteger.ZERO

        and: 'for character - return NUL'
        Extractors.defaultValue(Character) == (char) '\0'
        Extractors.defaultValue(char) == (char) '\0'

        and: 'for boolen - return false'
        Extractors.defaultValue(Boolean) == Boolean.FALSE
        !Extractors.defaultValue(boolean)

        and: 'for classes with default constructor - instantiate'
        Extractors.defaultValue(Date) instanceof Date

        and: 'for collections - pick ordered hash, or array-based implementation'
        Extractors.defaultValue(Map)          instanceof LinkedHashMap
        Extractors.defaultValue(Collection)   instanceof ArrayList
        Extractors.defaultValue(List)         instanceof ArrayList
        Extractors.defaultValue(AbstractList) instanceof ArrayList
        Extractors.defaultValue(LinkedList)   instanceof LinkedList
        Extractors.defaultValue(Set)          instanceof LinkedHashSet


        and: 'for arrays - return zero elements'
        Extractors.defaultValue(int[].class)== new int[0]
        Extractors.defaultValue(int[][].class)== new int[0][0]
        Extractors.defaultValue(String[].class)== new String[0]
        Extractors.defaultValue(String[][].class)== new String[0][0]

        and: 'for classes with no constructor and not covered by other special cases - return null'
        Extractors.defaultValue(RowSet) == null
    }

    def "find @Inject annotated injectable constructors"() {
        def allConstructors = InjectableDomain.WithAnnotatedConstructor.class.declaredConstructors

        when:
        def signatureForceAnnotated = Extractors.findInjectableConstructor(true, allConstructors)?.parameterTypes as List
        def signatureDefault = Extractors.findInjectableConstructor(false, allConstructors)?.parameterTypes as List

        then:
        signatureForceAnnotated==[Date]
        signatureDefault==[Date]
    }

    def "find injectable constructors on non-annotated classes"() {
        def allConstructors = InjectableDomain.NoAnnotatedConstructor.class.declaredConstructors

        when: 'we request an injectable constructor without requiring it to be annotated'
        def signatureDefault = Extractors.findInjectableConstructor(false, allConstructors)?.parameterTypes as List

        then: 'we get the one with most parameters'
        signatureDefault==[Date, String]

        when: 'we require it is annotated'
        Extractors.findInjectableConstructor(true, allConstructors)?.parameterTypes as List

        then: 'an exception is thrown if none available'
        NoSuchMethodException e = thrown()
        e.message =~ 'No injectable constructor found in:'

    }

    def "fail if more than one @Inject annotated constructors"() {
        def allConstructors = InjectableDomain.WithManyAnnotatedConstructors.class.declaredConstructors

        when:
        Extractors.findInjectableConstructor(false, allConstructors)

        then:
        NoSuchMethodException e = thrown()
        e.message =~ 'Ambiguous injectable constructor:'
    }

    def "fail if no constructors"() {
        when:
        Extractors.findInjectableConstructor(false, new Constructor<?>[0])

        then:
        NoSuchMethodException e = thrown()
        e.message == 'Need at least one candidate for injectable constructor!'
    }

    def "find injectable methods"(useCase, Class clazz, Collection<List<Class>> signatures) {
        when:
        def methods = Extractors.findInjectableMethods(clazz.declaredMethods)
        def methodsArgs = methods.collect { it.parameterTypes.toList() }

        then:
        methodsArgs.toSet() == signatures as Set

        where:
        useCase                      | clazz                                                || signatures
        'no annotated methods'       | InjectableDomain.NoAnnotatedConstructor.class        || []
        'public and private methods' | InjectableDomain.WithAnnotatedConstructor.class      || [[int], [Date]]
        'different number of args'   | InjectableDomain.WithManyAnnotatedConstructors.class || [[int, double], [Date]]
    }

    def "autowire by type"() {
        given: 'a constructor or method'
        def clazz = InjectableDomain.NoAnnotatedConstructor.class
        Constructor<InjectableDomain.NoAnnotatedConstructor> ctor = Extractors.findInjectableConstructor(false, clazz.declaredConstructors)

        when: 'build arguments for injectable signature'
        def args = Extractors.autowireByType(ctor) { type->
            switch (type) {
                case Date: return new Date(0)
                case String: return 'boom'
                default: assert false
            }
        }
        def instance = ctor.newInstance(args)

        then: 'the instance arguments are resolved by the closure provided'
        instance.foobar.time == 0
        instance.bazqux == 'boom'

    }

    @SuppressWarnings("GroovyPointlessBoolean")
    def "custom DI example"() {
        given: 'a configured simple injector'
        def xyzzy = Spy(DiDomain.XyzzyServiceImpl)
        def di = new SimpleDi();
        di.bindSingleton(DiDomain.IXyzzyService, xyzzy);
        di.bindSingleton(DiDomain.IFooBarStrategy, new DiDomain.FooBarLocalImpl());

        when: 'we instantiate, an application'
        def app = di.instantiate(DiDomain.MyApplication);

        and: 'hook custom lifecycle listeners'
        di.instantiated(DiDomain.XyzzyListener).forEach { xyzzy.addListener(it) }

        then:
        app.running == null
        app.foobar.doneIt == false
        app.foobar.notifs == [:]
        1 * xyzzy.addListener(_)

        when: 'we run the app'
        app.run();

        then:
        app.running == true
        app.foobar.doneIt == true
        app.foobar.notifs == [run: 1]

        when: 'gracefully close'
        di.instantiated(Closeable).forEach { it.close() }
        app.shutdown();

        then:
        1 * xyzzy.close()
        app.running == false
        app.foobar.doneIt == true
        app.foobar.notifs == [run: 1, done: 2]
    }

}
