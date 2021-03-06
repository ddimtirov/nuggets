# v0.5.0 TextTable enhancements and Exceptions usability fixes

`TextTable` is the star of this release! 

Lwt's start with the brand new `separator()` method, allowing to 
organize the rows into logical groups with custom titles and styling. 
Another great addition is that each table cell can now contain a 
multi-line string, which comes in handy when you need to display 
long line-wrapped values. 

The formatting of table frames and column-headers is extended, so we 
can specify glyphs for each of these or turn them off entirely. We 
can even simulate cell-spans by disabling the the right-hand border 
per-cell (though content is still limited to a single grid cell).

Furthermore, calculated columns are getting a lot more powerful with 
the addition of `SiblingLookup` that can be transparently injected and  
allow query values from the same row, process all column values (i.e. 
for aggregated metric) or iterate through the table rows.

If a table column is declared as `hidden`, we can use it for lookups, 
but it will not render in the final output; or declare it as `virtual` 
when it is entirely calculated without underlying value in the input 
dataset.

The `Exceptions` class has two minor additions - `rethrowing()` converts
throwing functional interfaces in their non-throwing counterparts. While 
`rethrowR()` and `rethrowC()` aliases, can be used to disambiguate the 
cases where Java mandates an explicit cast in a `rwthrow()` call.

For example, Java gets confused whether a method literal should be a 
considered a `ThrowingRunnable` or `Callable`  and requires an explicit cast
in the following case

```$java
return rethrow((Callable)future::get)
```

Removing the cast to `Callable` in the case above would be a syntax error, 
but we can make it a little bit nicer if we use `rethrowC(future::get)` 
instead.


# v0.4.0 Thread introspection, Reflection Proxy, Retry, Data and Classpath URLs 

This release delivers 4 small features - while not related, they are 
especially useful if you are hacking legacy code or doing whitebox testing.

The `Threads` class is a wraps a Java `ThreadGroup` and provides convenient 
access its contained `Threads` or the actual `Runnable` instances that do the 
work. This, combined with reflection allows to get access to the internals 
of servers and messaging frameworks, or could be just useful to monitor for 
leaked threads. The API also allows conveniently to navigate to parent and 
child thread-groups starting from `main` or the thread group of the current 
thread. 

`ReflectionProxy` complements the `Threads` class, by providing convenient 
way to apply a series of reflective operations on an object graph. Here is 
a combined example, changing timeout field of a hypothetical server.

```$java
Runnable acceptor = Threads.getAllThreads().getWithUniqueName("Acceptor-Thread");
ReflectionProxy.wrap(acceptor).get("reactor").get("config").set("timeout", 100_000);
```

Another useful feature for concurrent testing is being able to retry until 
success. This is achieved by `Functions.retry()`. While concept is similar to Spock's 
[PollingConditions](http://spockframework.org/spock/javadoc/1.0/spock/util/concurrent/PollingConditions.html),
the main difference is that `Functions.retry()` is reentrant. 

By *reentrant*, we mean that you may write a method `foo` using `retry()` 
and later call `foo` from the `retry()` body in method `bar`. 
A naive implementation would spin in `foo` until it times out, effectively 
causing priority inversion. This is exacerbated when used in architectures
such as single-threaded reactor. `Functions.retry()` only spins at the 
top-level, of the stack with any reentrant calls being treated as pass-through.
 
In addition, `Functions.retry()` can come handy outside of testing too - i.e. 
for handling unavoidable races. It is robust, allows for busy-waiting before 
sleeping, and handles interruption well. 

Finally, the `UrlStreamHandlers` provides base classes that can be used to add
support for Data URLs, Classpath URLs, or custom schemas translating to supported 
URL types (i.e. one may implement a handler translating `config:connection/http/port` 
to `file:config/connection.xml` and delegating the resolution to it).


# v0.3.0 Port allocator, Reflective invocation, Void to returning function adaptors 

While the theme of this release was to improve the continuous integration infrastructure,
 we also added a few minor features.
 
The `Ports` class implements block-based port allocation, useful when one does integration
testing and wants to test in parallel a service which binds ports, and/or needs a private 
data directory.
 
The `Extractors` utility got a brand new `invokeMethod()` and `getAccessibleMethod()`, 
 allowing to break encapsulation with minimum fuss (similar to their _`xxxField()`_ 
 counterparts). The parameter order of `getAccessibleField()` has been changed in order
 to make it consistent with the other methods (should result in easy to fix compile error). 

The `Functions` utility now provides the `ret()` and `retnul()`, which allow us to write 
 more expressive one-liners when we need to use a void function in a position expecting 
 a non-void expression. 

From the non-functional side, now the tests are running on OS X, Windows and Linux under 
both JDK8 and (currently failing) JDK9. We have also added a [Coverity scan](https://scan.coverity.com/projects/ddimtirov-nuggets) 
on top of the existing Findbugs and Checkstyle checks.
 
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