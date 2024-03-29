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

import io.github.ddimitrov.nuggets.internal.groovy.DelegatedClosure
import io.github.ddimitrov.nuggets.internal.groovy.NuggetsExtensions
import io.github.ddimitrov.nuggets.internal.groovy.NuggetsStaticExtensions
import org.junit.jupiter.api.Test

@SuppressWarnings(["GroovyResultOfObjectAllocationIgnored", "GroovyAccessibility"])
class FullLineCoveragePadding {
    @Test void constructors() {
        new Functions()
        new NuggetsExtensions()
        new NuggetsStaticExtensions()
        new UrlStreamHandlers()
        new Exceptions()
        new Extractors()
        new Functions()
        new ExceptionTransformerBuilder()
        new DispatchException()
        assert new TextTable.DataBuilder(true, Collections.singletonList(new TextTable.Column('dummy', 0))).toString()!=null
    }
    @Test void delegates() {
        def c = new DelegatedClosure({ Object... it ->}) {}
        c.asWritable()
        c.dehydrate()
        c.rehydrate(c.delegate, c.owner, c.getThisObject())
        c.clone()
        !c.isCase("dummy")
        c.getParameterTypes().length==1
        c.getMaximumNumberOfParameters()==1
        c.setProperty "delegate", c.getProperty("delegate")
        c.setResolveStrategy      c.getResolveStrategy()
        c.setDelegate             c.getDelegate()
        c.setDirective            c.getDirective()
    }
    @Test void trivial() {
        int foo = Functions.ret(100, System.&gc )
        Functions.retnul(System.&gc)
    }
}
