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
import spock.lang.Title

@Title("TextTable :: padding")
@Subject(TextTable)
class TextPaddingSpec extends Specification {
    @SuppressWarnings("GroovyAssignabilityCheck")
//    @Unroll def 'assert pad(#length, "#pad")=="#expected"'(int length, String pad, String expected) {
    def 'repeats the pattern until reaching the desired length and trims the last if needed'(int length, String pad, String expected) {
        when:
        def s = TextTable.pad(new StringBuilder(), length, pad) as String

        then:
        s.length()==length
        s == expected

        where:
        length | pad  || expected
        0      |  '1' || ''
        1      |  '1' || '1'
        2      |  '1' || '11'
        5      |  '1' || '11111'
        0      | '12' || ''
        1      | '12' || '1'
        2      | '12' || '12'
        5      | '12' || '12121'
        7      | '12' || '1212121'
    }

    def 'error handling if the output throws on append'() {
        setup:
        def out = Mock(Appendable) {
            //noinspection GroovyAssignabilityCheck
           append(*_) >> { throw new IOException() }
        }

        when:
        TextTable.pad(out, 10, ' ')

        then:
        thrown(IOException)
    }
}
