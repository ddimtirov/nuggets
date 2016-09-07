#### Release Checklist
*[ ] Javadocs 
    *[x] non-private members
    *[x] classes
    *[x] thread safety policy per class (document exceptions per method)
*[x] Update README
*[x] Testing
    *[x] new code is tested or at least demos are run from the test suite 
    *[x] functionality is covered
    *[x] error handling code is covered 
*[ ] Build
    *[x] Build Javadoc
    *[x] run static anaysis and style checks
    *[ ] Publish Javadoc somewhere and refer from readme
*[ ] Setup services
    *[ ] TravisCI
    *[ ] Coveralls
    *[ ] VersionEye

|                       | Exceptions | TransformerBuilder | Extractors 
|-----------------------|:---:|:---:|:---:
| javadoc members       | DONE| DONE| DONE 
| javadoc classes       | DONE| DONE| DONE 
| javadoc thread-safety | DONE| DONE| DONE 
| testing functionality | DONE| DONE| DONE  
| testing error-handling| DONE| DONE| DONE 
    
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