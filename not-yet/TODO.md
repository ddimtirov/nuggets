#### Release Checklist
|                       | Exceptions | TransformerBuilder | Extractors 
|-----------------------|:---:|:---:|:---:
| javadoc members       | DONE| DONE| DONE 
| javadoc classes       | DONE| DONE| DONE 
| javadoc thread-safety | DONE| DONE| DONE 
| annotate nullness     | DONE| DONE| DONE 
| testing functionality | DONE| DONE| DONE  
| testing error-handling| DONE| DONE| DONE 

*[ ] Update README


# TODO:  
# TODO: 
# TODO: 
# TODO: https://scan.coverity.com/travis_ci
# TODO: Publish javadocs to gh-pages

*[ ] Setup services
    *[ ] Build commits
      *[ ] [TravisCI](https://travis-ci.org/ddimtirov/nuggets/settings)
      *[ ] Publish Javadoc to gh-pages and refer from readme
      *[ ] add to the matrix - osx, openjdk8
      *[ ] add windows to the matrix via appveyor
    *[ ] Code reports
        *[ ] Codecov
        *[ ] VersionEye
        *[ ] Coverity
*[ ] Release  
    *[ ] Sign tagged releases   
    *[ ] Publish tagged releases to Github Releases  
    *[ ] Publish tagged releases to Bintray  
    *[ ] Publish tagged releases to Central  
*[ ] add badges to readme http://shields.io/



License: https://img.shields.io/github/license/ddimitrov/nuggets.svg

    
#### TODO: 
- Exceptions
    - parse exceptions from exception string (best effort)
    - diff exceptions
- Threads
    - parse thread dump
    - correlate similar threads    
    - build stack-tree with frequencies (profiler-ish like)
    - analyze threads over time (profiler-ish like)
- move documentation out of javadocs and into asciidoc, extract code samples from tests
- java agent instrumenting all call sites to use the thread-local exception cleaner
- Groovy extensions
- Kotlin extensions

