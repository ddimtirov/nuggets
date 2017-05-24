/*
 *    Copyright 2017 by Dimitar Dimitrov
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


@Title("Reflection Proxy usage")
@Subject(ReflectionProxy)
@SuppressWarnings("GroovyAccessibility")
class ReflectionProxySpec extends Specification {
    def o = new EncapsulationDomain.ShadowingSubclass(1, 2, 3, 4);

    def "Java style"() {
        given:@Subject rp = ReflectionProxy.wrap(o)

        when: def hash = rp.get("privateFinalStaticField").invoke("hashCode").unwrap(Integer)
        then: hash!=0

        when: def pff = rp.get("privateFinalField").unwrap(Integer)
        and:  def pffParent = rp.resolvingAtType(EncapsulationDomain).get("privateFinalField").unwrap() as int
        then: pffParent==2 & pff==4

        when: rp.tap { it.set("privateField", 3) }
        and:  def x = rp.map{ "foo"+it.get("privateField").unwrap() }.unwrap()
        then: o.privateField==3 & x=="foo3"
    }

    def "Groovy style - extension methods"() {
        given:@Subject rp = ReflectionProxy.wrap(o)

        when: def pff = rp["privateFinalField"] as int
        and:  def pffParent = rp[EncapsulationDomain]["privateFinalField"] as int
        then: pffParent==2 & pff==4

        when: def hash = rp["privateFinalStaticField"].invoke('hashCode') as int
        then: hash!=0

        when: rp.tap { it["privateField"]=3 }
        and:  def x = rp.map {"foo"+it.get("privateField").unwrap() }.unwrap()
        then: o.privateField==3 & x=="foo3"
    }

    def "Groovy DSL style"() {
        given:@Subject rp = o.reflectionDsl()

        when: def pff = rp.privateFinalField as int
        and:  def pffParent = rp[EncapsulationDomain].privateFinalField as int
        then: pffParent==2 & pff==4

        when: def hash = rp.privateFinalStaticField.hashCode() as int
        then: hash!=0

        when: rp.privateField=3
        and:  def x = "foo"+rp.privateField
        then: o.privateField==3 & x=="foo3"
    }

    def "equality"() {
        expect:
        ReflectionProxy.wrap(o)==ReflectionProxy.wrap(o)
        ReflectionProxy.wrap(o)!=ReflectionProxy.wrap(o).resolvingAtType(Object)
        ReflectionProxy.wrap(o).hashCode()==ReflectionProxy.wrap(o).hashCode()
        ReflectionProxy.wrap(o).hashCode()==ReflectionProxy.wrap(o).resolvingAtType(Object).hashCode()
        ReflectionProxy.wrap(o).resolvingAtType(Object).toString().startsWith ReflectionProxy.wrap(o).toString()
        and:
        o.reflectionDsl()==o.reflectionDsl()
        o.reflectionDsl()!=o.reflectionDsl()[Object]
        o.reflectionDsl().hashCode()==o.reflectionDsl().hashCode()
        o.reflectionDsl().hashCode()==o.reflectionDsl()[Object].hashCode()
        o.reflectionDsl()[Object].toString().startsWith o.reflectionDsl().toString()
    }
}