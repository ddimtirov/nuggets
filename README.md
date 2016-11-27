**nuggets** is (yet another) utility library for Java. I have tried to include 
only non-trivial features, especially ones that I find I have needed repeatedly,
as well as features that make code easier to read and maintain. 

[![Build Status](https://travis-ci.org/ddimtirov/nuggets.svg?branch=master)](https://travis-ci.org/ddimtirov/nuggets)
[![Codacy Grade](https://api.codacy.com/project/badge/Grade/0951cb36db314ff1bf69646402f4b988)](https://www.codacy.com/app/dimitar-dimitrov/nuggets?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ddimtirov/nuggets&amp;utm_campaign=Badge_Grade)
[![Codacy Coverage](https://api.codacy.com/project/badge/Coverage/0951cb36db314ff1bf69646402f4b988)](https://www.codacy.com/app/dimitar-dimitrov/nuggets?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ddimtirov/nuggets&amp;utm_campaign=Badge_Coverage)
[![codecov](https://codecov.io/gh/ddimtirov/nuggets/branch/master/graph/badge.svg)](https://codecov.io/gh/ddimtirov/nuggets) 
[![Dependency Status](https://www.versioneye.com/user/projects/57d2624987b0f6003c14ac1e/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/57d2624987b0f6003c14ac1e)

### Functionality:

Below is a list of features, see the [javadocs](https://ddimtirov.github.io/nuggets/javadoc/io/github/ddimitrov/nuggets/package-summary.html) for detailed documentation. 
<!-- browse the sources at [sourcegraph](https://sourcegraph.com/github.com/ddimtirov/nuggets@master). -->

- `Exceptions` - utils for [dealing with checked exceptions](https://kotlinlang.org/docs/reference/exceptions.html#checked-exceptions)
  and [huge stacktraces](https://dzone.com/articles/filtering-stack-trace-hell)
  - rethrow checked exception as unchecked
  - enable the usage of throwing methods in not-throwing lambdas
  - enrich exception message without wrapping it
  - cleanup exception stacktrace, causes and suppressed exceptions
    - preset stack transformer that works for many situations
    - preset stack transformers for Groovy MOP, Java Reflection, etc.
    - stack transformer to get rid of some parasitic stackframes in Groovy
  - dump an exception stacktrace to multi-line string and parse it back 
- `Extractors` - reflection utils  
  - `peek` and `poke` to a private or final field  
  - convert between wrapper and primitive classes
  - create a default value for type (no-args constructor, zero, `false`, or `null`)
  - safely load potentially missing classes (useful for plugins and optional functionality)
  - dependency injection primitives (roll your custom DI injector in few lines)
- `TextTable` - formats complex data in logs, stdout and file reports
  - rendering to any `Appendable` object (`StringBuilder`, `Writer`, etc.)
  - alignment, padding and custom formatting per column
  - default values for optional column
  - extensible visual styles
- Special Groovy API  
  - Use `Extractors` as Groovy [category](http://groovy-lang.org/metaprogramming.html#categories) 
    to decorate any object with `peekField()/pokeField()`, which can be used access private or 
    final fields as in this example: `foobar.peekField('finalField', Integer)`
  - Any `java.lang.Class` will gain `peekField()/pokeField()` as 
    [extension methods](http://groovy-lang.org/metaprogramming.html#_extension_modules)
    that can be used to access static private or final fields - i.e. 
    `SomeClass.pokeField('someFinalField', newValue)`
- Special Kotlin API
  - Transform Exceptions in more natural way, by adding `throwable.transform {...}`
    extension method. Added few extension methods to the transform builder.
  - Access encapsulated fields by using `clazz.peek/pokeStaticField(...)` 
    and `instance.peek/pokeField(...)`
  - Added `col(name) { ... }` extension method for more natural config 
    of a table column.    
### Non-functional Features:
- Fat-jar friendly (don't add yet another jar to your distribution)
  - no 3rd party dependencies at runtime 
  - some optional features take advantage of `@Inject`, `Groovy` and `Kotlin` 
    if they happen to be on the class path
  - all components are designed with dependencies in mind, making it trivial 
    to strip the parts you don't use with something like `ProGuard`. 
- high quality testing and javadoc
- annotated with the JetBrains annotations (helps if you use IntelliJ IDEA)
- the naming of static methods is chosen to be easily recognizable, 
  yet reduce the chance of clashing when imported statically.
- The performance should be reasonable, though that is not a requirement for now.
- Most of the classes are thread-safe, and thread safety and threading policies are
  documented in the class and method Javadoc.
- Many of the classes are abusing reflection to flip the `accessible` bit on fields 
  and methods. As usual, if you are using security manager you need to do your testing.
