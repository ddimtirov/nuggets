**nuggets** is (yet another) utility library for Java. I have tried to include 
only non-trivial features, especially ones that I find I have needed repeatedly,
as well as features that make code easier to read and maintain. 

Licensed under the Apache License, Version 2.0

[![Build Status](https://travis-ci.org/ddimtirov/nuggets.svg?branch=master)](https://travis-ci.org/ddimtirov/nuggets)
[![codecov](https://codecov.io/gh/ddimtirov/nuggets/branch/master/graph/badge.svg)](https://codecov.io/gh/ddimtirov/nuggets)
[sourcegraph](https://sourcegraph.com/github.com/ddimtirov/nuggets@master) 
| [codeclimate](https://codeclimate.com/github/ddimtirov/nuggets)
| [javadocs](https://ddimtirov.github.io/nuggets/javadoc/).

### Functionality:

For detailed documentation, see the project's [javadocs](https://ddimtirov.github.io/nuggets/javadoc/).
- `Exceptions` - utils for [dealing with checked exceptions](https://kotlinlang.org/docs/reference/exceptions.html#checked-exceptions)
  and [huge stacktraces](https://dzone.com/articles/filtering-stack-trace-hell)
  - rethrow checked exception as unchecked
  - enable the usage of throwing methods in not-throwing lambdas
  - enrich exception message without wrapping it
  - cleanup exception stacktrace, causes and suppressed exceptions
    - preset stack transformer for Groovy
    - more on the way.
  - dump an exception to multi-line string
- `Extractors` - reflection utils  
  - `peek` and `poke` to a private or final field  
  - convert between wrapper and primitive classes
  - create a default value for type (no-args constructor, zero, `false`, or `null`)
  - safely load potentially missing classes (useful for plugins and optional functionality)
  - dependency injection primitives (roll your custom DI injector in few lines)
  

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
