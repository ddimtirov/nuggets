**nuggets** is (yet another) utility library for Java. I have tried to include 
only non-trivial features, especially ones that I find I have needed repeatedly,
as well as features that make code easier to read and maintain. 

[![Build Status](https://travis-ci.org/ddimtirov/nuggets.svg?branch=master)](https://travis-ci.org/ddimtirov/nuggets)
[![codecov](https://codecov.io/gh/ddimtirov/nuggets/branch/master/graph/badge.svg)](https://codecov.io/gh/ddimtirov/nuggets) 
[![Dependency Status](https://www.versioneye.com/user/projects/57d2624987b0f6003c14ac1e/badge.svg?style=flat-square)](https://www.versioneye.com/user/projects/57d2624987b0f6003c14ac1e)

### Functionality:

See the [javadocs](https://ddimtirov.github.io/nuggets/javadoc/io/github/ddimitrov/nuggets/package-summary.html) for detailed documentation; 
browse the sources at [sourcegraph](https://sourcegraph.com/github.com/ddimtirov/nuggets@master).

- `Exceptions` - utils for [dealing with checked exceptions](https://kotlinlang.org/docs/reference/exceptions.html#checked-exceptions)
  and [huge stacktraces](https://dzone.com/articles/filtering-stack-trace-hell)
  - rethrow checked exception as unchecked
  - enable the usage of throwing methods in not-throwing lambdas
  - enrich exception message without wrapping it
  - cleanup exception stacktrace, causes and suppressed exceptions
    - preset stack transformer for Groovy
    - more on the way.
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
- no 3rd party dependencies at runtime
- high quality testing and javadoc
- annotated with the JetBrains annotations (helps if you use IntelliJ IDEA)
- the naming of static methods is chosen to be easily recognizable, 
  yet reduce the chance of clashing when imported statically.
- The performance should be reasonable, though that is not a requirement for now.
- Most of the classes are thread-safe, and thread safety and threading policies are
documented in the class and method Javadoc.
- Many of the classes are abusing reflection to flip the `accessible` bit on fields 
and methods. As usual, if you are using security manager you need to do your testing.

Finally, go ahead and read [Guava's philosophy](https://github.com/google/guava/wiki/PhilosophyExplained) 
document - not that it has much to do with this project (at least not yet), but it's a good read ;-)
