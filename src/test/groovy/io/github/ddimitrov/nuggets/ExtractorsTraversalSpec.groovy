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

import static io.github.ddimitrov.nuggets.ExtractorsTraversalDomain.*

class ExtractorsTraversalSpec extends Specification {
    def "linearization has all parents of a class"(Class<?> c, List<Class<?>> linearization) {
        expect:
        Extractors.linearized(c).asList()==linearization

        where:
        c  | linearization
        CA | [CA, C4, C3, C2, C1, I4, I2, I3, I1, Object ]
        CB | [CB, C3, C2, C1, I3, I2, I1, Object ]
        CC | [CC, I1, I2, Object ]
        I4 | [I4, I2, I3, I1]
    }

    def "every supertype's lineraization is a strict subset of subtype's linearization"(Class<?> type) {
        when:
        def linearized = Extractors.linearized(type).asCollection()

        then:
        for (supertype in linearized) {
            def stl = Extractors.linearized(supertype).asCollection()
            assert linearized.containsAll(stl) & (stl.size()<linearized.size() | supertype==type )
        }

        where:
        type << [CA, CB, CC, I1, I2, I3, I4, C1, C2, C3, C4]
    }

    // would be nice if we can test the specificity ordering, but I can't think of a good oracle

    def "iteration through accessible constructors"(Class type, List<String> expectedSignatures) {
        expect:
        Extractors.eachAccessible({ it.declaredConstructors }, type, null) {
            def signature = it.toString() - type.enclosingClass.name
            assert expectedSignatures.remove(signature) : "missing signature for $type.simpleName"
        }
        assert expectedSignatures.empty : "extra signatures for $type.simpleName"

        where:
        type | expectedSignatures
        CA   | ['public $CA()', 'public $C4(int,double)', 'protected $C3(int)', '$C2()', '$C2(int)', '$C2(double)', 'protected $C1(int)']
        CB   | ['public $CB()', 'public $CB(int)', 'protected $C3(int)', '$C2()', '$C2(int)', '$C2(double)', 'protected $C1(int)']
        CC   | ['$CC()']
        C1   | ['protected $C1(int)']
        C2   | ['$C2()', '$C2(int)', '$C2(double)', 'protected $C1(int)']
        C3   | ['protected $C3(int)', '$C2()', '$C2(int)', '$C2(double)', 'protected $C1(int)']
        C4   | ['public $C4(int,double)', 'protected $C3(int)', '$C2()', '$C2(int)', '$C2(double)', 'protected $C1(int)']
        I1   | [] // interfaces have no constructors
        I2   | []
        I3   | []
        I4   | []
    }

    def "iteration through accessible fields"(Class type, List<String> expectedSignatures) {
        expect:
        Extractors.eachAccessible({ it.declaredFields }, type, null) {
            def signature = it.toString() - type.enclosingClass.name
            assert expectedSignatures.remove(signature) : "missing signature for $type.simpleName"
        }
        assert expectedSignatures.empty : "extra signatures for $type.simpleName"

        where:
        type | expectedSignatures
        CA   | ['private final int $C3.i', 'private final int $C1.i']
        CB   | ['private final int $C3.i', 'private final int $C1.i']
        CC   | []
        C1   | ['private final int $C1.i']
        C2   | ['private final int $C1.i']
        C3   | ['private final int $C3.i', 'private final int $C1.i']
        C4   | ['private final int $C3.i', 'private final int $C1.i']
        I1   | []
        I2   | []
        I3   | []
        I4   | []
    }


    def "iteration through accessible methods"(Class type, List<String> expectedSignatures) {
        expect:
        Extractors.eachAccessible({ it.declaredMethods }, type, null) {
            def signature = it.toString() - type.enclosingClass.name
            assert expectedSignatures.remove(signature) : "missing signature for $type.simpleName"
        }
        assert expectedSignatures.empty : "extra signatures for $type.simpleName"

        where:
        type | expectedSignatures
        CA   | ['public void $CA.bing()', 'private static void $C3.foo()', 'private void $C3.bazz()', 'public void $C2.bang()', 'public void $C2.bong()', 'private static void $C1.foo()']
        CB   | ['public void $CB.bing()', 'public void $CB.beng()', 'private static void $C3.foo()', 'private void $C3.bazz()', 'public void $C2.bang()', 'public void $C2.bong()', 'private static void $C1.foo()']
        CC   | ['public java.lang.String $CC.toString()', 'public void $CC.bang()', 'public void $CC.bong()']
        C1   | ['private static void $C1.foo()']
        C2   | ['public void $C2.bang()', 'public void $C2.bong()', 'private static void $C1.foo()']
        C3   | ['private static void $C3.foo()', 'private void $C3.bazz()', 'public void $C2.bang()', 'public void $C2.bong()', 'private static void $C1.foo()']
        C4   | ['private static void $C3.foo()', 'private void $C3.bazz()', 'public void $C2.bang()', 'public void $C2.bong()', 'private static void $C1.foo()']
        I1   | ['public abstract void $I1.bang()', 'public abstract void $I1.bong()']
        I2   | []
        I3   | ['public abstract void $I3.bing()', 'public abstract void $I3.bang()', 'public default void $I3.beng()']
        I4   | []
    }


    def "iteration through accessible methods with stop class"() {
        when:
        def signatures = []
        Extractors.eachAccessible({ it.declaredMethods }, C3, C2) {
            signatures << it.toString() - C3.enclosingClass.name
        }
        then:
        signatures==['private static void $C3.foo()', 'private void $C3.bazz()'] & !signatures.any { it.contains('$C1') }
    }
    def "iteration through accessible objects can throw exceptions"() {
        when:
        Extractors.eachAccessible({ it.declaredMethods }, C3, C2) {
            throw new IOException("test")
        }
        then:
        IOException e = thrown()
        e.message == 'test'
    }
}
