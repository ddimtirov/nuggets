**nuggets** is (yet another) utility library for Java. **nuggets** is a sharp tool - 
it is you who uses it for good or for evil. **nuggets** is rubber gloves for those 
of us who need to dig deep in shitty codebases. **nuggets** is an experiment in 
transgressing the one-size-fits-all rules of thumb of "Effective Java". **nuggets** 
strives to maximize the [functionality-to-code](http://www.infovis-wiki.net/index.php/Data-Ink_Ratio) 
ratio, by providing a composable basis set of well chosen primitives, rather than 
catering explicitly for every usage scenario. **nuggets** is an aesthetic exercise -
from the Javadoc stylesheet, to the choice of infrastructure, I tinker and 
experiment until the result pleases me. 

[![Linux/OSX Build](https://travis-ci.org/ddimtirov/nuggets.svg?branch=master)](https://travis-ci.org/ddimtirov/nuggets)
[![Windows Build](https://ci.appveyor.com/api/projects/status/uruwl3u6eynnpok9/branch/master?svg=true)](https://ci.appveyor.com/project/ddimtirov/nuggets/branch/master)
[![Codacy Grade](https://api.codacy.com/project/badge/Grade/0951cb36db314ff1bf69646402f4b988)](https://www.codacy.com/app/dimitar-dimitrov/nuggets?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ddimtirov/nuggets&amp;utm_campaign=Badge_Grade)
[![Codacy Coverage](https://api.codacy.com/project/badge/Coverage/0951cb36db314ff1bf69646402f4b988)](https://www.codacy.com/app/dimitar-dimitrov/nuggets?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=ddimtirov/nuggets&amp;utm_campaign=Badge_Coverage)
[![codecov](https://codecov.io/gh/ddimtirov/nuggets/branch/master/graph/badge.svg)](https://codecov.io/gh/ddimtirov/nuggets) 
[![Latest Release](https://api.bintray.com/packages/ddimitrov/oss/nuggets/images/download.svg) ](https://bintray.com/ddimitrov/oss/nuggets/_latestVersion)
[![Coverity Scan](https://scan.coverity.com/projects/10133/badge.svg)](https://scan.coverity.com/projects/ddimtirov-nuggets)

### Functionality:

Below is a list of features, see the [javadocs](https://ddimtirov.github.io/nuggets/javadoc/io/github/ddimitrov/nuggets/package-summary.html) for detailed documentation, 
browse the sources at [sourcegraph](https://sourcegraph.com/github.com/ddimtirov/nuggets@master). 

- `Exceptions` - utils for [dealing with checked exceptions](https://kotlinlang.org/docs/reference/exceptions.html#checked-exceptions)
  and [huge stacktraces](https://dzone.com/articles/filtering-stack-trace-hell)
  - rethrow checked exception as unchecked
  - convert throwing functional interfaces into non-throwing counterparts
  - enable the usage of throwing methods in not-throwing lambdas
  - enrich exception message without wrapping it
  - cleanup exception stacktrace, causes and suppressed exceptions
    - preset stack transformer that works for many situations
    - preset stack transformers for Groovy MOP, Java Reflection, etc.
    - stack transformer to get rid of some parasitic stackframes in Groovy
  - dump an exception stacktrace to multi-line string and parse it back 
- `Extractors` - reflection utils  
  - `peek` and `poke` to a private or final field
  - `invokeMethod` to invoke any method with minimum ceremony  
  - convert between wrapper and primitive classes
  - create a default value for type (no-args constructor, zero, `false`, or `null`)
  - safely load potentially missing classes (useful for plugins and optional functionality)
  - dependency injection primitives (roll your custom DI injector in few lines)
  - type linearization - walk the class inheritance tree in concrete first, then breadth 
    first, `Object` last order
  - iterator through all constructors, fields or methods (including private inherited)
- `TextTable` - formats complex data in logs, stdout and file reports
  - rendering to any `Appendable` object (`StringBuilder`, `Writer`, etc.)
  - alignment, padding, right-hand border and custom formatting per cell or column 
  - default values for optional column
  - extensible visual styles for frames, borders, separators, headers
  - row-group separators
  - each cell can contain multi-line strings
  - virtual calculated columns, based on row and column siblings
  - hiden columns, holding data used by calcolations, but not rendered
- `Functions` - utilities for lambdas and `java.util.function` objects
  - decorate lambdas and functional objects with human-readable `toString()`
  - flexible `fallback()` combinator, trying multiple functions until the 
    result matches a predicate and/or no exception is thrown. Nice error 
    reporting.
  - composable reentrant `retry()` decorator 
  - factory for utility function objects allowing to intercept function 
    objects before/after call
  - adaptors allowing to use `void` functions in non-void context
- `Ports` implement a block-based port allocation pattern, useful for integration tests
  - clean, simple and extensible API
  - hook to customize the actual allocation algorithm.  
  - hooks to validate and export the allocated ports to custom config mechanism 
    (i.e. generate config files, or update `System.getProperties()`)
- `UrlStreamHandlers` provides base classes and utilities to easily incorporate 
  data URLs and custom resource-based URL handlers into any application.
- `Threads` allows to introspect running threads, thread-groups and runnables. 
- `ReflectionProxy` is a convenient way to perform a series of reflective 
   accesses, starting from an object. Invaluable for all these cases where you 
   need to monkey-patch a vendor class (especially powerful when combined with 
   `Threads` to get access to the runnable of another thread).
    
- Special Groovy API  
  - Use `Extractors` as Groovy [category](http://groovy-lang.org/metaprogramming.html#categories) 
    to decorate any object with `peekField()/pokeField()`, which can be used access private or 
    final fields as in this example: `foobar.peekField('finalField', Integer)`
  - Any `java.lang.Class` will gain `peekField()/pokeField()` as 
    [extension methods](http://groovy-lang.org/metaprogramming.html#_extension_modules)
    that can be used to access static private or final fields - i.e. 
    `SomeClass.pokeField('someFinalField', newValue)`
  - Use `closure.named('name')` to decorate Closures with human-readable `toString()`
  - Use `DelegatedClosure` when you want to write a decorator intercepting a 
    method or two of a wrapped closure.
  - `Ports` supports array-like indexing to get ports by ID (i.e. one can write 
    `ports['http']`), as well as DSL configuration such as: 
    ```
     def ports = Ports.continuousBlock(10) // easy config for default setup
     ports.withPortSpec(5000) { // easy registration
        id "foo"
        id "bar" offset 1
        id "baz"
     }
    ```
  - `Threads` allows access to the runnable of threads with unique name within the scope by using
     square-brackets notation - i.e. `threadPool['worker-thread-1']`
  - `ReflectionProxy` allows the usage of square brackets to 
    access fields, as well as specify resolution type for shadowed fields.
    For example, if a private field `foo` is declared in both `Parent` and `Child`
    you can access te parent's `foo` from a child instance by doing
    `childProxy[Parent]['foo']`.
  - Each object gets an extra `reflectionDsl()` method which allows to use the `ReflectionProxy`
    in more idiomatic way. Just call the methods and access the fields as properties.
    Use square brackets to specify a resolution type. I.e. the above example would translate to
    `chieldProxyDsl[Parent].foo`      
        
- Special Kotlin API
  - Transform Exceptions in more natural way, by adding `throwable.transform {...}`
    extension method. Added few extension methods to the transform builder.
  - Access encapsulated fields by using `clazz.peek/pokeStaticField(...)` 
    and `instance.peek/pokeField(...)`
  - Added `col(name) { ... }` extension method for more natural config 
    of a table column.    
  - `Ports` supports array-like indexing to get ports by ID (i.e. one can write 
    `ports['http']`), as well as DSL configuration such as: 
    ```
     def ports = portsBlock(10) // easy config for default setup
     ports.withPorts(5000) { reserve -> // easy registration
        reserve port "foo"
        reserve port "bar" at 5
        reserve port "baz"
     }
    ```
    
### Non-functional Features:
- Fat-jar friendly (don't add yet another jar to your distribution)
  - no 3rd party dependencies at runtime 
  - some optional features take advantage of `@Inject`, `Groovy` and `Kotlin` 
    if they happen to be on the class path
  - all components are designed with dependencies in mind, making it trivial 
    to strip the parts you don't use with something like `ProGuard`. 
- high quality testing and javadoc
  - tested on OS X, Windows and Linux
  - code quality checked by Findbugs, Checkstyle and Coverity
  - test coverage tracked by Codecov
  - Codacy dashboard tracks multiple metrics over time
- annotated with the JetBrains annotations (helps if you use IntelliJ IDEA)
- the naming of static methods is chosen to be easily recognizable, 
  yet reduce the chance of clashing when imported statically.
- The performance should be reasonable, though that is not a requirement for now.
- Most of the classes are thread-safe, and thread safety and threading policies are
  documented in the class and method Javadoc.
- Many of the classes are abusing reflection to flip the `accessible` bit on fields 
  and methods. As usual, if you are using security manager you need to do your testing.
