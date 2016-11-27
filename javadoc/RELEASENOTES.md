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