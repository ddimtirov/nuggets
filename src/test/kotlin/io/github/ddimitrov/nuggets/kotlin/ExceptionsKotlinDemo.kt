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

package io.github.ddimitrov.nuggets.kotlin

import io.github.ddimitrov.nuggets.TextTable
import io.github.ddimitrov.nuggets.internal.kotlin.*
import java.lang.reflect.InvocationTargetException
import java.sql.Time
import java.util.*
import java.util.concurrent.ExecutionException
import kotlin.test.assertTrue


fun demonstrateExceptionManipulation() : Unit = throw Exception().transform {
    var summary = 0
    unwrap                          <ExecutionException>()
    unwrapThese                     (RuntimeException::class, InvocationTargetException::class)
    unwrapWhen                      { it is RuntimeException && (it.message?.length?:0>0 || it.message==it.cause.toString()) }
    replaceStackTrace               { summary=it.size; if (it.size%2==1) emptyArray() else it }
    replaceMessage                  { "${it.message} insightful summary ($summary frames truncated)" }
    filterStackFramesForClass       (Regex("org\\.spockframework\\.(runtime|util)\\..*"))
    filterStackFramesForClass       (Regex("(sun|java\\.lang)\\.reflect\\..*"))
    filterStackFramesForClassPrefix ("org.junit.")
    filterStackFramesForClassPrefix ("org.codehaus.groovy.")
}

fun demonstrateReflectionAccess() : Unit {
    val d = Time(42)

    val dss1: Int = Date::class.java.peekStaticField("defaultCenturyStart")
    val dss2 = Date::class.java.peekStaticField<Int>("defaultCenturyStart")
    val dss3: Int = Date::class.peekStaticField("defaultCenturyStart")
    val dss4 = Date::class.peekStaticField<Int>("defaultCenturyStart")
    Date::class.java.pokeStaticField("defaultCenturyStart", dss1)
    Date::class.pokeStaticField("defaultCenturyStart", dss1)

    val fastTime1: Long = d.peekField("fastTime")
    val fastTime2: Long = (d as Date).peekField("fastTime")

    d.pokeField("fastTime", fastTime1)
    (d as Date).pokeField("fastTime", fastTime1)

    assertTrue { dss1==dss2 && dss3!=dss4 && fastTime1 == fastTime2 }
}

fun demonstrateTextTable() {
    TextTable.withColumns("foo", "bar")
            .col("baz") { alignment=1.0 }
            .col("qux") { defaultValue="n/a" }
            .col("barf") { defaultValue="n/a"; padding=2 }
            .withData()
            .row(1, 2, 3, "foobar", "bazqux")
            .row(1, 2, 3, null, "bazqux")
            .buildTable()
            .format(0, StringBuilder())
}