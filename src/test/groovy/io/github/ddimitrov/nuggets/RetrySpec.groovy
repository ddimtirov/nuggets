/*
 *    Copyright 2017 by Dimitar Dimitrov
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
import spock.lang.Timeout
import spock.lang.Title

import java.util.concurrent.TimeoutException


@Title("Functions :: retry")
@Subject(Functions)
@Timeout(10)
class RetrySpec extends Specification {
    def "retry calls the closure until it passes"() {
        int i = 1

        when:
        def result = Functions.retry("incrementing", 1000) {
            assert ++i>=5
            return i
        }

        then:
        result == 5

    }

    def "but it may timeout"() {
        def testThread = Thread.currentThread()
        def interrupter = Thread.start { while (!Thread.interrupted()) testThread.interrupt() } // increase coverage, force spinning

        when:
        Functions.retry("failing", 100) {
            assert false
        }

        then:
        def e = thrown(TimeoutException)
        and: 'the exception message contains the description and extra details'
        e.message =~ 'failing'

        cleanup:
        interrupter.interrupt()
        Functions.retry("race between the interrupter interrupting us and us interrupting it", 10_000) {
            interrupter.join()
        }
    }

    def "one can listen for retries - useful for test reports"() {
        given:
        def results = [:]
        assert !Functions.snoopRetries("test-listener") { description, result, error ->
            results[description] = error ?: result
        }

        when:
        int i = 1
        def result = Functions.retry("incrementing", 1000) {
            assert ++i >= 5
            return i
        }
        result == 5

        and:
        Functions.retry("failing", 100) { assert false }

        then:
        thrown(TimeoutException)
        and:
        results['incrementing'] == 5
        results['failing'] instanceof TimeoutException

        cleanup:
        Functions.snoopRetries("test-listener", null)
    }
}