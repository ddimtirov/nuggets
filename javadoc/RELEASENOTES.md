# v0.2.0 Functions and small improvements in Extractors 

The theme for `v0.2.0` release is lambda utilities. This is not yet another 
 Functional Java library, but rather adding small bits and pieces to help
 to the standard library to make it more debuggable and terse.

The `Functions` provides utilities that can be used with `java.util.function` 
 objects and are especially convenient with lambdas. These include decorators
 adding human-readable `toString()`, convenient factories for functional objects 
 intercepting arguments and return values, and a flexible `fallback()` combinator, 
 trying multiple functions until the result matches a predicate and/or no 
 exception is thrown. 

The *Groovy API* got an extra `closure.named('name')` extension method for
 any `Closure` instance. As a byproduct, the `DelegatedClosure` is a convenient 
 base class that one can extend when you want to write a decorator intercepting
 a method or two of a wrapped closure.

The `Extractors` now provides `eachAccessible()` as a way to enumerate and 
 process every field, method or constructor of a class, including the privately 
 inherited ones. It also allows you to throw exceptions, so no more `catch (Exception e)`
 arround reflection code. Another addition is `linearized()` providing a list 
 of all supertypes for a class in a sensible order - superclasses first, then
 interfaces breadth-first and finally `Object` - comes handy when doing manual
 method dispatch (i.e. in some custom DI scenarios). 

# v0.1.0 Exceptions, Extractors, Tables

Initial release delivering 3 basic utilities, useful with day-to-day coding in Java. 
 A lot of the work in this release was about setting up the project infrastructure,
 including build, continuous integration+static analysis, documentation, release and
 publication process, etc. 

The `Exceptions` helps with various error-handling scenarios, including dealing with
 badly conceived checked exceptions (i.e. `Appendable`), as well as cleaning up stack 
 traces to remove useless frames and add custom annotations.

The `Extractors` allows to breaking the encapsulation of badly designed APIs and 
 access privates, and mutate final fields of any object (subject to security policy).
 It also provides the building blocks for custom dependency injector for those cases 
 when Guice and Dagger may be overkill.
 
Finally, the `TextTable` will come handy for those cases when one needs a quick and 
 dirty text table in a log file or other monospaced output.