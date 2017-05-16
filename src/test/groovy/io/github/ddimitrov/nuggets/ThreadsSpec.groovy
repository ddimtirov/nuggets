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

import java.util.concurrent.CountDownLatch

@Subject(Threads)
class ThreadsSpec extends Specification {
    def "current group is a subset of the top"() {
        expect:
        Threads.allThreads.all.containsAll(Threads.siblingThreads.all)
    }

    @Timeout(5)
    def "full functionality demo"() {
        given: "a new test group"
        def testThreadGroup = new ThreadGroup("test")
        testThreadGroup.daemon = true
        def sync = new CountDownLatch(1)

        expect:
        Threads.siblingThreads.containedGroups(false).contains(testThreadGroup)
        Threads.siblingThreads.containedGroups(true).contains(testThreadGroup)

        when: def testThreads = new Threads(testThreadGroup)
        then: testThreads.all.size()==0
        and:  testThreads.parentGroupThreads.all==Threads.siblingThreads.all

        when: 10.times { new Thread(testThreadGroup, { sync.await() }, "test-thread-$it").start()}
        then: testThreads.all.size()==10
        and:  Threads.allThreads.all.containsAll(testThreads.all)

        expect:
        with(testThreads.byName) {
            keySet()==(0..9).collect { "test-thread-$it" } as Set
            values().every { rs -> rs.size()==1 && rs instanceof List }
        }

        with(testThreads.withUniqueName) {
            keySet()==(0..9).collect { "test-thread-$it" } as Set
            values().every { it instanceof Runnable }
        }

        when:
        sync.countDown()
        testThreads.containedThreads(false)*.join()
        then: testThreads.all.empty

        cleanup:
        sync.countDown()
    }
    @Timeout(5)
    def "groovy functionality demo"() {
        given: "a new test group"
        def testThreadGroup = new ThreadGroup("test")
        testThreadGroup.daemon = true
        def sync = new CountDownLatch(1)

        expect:
        testThreadGroup in Threads.siblingThreads.containedGroups(false)

        when: def testThreads = new Threads(testThreadGroup)
        then: testThreads.all.empty

        when: 0.upto(9) { new Thread(testThreadGroup, { sync.await() }, "test-thread-$it").start()}
         and: new Thread(testThreadGroup, { sync.await() }, "test-thread-9").start()
        then: 0.upto(8) {
            assert testThreads.byName["test-thread-$it"].size()==1
            assert testThreads.byName["test-thread-$it"][0] instanceof Closure
            assert testThreads.withUniqueName["test-thread-$it"] == testThreads.byName["test-thread-$it"][0]
            assert testThreads.byName["test-thread-$it"].size()==1
            assert testThreads.byName["test-thread-$it"][0] instanceof Closure
            assert testThreads["test-thread-$it"] == testThreads.byName["test-thread-$it"][0]
        }
        testThreads.byName["test-thread-9"].size()==2
        testThreads.withUniqueName["test-thread-9"] == null
        testThreads["test-thread-9"] == null

        when:
        sync.countDown()
        testThreads.containedThreads(false)*.join()
        then: testThreads.all.empty

        cleanup:
        sync.countDown()
    }
}