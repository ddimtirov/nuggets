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

import spock.lang.AutoCleanup
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title
import spock.util.environment.RestoreSystemProperties

import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@Title("Port allocator tests")
@Subject([Ports, Ports.BlockRegistrar])
class PortsBlockRegistrarSpec extends Specification {
    @AutoCleanup Ports ports
    @AutoCleanup Ports.Registrar registrar

    def "simple Java usage"() {
        given:
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))

        when: 'we reserve some ports and freeze the config'
        ports.reservePort("foo")
              .reservePort("bar", 1)
              .reservePort("baz")
              .freeze(5000)

        then:
        ports.basePort==5000
        ports.port('foo') in 5000..5010
        ports.port('bar') == 5001
        ports.port('baz') in 5000..5010

        and:
        !portAvailable(ports.basePort)

        when:
        ports.close()
        ports.port('foo')

        then:
        thrown(IllegalStateException)
    }


    def "Java modern usage"() {
        given:
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))

        when: 'we reserve some ports and freeze the config'
        ports.withPorts(5000, { reserve ->
            reserve.id("foo");
            reserve.id("bar").offset(1);
            reserve.id("baz");
        } as Consumer<Ports.PortsSpecBuilder>) // ignore the closure coercion - from Java, you would use a real lambda

        then:
        ports.basePort==5000
        ports.port('foo') in 5000..5010
        ports.port('bar') == 5001
        ports.port('baz') in 5000..5010

        and:
        !portAvailable(ports.basePort)

        when:
        ports.close()
        ports.port('foo')

        then:
        thrown(IllegalStateException)
    }


    @RestoreSystemProperties
    def "Publishing to System.properties"() {
        given:
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, 10, 0)
        ports = new Ports(registrar).withExporter { id, port ->
            System.setProperty("p1.$id", "$port")
        }

        when:
        ports.with {
            reservePort("foo")
            reservePort("bar", 1)
            reservePort("baz")
            freeze(5000)
        }

        then:
        ports.basePort==5000
        Integer.getInteger('p1.foo') in 5000..5009
        Integer.getInteger('p1.bar') == 5001
        Integer.getInteger('p1.baz') in 5000..5009
    }

    def "Groovy usage"() {
        given:
        ports = Ports.continuousBlock(10)

        when:
        ports.withPortSpec(5000) {
            id "foo"
            id "bar" offset 1
            id "baz"
        }

        then:
        ports.basePort==5000
        ports['foo'] in 5000..5010
        ports['bar'] == 5001
        ports['baz'] in 5000..5010
    }

    def "Specifying offset without an ID is an error"() {
        given: ports = Ports.continuousBlock(10)

        when:
        ports.withPortSpec(5000) {
            offset 1
        }

        then: thrown(IllegalArgumentException)
    }

    def "Specifying an ID conflicting with the BASE_PORT_ID (which happens to be an empty string) is an error"() {
        given: ports = Ports.continuousBlock(10)

        when: ports.reservePort(id)
        then: thrown(IllegalArgumentException)

        when: ports.reservePort(id, 5)
        then: thrown(IllegalArgumentException)

        where: id << [Ports.Exporter.BASE_PORT_ID, ""]
    }

    def "Normal usage scenarios"(int base, int blockSize, int barOffset) {
        given:
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, blockSize, 0))

        when:
        ports.reservePort("foo")
              .reservePort("bar", barOffset)
              .reservePort("baz")
              .freeze(base)

        then:
        ports.basePort==base
        !portAvailable(ports.basePort)
        ports.port('foo') in base..base+blockSize
        ports.port('bar') == base+barOffset
        ports.port('baz') in base..base+blockSize

        when:
        ports.close()
        ports.port('foo')

        then:
        thrown(IllegalStateException)
        portAvailable(old(ports.basePort))

        where:
        base | blockSize | barOffset
        5000 | 10        | 1
        5100 |  5        | 2
        5000 | 10        | 9
    }

    @RestoreSystemProperties
    def "Publishin port allocations to system properties"() {
        given: 'a port allocator, configured with exporter that stores the posts in System props'
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))
        ports.withExporter { id, port -> System.setProperty("p1.${id ?: 'basePort'}", "$port") }

        when: 'we allocate ports'
        ports.with({ Ports it -> it.with {
            reservePort("foo")
            reservePort("bar", 1)
            reservePort("baz")
            freeze(5000)
        }})

        then: 'we can find all the values from system props'
        Integer.getInteger('p1.basePort') == ports.basePort
        Integer.getInteger('p1.foo') == ports.port('foo')
        Integer.getInteger('p1.bar') == ports.port('bar')
        Integer.getInteger('p1.baz') == ports.port('baz')
    }

    def "Batch publishing of port allocations"() {
        given: 'exporter publishes immutable view of the props'
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))
        def result = new CompletableFuture<Map<String, Integer>>()
        ports.withExporter(Ports.Exporter.batching {
            result.complete(it.asImmutable())
        })

        when: 'we allocate ports'
        ports.with{
            reservePort("foo")
            reservePort("bar", 1)
            reservePort("baz")
        }
        then: !result.done

        when: ports.freeze(5000)
        then: result.done && result.get() == [foo: 5000, bar: 5001, baz:5002]

        when: result.get().base=5001
        then: thrown(UnsupportedOperationException)
    }

    @RestoreSystemProperties
    def "Allocating 2 blocks should be non-overlapping"() {
        given: 'we have 2 allocators, configured witht he same specs'
        def reservation = { Ports it -> it.with {
            reservePort("foo")
            reservePort("bar", 1)
            reservePort("baz")
            freeze(5000)
        }}

        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))
        def ports2 = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))

        when: 'we allocate ports'
        ports.with(reservation)
        ports2.with(reservation)

        then: 'the first should claim the desired ports-range'
        ports.basePort==5000
        ports.port('foo') in 5000..5009
        ports.port('bar') == 5001
        ports.port('baz') in 5000..5009

        and: 'the second should use the port range one block-size away'
        ports2.basePort==5010
        ports2.port('foo') in 5010..5019
        ports2.port('bar') == 5011
        ports2.port('baz') in 5010..5019

        cleanup:
        ports2?.close()
    }

    def "error when registration fails"() {
        given: 'we use a dummy registrar that always fails to allocate a block'
        def registrar = Stub(Ports.Registrar) {
            lock(_) >> -1
        }
        ports = new Ports(registrar)
        ports.reservePort("baz", 1)

        when: 'we try (and fail) to allocate a block'
        ports.freeze(5000)

        then: 'an exception is thrown'
        thrown(IllegalStateException)
    }

    def "error when explicit port offsets clash"() {
        given: 'we have registered an id with certain port'
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))
        ports.reservePort("bar", 1)

        when: 'we try to reserve the same port again'
        ports.reservePort("baz", 1)

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)

        and: "we can still restate reservation for an offset as long as it is the same id"
        ports.reservePort("bar", 1)
    }

    def "error when port names clash"() {
        given: 'we have registered an id with certain port'
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0))
        ports.reservePort("bar", 1)
        ports.reservePort("dyn")

        when: "we restate reservation for the dynamic port id and explicit offset"
        ports.reservePort("dyn", 2)

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)

        when: "we restate reservation for the explicit port id and dynamic offset"
        ports.reservePort("bar")

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)


        when: "we restate reservation for the same port id and different offset"
        ports.reservePort("bar", 2)

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)

        when: "we restate reservation for the same port id and DYNAMIC offset"
        ports.reservePort("bar")

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)

        when: "we restate reservation for the same port ids and offsets"
        ports.reservePort("bar", 1)
        ports.reservePort("dyn")

        then: 'an exception is thrown'
        noExceptionThrown()
    }

    def "error when statically allocated ports clash with dynamic allocated"() {
        given: 'a custom dynamic port finder, returning a fixed offset'
        final CLASHING_PORT_OFFSET = 1
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, 10, 0)
        def dynamicPortFinder = {-> CLASHING_PORT_OFFSET}
        ports = new Ports(registrar, dynamicPortFinder)

        when: 'we try to reserve a dynamic port and a static port with clashing offset'
        ports.reservePort("bar", CLASHING_PORT_OFFSET)
        ports.reservePort("baz")
        ports.freeze(5000)

        then: 'an exception is thrown'
        thrown(IllegalArgumentException)

        cleanup:
        ports.close()
    }

    def "error when looking for unknown port"() {
        given: "we declare ports 'bar' and 'baz'"
        ports = new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 10, 0)).with {
            reservePort "bar"
            reservePort "baz"
            freeze 5000
        }

        when: "we lookup undeclared port 'foo'"
        ports.port("foo")

        then: 'an exception is thrown'
        thrown(NoSuchElementException)

        and: "we can still lookup the declared ports"
        ports.port("bar")>0

    }

    @SuppressWarnings("GroovyResultOfObjectAllocationIgnored")
    def "Lock ports can be outside the range, next to it above or below"(int rangeSize, int offsetOk, int offsetBad) {
        when: new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, offsetBad)
        then: thrown(IllegalArgumentException)

        when: new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, offsetOk)
        then: notThrown(IllegalArgumentException)

        where:
        rangeSize | offsetOk | offsetBad
        10        | -1       | -2
        10        | 10       | 11
    }

    def "When port range is exhausted, IOException will be thrown"() {
        given: List<Ports> ranges = []

        when: "we try to allocate 20 x 1000 ports blocks starting from 50000"
        20.times {
            // port range is from 1-65535 - we can allocate no more than 15 blocks here + 1 incomplete (which succeeds because we reserve offset 0)
            ranges << new Ports(new Ports.BlockRegistrar(InetAddress.localHost, 1000, 0))
                          .freeze(50_000)
        }

        then: "we ultimately run out of ports"
        thrown(IOException)
        ranges*.basePort.size() in 10..16

        cleanup: ranges*.close()
    }

    def "If necesarry, the step is increased to accommodate the locking port at #lockOffset"(int startPort, int rangeSize, int lockOffset, basePorts) {
        given: List<Ports> ranges = []

        when: "we allocate 5 ranges"
        5.times {
            ranges << new Ports(new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, lockOffset))
                          .freeze(startPort)
        }

        then: "we ultimately run out of ports"
        ranges*.basePort == basePorts

        cleanup:
        ranges*.close()

        where:
        startPort | rangeSize | lockOffset || basePorts
        5000      | 100       | 0          || [5000, 5100, 5200, 5300, 5400]
        5000      | 100       | -1         || [5000, 5101, 5202, 5303, 5404]
        5000      | 100       | 10         || [5000, 5100, 5200, 5300, 5400]
        5000      | 100       | 99         || [5000, 5100, 5200, 5300, 5400]
        5000      | 100       | 100        || [5000, 5101, 5202, 5303, 5404]
    }

    def "we can use a publisher to make sure none of the allocated ports is locked"() {
        given:
        int occupiedOffset = 5
        def desiredBase = 5000
        def squatter = new ServerSocket(5000 + occupiedOffset, 10, InetAddress.localHost)
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, 10, 0)

        when:
        ports = new Ports(registrar).withExporter { id, p ->
            if (id=='' || portAvailable(p)) return
            throw new Ports.PortVetoException(id, p, 'Port taken')
        }.with {
            reservePort "foo", 3
            reservePort "bar", occupiedOffset
            freeze desiredBase
        }

        then: 'an exception is thrown'
        ports.basePort==5006
        ports.basePort==desiredBase + occupiedOffset + 1
        ports.port('foo')==ports.basePort + 3
        ports.port('bar')==ports.basePort + occupiedOffset

        cleanup:
        squatter.close()
    }

    def "we can set a preference that port blocks are aligned on range boundaries"() {
        given:
        int occupiedOffset = 5
        def desiredBase = 5000
        def rangeSize = 10
        def squatter = new ServerSocket(5000 + occupiedOffset, 10, InetAddress.localHost)
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, 0)
        registrar.alignToBasePortHint = true

        when:
        ports = new Ports(registrar).withExporter { id, p ->
            if (id=='' || portAvailable(p)) return
            throw new Ports.PortVetoException(id, p, 'Port taken')
        }.with {
            reservePort "foo", 3
            reservePort "bar", occupiedOffset
            freeze desiredBase
        }

        then: 'an exception is thrown'
        ports.basePort==5010
        ports.basePort==desiredBase + rangeSize
        ports.port('foo')==ports.basePort + 3
        ports.port('bar')==ports.basePort + occupiedOffset

        cleanup:
        squatter.close()
    }

    def "the base port hint can be zero the second time around to ask for automatic reallocation"() {
        given:
        def rangeSize = 10
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, 0)

        when: registrar.lock(0)
        then: thrown(IllegalArgumentException)

        when: def initialBasePort = registrar.lock(5000)
        then: !portAvailable(initialBasePort) & initialBasePort == 5000

        when: def nextBasePort = registrar.lock(0)
        then: portAvailable(initialBasePort) & nextBasePort == initialBasePort+rangeSize
    }

    def "for the block registrar, the abs(basePortHint) should be a valid port number"() {
        given:
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, 10, 0)

        when: registrar.lock(invalidPort)
        then: thrown(IllegalArgumentException)

        where:
        invalidPort << [999999, 0x10000, -65537]
    }

    def "If the lock port invalid, throw exception"() {
        given:
        def rangeSize = 10
        def lockPortOffset = rangeSize

        when:
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, lockPortOffset)
        registrar.lock(0xFFFF-rangeSize+1)
        then: thrown(IOException)
    }

    def "Lock a block of ports immediately below and including 0xFFFF"() {
        given: 'we really need ot use the last 10 ports, then use a lock port below the block'
        def rangeSize = 10
        def lockPortOffset = -1
        registrar = new Ports.BlockRegistrar(InetAddress.localHost, rangeSize, lockPortOffset)

        when: def basePort = registrar.lock(0xFFFF - rangeSize + 1)
        then: 'the lock is outside of the allocated range and all ports in range are available for binding'
        !portAvailable(basePort - 1)
        0.upto(9) { assert portAvailable(basePort+it) }
    }

    private static boolean portAvailable(int port, InetAddress address = InetAddress.localHost) {
        try {
            new ServerSocket(port, 10, address).close()
            return true
        } catch(ignored) {
            return false
        }
    }
}
