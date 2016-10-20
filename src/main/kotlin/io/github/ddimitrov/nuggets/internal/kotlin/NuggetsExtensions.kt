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

/**
 * Removes stack frames whose [StackTraceElement.getClassName] matches the specified pattern.
 *
 * @receiver an instance of a transformer builder
 * @param classPattern classes of stack frames to remove.
 * @return the same builder instance for chaining
 * @throws IllegalStateException if the transform function has already been built.
 * @see transform for example.
 */
fun ExceptionTransformerBuilder.filterStackFramesForClass(classPattern: Regex) = filterStackFramesForClass(classPattern.toPattern())

/**
 * Specifies to remove wrapped exceptions of certain classes. Useful when using multiple-layers,
 * where each layer wraps and rethrows an exception. If this method is called multiple
 * times, the specified conditions are OR-ed together.
 *
 * @receiver an instance of a transformer builder
 * @param exceptionClasses throwable is removed only if its class matches exactly one
 *                         of these specifief classes.
 * @return the builder instance for chaining
 * @throws IllegalStateException if the transform function has already been built.
 * @see transform for example.
 */
fun ExceptionTransformerBuilder.unwrapThese(vararg exceptionClasses: KClass<out Throwable>) = unwrapThese(*exceptionClasses.map { it.java }.toTypedArray())

/**
 * Specifies to remove wrapped exception of certain classes. Useful when using multiple-layers,
 * where each layer wraps and rethrows an exception. If this method is called multiple
 * times, the specified conditions are OR-ed together.
 *
 * @receiver an instance of a transformer builder
 * @param <T> throwable is removed only if its class matches.
 * @return the builder instance for chaining
 * @throws IllegalStateException if the transform function has already been built.
 * @see transform for example.
 */
inline fun <reified T: Throwable> ExceptionTransformerBuilder.unwrap() = unwrapThese(T::class)

/**
 * A shortcut way of creating an exception transformer and transforming an exception in one go.
 * Typically used when throwing or catching exceptions.
 * @receiver the exception to transform
 * @param <T> a function configuring the transform builder
 * @return the transformed exception
 */
// * @sample [io.github.ddimitrov.nuggets.kotlin.KotlinExtensionsTest.exceptionManipulation]
fun Throwable.transform(transformationSpec: ExceptionTransformerBuilder.()->Unit) : Throwable {
    val builder = ExceptionTransformerBuilder()
    builder.transformationSpec()
    val transformation = builder.build()
    return transformation.apply(this)
}

/**
 * Extract the value of a static field regardless of its visibility.
 *
 * @receiver the java class on which the field is declared.
 * @param fieldName the name of the field to extract.
 * @param T the expected type of the returned value. Needs to be a boxed (non-primitive) type.
 *
 * @return the extracted value of the requested field.
 * @throws IllegalArgumentException if the `T` is a primitive type.
 *
 * @see Extractors.getAccessibleField
 * @see peekField
 */
inline fun <reified T: Any> Class<*>.peekStaticField(fieldName: String): T =
        Extractors.peekField(null, this, fieldName, T::class.java)

/**
 * Extract the value of a static field regardless of its visibility.
 *
 * Note: ideally this method would have been a companion extension,
 * but as of Kotlin 1.1 that is [not possible](see http://stackoverflow.com/questions/28210188/static-extension-methods-in-kotlin#comment55718346_33853233).
 *
 * @receiver the Kotlin class on which the field is declared.
 * @param fieldName the name of the field to extract.
 * @param T the expected type of the returned value. Needs to be a boxed (non-primitive) type.
 *
 * @return the extracted value of the requested field.
 * @throws IllegalArgumentException if the `T` is a primitive type.
 *
 * @see Extractors.getAccessibleField
 * @see peekField
 */
inline fun <reified T: Any> KClass<*>.peekStaticField(fieldName: String): T =
        java.peekStaticField(fieldName)

/**
 * Modify the value of a static field regardless of its visibility or `final` modifier.
 *
 * @receiver the Java class on which to set the field.
 * @param fieldName the name of the field to set.
 * @param value the new value to set.
 *
 * @see Extractors.getAccessibleField
 * @see pokeField
 */
fun Class<*>.pokeStaticField(fieldName: String, value: Any?) =
        Extractors.pokeField(null, this, fieldName, value)

/**
 * Modify the value of a static field regardless of its visibility or `final` modifier.
 *
 * @receiver the Kotlin class on which to set the field.
 * @param fieldName the name of the field to set.
 * @param value the new value to set.
 *
 * @see Extractors.getAccessibleField
 * @see pokeField
 */
fun KClass<*>.pokeStaticField(fieldName: String, value: Any?) =
        java.pokeStaticField(fieldName, value)

/**
 * Extract the value of a field regardless of its visibility. If there are multiple shadowed fields with the
 * given name, return the value of the first found when traversing the inheritance hierarchy upwards,
 * starting from the `R` type, until `Object`.
 *
 * @receiver the object containing the data.
 * @param R the type from which to start the search for the field. See [Extractors.getAccessibleField] for the precise meaning.
 * @param fieldName the name of the field to extract.
 * @param T the expected type of the returned value. Needs to be a boxed (non-primitive) type.
 *
 * @return the extracted value of the requested field.
 * @throws IllegalArgumentException if the `T` is a primitive type.
 *
 * @see peekStaticField
 */
inline fun <reified T: Any, reified R: Any> R.peekField(fieldName: String ): T =
        Extractors.peekField(this, R::class.java, fieldName, T::class.java)

/**
 * Modify the value of a field regardless of its visibility or `final` modifier.
 * If there are multiple shadowed fields with the given name, set the value of the
 * first found when traversing the inheritance hierarchy starting from the `R` type,
 * until `Object`.
 *
 * @receiver the object on which to set the field.
 * @param R the type from which to start the search for the field. See [Extractors.getAccessibleField] for the precise meaning.
 * @param fieldName the name of the field to set.
 * @param value the new value to set.
 *
 * @see pokeStaticField
 */
inline fun <reified R: Any> R.pokeField(fieldName: String, value: Any?) =
        Extractors.pokeField(this, R::class.java, fieldName, value)

/**
 * Adds a column to the right, with the desired name and configurable attributes,
 * displaying the next data index.
 * @receiver a layout builder
 * @param columnName the name of the new column
 * @param config a function that can configure the column.
 * @return reference to `this` for chaining.
 */
fun TextTable.LayoutBuilder.col(columnName: String, config: TextTable.Column.()->Unit): TextTable.LayoutBuilder =
        column(columnName) { it.config() }
