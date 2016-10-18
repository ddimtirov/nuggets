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

package io.github.ddimitrov.nuggets.internal.kotlin

import io.github.ddimitrov.nuggets.ExceptionTransformerBuilder
import io.github.ddimitrov.nuggets.Extractors
import io.github.ddimitrov.nuggets.TextTable
import kotlin.reflect.KClass

fun ExceptionTransformerBuilder.filterStackFramesForClass(classPattern: Regex) = filterStackFramesForClass(classPattern.toPattern())
fun ExceptionTransformerBuilder.unwrapThese(vararg exceptionClasses: KClass<out Throwable>) = unwrapThese(*exceptionClasses.map { it.java }.toTypedArray())
inline fun <reified T: Throwable> ExceptionTransformerBuilder.unwrap() = unwrapThese(T::class)

fun Throwable.transform(transformationSpec: ExceptionTransformerBuilder.()->Unit) : Throwable {
    val builder = ExceptionTransformerBuilder()
    builder.transformationSpec()
    val transformation = builder.build()
    return transformation.apply(this)
}

// Ideally peek/pokeStaticField would have been companion extension, but as of Kotlin 1.1 that is not possible
// see http://stackoverflow.com/questions/28210188/static-extension-methods-in-kotlin#comment55718346_33853233
inline fun <reified T: Any> Class<*>.peekStaticField(fieldName: String): T =
        Extractors.peekField(null, this, fieldName, T::class.java)

inline fun <reified T: Any> KClass<*>.peekStaticField(fieldName: String): T =
        java.peekStaticField(fieldName)

fun Class<*>.pokeStaticField(fieldName: String, value: Any?) =
        Extractors.pokeField(null, this, fieldName, value)

fun KClass<*>.pokeStaticField(fieldName: String, value: Any?) =
        java.pokeStaticField(fieldName, value)

inline fun <reified T: Any, reified R: Any> R.peekField(fieldName: String ): T =
        Extractors.peekField(this, R::class.java, fieldName, T::class.java)

inline fun <reified R: Any> R.pokeField(fieldName: String, value: Any?) =
        Extractors.pokeField(this, R::class.java, fieldName, value)

fun TextTable.LayoutBuilder.col(columnName: String, config: TextTable.Column.()->Unit): TextTable.LayoutBuilder =
        column(columnName) { it.config() }
