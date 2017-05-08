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

class UrlStreamHandlersSpec extends Specification {
    def "parse data URLs to text"() {
        given:
        def u = new URL(url)

        expect:
        u.text == payload

        where:
        url                                                         || payload       | mimeType
        'data:,Hello World'                                         || 'Hello World' | 'text/plain'
        'data:,Hello%20World'                                       || 'Hello World' | 'text/plain'
        'data:text/plain,Hello%20World'                             || 'Hello World' | 'text/plain'
        'data:text/plain;encoding=US-ASCII,Hello%20World'           || 'Hello World' | 'text/plain;encoding=US-ASCII'
        'data:;base64,SGVsbG8gV29ybGQ='                             || 'Hello World' | 'text/plain'
        'data:text/plain;encoding=US-ASCII;base64,SGVsbG8gV29ybGQ=' || 'Hello World' | 'text/plain;encoding=US-ASCII'
    }

    def "create data URLs from text"() {
        when:
        String urlString = UrlStreamHandlers.createDataUrl(null, payload)
        String urlStringMime = UrlStreamHandlers.createDataUrl("text/plain;charset=UTF8", payload)
        then:
        new URL(urlString).text == payload
        new URL(urlStringMime).text == payload
        where: payload << ['Hello World', 'Hello\r\nWorld', "Hello\u0000 World"]
    }

    def "create data URLs from bytes"() {
        given: def payload = -128..128 as byte[]
        when:
        String urlString = UrlStreamHandlers.createDataUrlBase64(null, payload)
        String urlStringMime = UrlStreamHandlers.createDataUrlBase64("bunary/octet", payload)
        then:
        new URL(urlString).content == payload
        new URL(urlStringMime).content == payload
    }

    def "get data content as type"() {
        given: def url = new URL("data:,the%20quick%20brown%20fox")
        when:
        def c = url.openConnection()
        then:
        c.getContent(byte[])=="the quick brown fox".getBytes("UTF-8")
        c.getContent(String)=="the quick brown fox"
    }

    def "check data URL headers"() {
        given: def url = new URL("data:,the%20quick%20brown%20fox")
        when:
        def c = url.openConnection()
        then:
        c.connect() // or we'll get exception
        c.getHeaderFieldInt('content-length', -1)=="the quick brown fox".length()
    }

    def "work with resource urls"() {
        expect:
        new URL("cp:$classloaderPath").text==content
        new URL("cplocal:$localPath").text==content

        where:
        classloaderPath                              | localPath         || content
        'hello-root.txt'                             | '/hello-root.txt' || "Hello World!"
        'io/github/ddimitrov/nuggets/hello-local.txt'| 'hello-local.txt' || "Здрасти!"
    }
}
