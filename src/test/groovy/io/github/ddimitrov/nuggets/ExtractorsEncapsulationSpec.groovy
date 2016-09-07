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

@Title("Extractors :: Breaking encapsulation")
@Subject(Extractors)
class ExtractorsEncapsulationSpec extends Specification {
    void cleanup() {
        EncapsulationDomain.resetConstants()
    }

    def "use peek to look at any instance fields, including private"() {
        setup:
        def x = new EncapsulationDomain(666, 999)

        expect: 'for instance fields, typically we can skip the class type'
        Extractors.peekField(x, 'finalField', Integer)==666
        Extractors.peekField(x, 'privateFinalField', Integer)==999
        Extractors.peekField(x, 'privateField', Integer)==8
    }

    def "use peek with target type to look at shadowed fields"() {
        setup:
        def x = new EncapsulationDomain.ShadowingSubclass(666, 999, 6, 9)

        expect: 'without type, peekField() returns the shadowed fields'
        Extractors.peekField(x, 'finalField', Integer)==6
        Extractors.peekField(x, 'privateFinalField', Integer)==9
        Extractors.peekField(x, 'privateField', Integer)==2

        and: 'in practice that is the same as using the actual type of `x`'
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'finalField', Integer)==6
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateFinalField', Integer)==9
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateField', Integer)==2

        and: 'we can access the shadowed fields by specifying the class which holds them'
        Extractors.peekField(x, EncapsulationDomain, 'finalField', Integer)==666
        Extractors.peekField(x, EncapsulationDomain, 'privateFinalField', Integer)==999
        Extractors.peekField(x, EncapsulationDomain, 'privateField', Integer)==8

        and: 'for unshadowed fields doesn`t really matter as long as the class has access to the field'
        Extractors.peekField(x, 'unshadowed', Integer)==5
        Extractors.peekField(x, EncapsulationDomain, 'privateField', Integer)==8
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'unshadowed', Integer)==5

    }

    def "use peek with null target and non-null target type to look at static fields"() {
        expect:
        Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', Integer)==42
        Extractors.peekField(null, EncapsulationDomain, 'finalStaticField', Object).class==Object
        Extractors.peekField(null, EncapsulationDomain, 'privateInlineableConstantField', Integer)==12
        Extractors.peekField(null, EncapsulationDomain, 'privateFinalStaticField', Object).class==Object
    }

    def "use peek to get the outer instance of an inner class"() {
        setup:
        def outer = new EncapsulationDomain(666, 999)
        def x = outer.createInner()

        expect:
        Extractors.peekField(x, 'this$0', EncapsulationDomain)==outer
    }

    def "when looking up primitive types, need to use the wrapper for the lookup type"() {
        expect: 'looking up an `int` field as `Integer` everything works fine'
        42==Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', Integer)

        when: 'if we try to look up the field by its real type (`int`)'
        Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', int)

        then: 'an exception will be thrown because the lookup function can not return an int as a generic return value (erased to `Object` at runtime)'
        IllegalArgumentException e = thrown()
        e.message=~ 'boxed'
    }

    def "peeking at a missing field, results in exception from the Reflection API"() {
        when: 'mistyped field'
        Extractors.peekField(null, EncapsulationDomain, 'blah', Integer)

        then:
        NoSuchFieldException e = thrown()
        e.message == 'blah'
    }

    def "peeking at a field with wrong type, results in undeclared ClassCastException"() {
        when: Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', Long)

        then:
        ClassCastException e = thrown()
        e.message == 'Cannot cast java.lang.Integer to java.lang.Long'
    }

    def "peeking at an instance field without instance, results in NPE"() {
        when: Extractors.peekField(null, EncapsulationDomain, 'finalField', Integer)

        then:
        NullPointerException e = thrown()
        e.message==null
    }

    def "use poke to change any instance fields, including final or private"() {
        setup:
        def x = new EncapsulationDomain(-1, -2)

        when:
        Extractors.pokeField(x, 'finalField', 666)
        Extractors.pokeField(x, 'privateFinalField', 999)
        Extractors.pokeField(x, 'privateField', 8)

        then: 'for instance fields, typically we can skip the class type'
        Extractors.peekField(x, 'finalField', Integer)==666
        Extractors.peekField(x, 'privateFinalField', Integer)==999
        Extractors.peekField(x, 'privateField', Integer)==8
    }
    def "use poke with target type to modify shadowed fields"() {
        setup:
        def x = new EncapsulationDomain.ShadowingSubclass(-1, -1, -1, -1)

        when: 'updating fields without class, we target the most derived shadowed version'
        // this:                                           // is the same as this:
        Extractors.pokeField(x, 'finalField', 6)           // Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'finalField', Integer)==6
        Extractors.pokeField(x, 'privateFinalField', 9)    // Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateFinalField', Integer)==9
        Extractors.pokeField(x, 'privateField', 2)         // Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateField', Integer)==2

        and: 'we can access the shadowed fields by specifying the class which holds them'
        Extractors.pokeField(x, EncapsulationDomain, 'finalField', 666)
        Extractors.pokeField(x, EncapsulationDomain, 'privateFinalField', 999)
        Extractors.pokeField(x, EncapsulationDomain, 'privateField', 8)

        then:
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'finalField', Integer)==6
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateFinalField', Integer)==9
        Extractors.peekField(x, EncapsulationDomain.ShadowingSubclass, 'privateField', Integer)==2
        Extractors.peekField(x, EncapsulationDomain, 'finalField', Integer)==666
        Extractors.peekField(x, EncapsulationDomain, 'privateFinalField', Integer)==999
        Extractors.peekField(x, EncapsulationDomain, 'privateField', Integer)==8
    }

    def "use poke with null target and non-null target type to modify static fields"() {
        expect: 'initial values'
        Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', Integer)==42
        Extractors.peekField(null, EncapsulationDomain, 'finalStaticField', Object).class==Object
        Extractors.peekField(null, EncapsulationDomain, 'privateInlineableConstantField', Integer)==12
        Extractors.peekField(null, EncapsulationDomain, 'privateFinalStaticField', Object).class==Object

        when:
        Extractors.pokeField(null, EncapsulationDomain, 'inlineableConstantField', 19)
        Extractors.pokeField(null, EncapsulationDomain, 'finalStaticField', new Date())
        Extractors.pokeField(null, EncapsulationDomain, 'privateInlineableConstantField', 36)
        Extractors.pokeField(null, EncapsulationDomain, 'privateFinalStaticField', "foobar")

        then: 'modified values'
        Extractors.peekField(null, EncapsulationDomain, 'privateFinalStaticField', Object).class==String
        Extractors.peekField(null, EncapsulationDomain, 'finalStaticField', Object).class==Date
        Extractors.peekField(null, EncapsulationDomain, 'privateInlineableConstantField', Integer)==36
        Extractors.peekField(null, EncapsulationDomain, 'inlineableConstantField', Integer)==19

        and: 'BEWARE: references to constants are inlined in Java code, but not in Groovy code'
        EncapsulationDomain.publicConstant==42
        EncapsulationDomain.privateConstant==12
        //noinspection GroovyAccessibility
        EncapsulationDomain.privateInlineableConstantField==36
        EncapsulationDomain.inlineableConstantField==19

    }

  def "poking a missing field, results in exception from the Reflection API"() {
        when: 'mistyped field'
        Extractors.pokeField(null, EncapsulationDomain, 'blah', 5)

        then:
        NoSuchFieldException e = thrown()
        e.message == 'blah'
    }

    def "poking a field with wrong type, results in undeclared ClassCastException"() {
        when: Extractors.pokeField(null, EncapsulationDomain, 'inlineableConstantField', 'abc')

        then:
        IllegalArgumentException e = thrown()
        e.message ==~ 'Can not set static int field .+inlineableConstantField to java.lang.String'
    }

    def "poking at an instance field without instance, results in NPE"() {
        when: Extractors.peekField(null, EncapsulationDomain, 'finalField', Integer)

        then:
        NullPointerException e = thrown()
        e.message==null
    }
}
