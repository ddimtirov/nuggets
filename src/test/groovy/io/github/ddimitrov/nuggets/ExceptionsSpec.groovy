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

package io.github.ddimitrov.nuggets

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.TempDir
import spock.lang.Title

import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

@Title("Exceptions :: Silent rethrows and formatting")
@Subject(Exceptions)
@SuppressWarnings("GroovyAccessibility")
class ExceptionsSpec extends Specification {
    @TempDir Path temporaryFolder

    void setup() {
        assert Exceptions.TRANSFORMER.get()==null : "Some of the previous tests did not clean up the global exception transformer"
    }

    def "Use Exceptions.toString(throwable) to quickly format exceptions with stacktraces."() {
        when: def string = Exceptions.toStackTraceString(new IllegalArgumentException())
        then: !string.empty

        when:  def lines = string.split('([\n\r])+') as List<String>
        then:
        lines.first()==IllegalArgumentException.name
        lines.drop(1).each {
            assert it ==~ /\s+at\s+(\S+\.)+\S+\(.+\)/ : "matches stack frame pattern"
        }
    }

    def "Use rethrowSilent(e) to tunnel exceptions without declaring them in Java"() {
        setup:
        def f = Files.createTempFile(temporaryFolder, ExceptionsSpec.simpleName, null)
        f.toFile().text="hello"

        when: "we get the size of existing file"
        def fileSize = ExceptionsJavaDemo.quietFileSize(f)

        then: "it comes back correct"
        fileSize==5L
        Files.delete(f) // make sure deletion succeeds

        when: "we try to get the size of missing file"
        ExceptionsJavaDemo.quietFileSize(f)

        then: "a checked exception is thrown, even if the quietFileSize() method does not declare it"
        def e = thrown(NoSuchFileException)
        e.message == f.path
    }

    def "Illustrate the use of rethrowing"() {
        expect: ExceptionsJavaDemo.rethrowing()
    }

    def "Use rethrowSilent(e, reason) to decorate rethrown exception with layer-specific info (i.e. TxnId)"() {
        setup:
        String txnId = "[txn:${System.nanoTime()}]"

        when: "the callable throws an error"
        ExceptionsJavaDemo.traceCall(txnId) { throw new IllegalStateException("illegal alien") }

        then: "The message of the thrown exception is decorated with txnId (see the impl catch clause for details)"
        def e = thrown(IllegalStateException)
        e.message == "FAILED $txnId: illegal alien" as String
    }

    // See ExceptionsJavaDemo.assureAllFresh() as Groovy is transparent to exceptions anyway
    def "Mute exceptions in void closure"() {
        setup:
        def f1 = Files.createTempFile(temporaryFolder, ExceptionsSpec.simpleName, null)
        def f2 = Files.createTempFile(temporaryFolder, ExceptionsSpec.simpleName, null)
        def f3 = Files.createTempFile(temporaryFolder, ExceptionsSpec.simpleName, null)
        [f1, f2, f3].each { Files.delete(it) }

        when: 'calling with files missing'
        ExceptionsJavaDemo.assureAllFresh([f1, f2, f3])
        then: 'create all files'
        notThrown(IOException)

        when: 'calling with files present'
        ExceptionsJavaDemo.assureAllFresh([f1, f2, f3])
        then: 'throw exception'
        thrown(IOException)
    }

    // See ExceptionsJavaDemo.assureFresh() as Groovy is transparent to exceptions anyway
    def "Mute exceptions in void closure returning result on success"() {
        setup:
        def f = Files.createTempFile(temporaryFolder, ExceptionsSpec.simpleName, null)
        Files.delete(f)

        when: 'calling with file missing'
        def v = ExceptionsJavaDemo.assureFresh(f, "success")
        then: 'if file was created successfully, return the retval'
        v == "success"

        when: 'calling with files present, exception will be thrown'
        ExceptionsJavaDemo.assureFresh(f, 'foobar')
        then: 'throw exception'
        thrown(IOException)
    }

    // See ExceptionsJavaDemo.findLongerThan() as Groovy is transparent to exceptions anyway
    def "Mute exceptions in closure returning result"() {
        setup: 'setup 2 files - one larget than 5 chars, one smaller'
        def file = Files.createTempFile(temporaryFolder, "one", null)
        file.toFile() << 'foobar' // longer than 5 bytes
        Files.createTempFile(temporaryFolder, "another", null).toFile() << 'foo' // shorter than 5 bytes
        def allFiles = Files.list(temporaryFolder) as List

        when: 'we search for all files larget than 5'
        def longerThan5 = ExceptionsJavaDemo.findLongerThan(allFiles, 5)

        then: 'we get one'
        longerThan5 ==[file]

        when: 'we add a missing file to the list'
        def deleted = Files.createTempFile(temporaryFolder, "deleted", null)
        Files.delete(deleted)
        ExceptionsJavaDemo.findLongerThan(allFiles + deleted, 5)==[file]

        then: 'checking the length of the deleted file throws checked exception (and we used Collection.forEach())'
        thrown(NoSuchFileException)
    }
}
