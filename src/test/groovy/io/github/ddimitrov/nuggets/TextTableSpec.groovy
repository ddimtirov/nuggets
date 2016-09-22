package io.github.ddimitrov.nuggets

import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Title

import java.util.concurrent.atomic.AtomicInteger

@Title("TextTable :: examples")
@Subject(TextTable)
class TextTableSpec extends Specification {
    static final EOL = System.lineSeparator()

    void setup() {
        TextTable.eol = '\n'
    }

    void cleanup() {
        TextTable.presetBox(TextTable.Box.ASCII)
    }
    def "we can keep manipulating/inspecting the layout"() {
        setup:
        def layout = TextTable.withColumns("key", "value")

        when: 'we can use some layout to create a table'
        def table2 = layout.withData().rows([
                ['abc', 123],
                ['foobar', 'bazqux'],
        ]).buildTable()

        then:
        table2.format(10, new StringBuilder()).toString() == """
          +--------+--------+
          | key    | value  |
          +--------+--------+
          | abc    | 123    |
          | foobar | bazqux |
          +--------+--------+
""" - '\n'

        and: 'there is no column column "notes"'
        !layout.toString().contains('notes')

        when: 'we can add "notes" later'
        layout.column('notes')

        then: 'we can use the modified layout'
        layout.toString().contains('notes')
        layout.allColumns.notes.name == 'notes'

        and: 'we can build a table with 3 columns, row by row'
        def data = layout.withData()
        data.row(value: 123, key: 'abc', notes: '') // out of order is ok
        data.row('foobar', 'bazqux', 'whatever blah')
        def table3 = data.buildTable()

        and: 'we get 3 column table'
        table3.format(10, new StringBuilder()).toString() == """
          +--------+--------+---------------+
          | key    | value  | notes         |
          +--------+--------+---------------+
          | abc    | 123    |               |
          | foobar | bazqux | whatever blah |
          +--------+--------+---------------+
""" - '\n'

        when: 'we increase the padding of all columns to 2'
        layout.allColumns.values()*.padding = 2
        and: 'use the same data as the prev example'
        def doublePadded = layout.withData().rows([
                ['abc', 123, ''],
                ['foobar', 'bazqux', 'whatever blah']

        ]).buildTable()

        then:
        doublePadded.format(10, new StringBuilder()).toString() == """
          +----------+----------+-----------------+
          |  key     |  value   |  notes          |
          +----------+----------+-----------------+
          |  abc     |  123     |                 |
          |  foobar  |  bazqux  |  whatever blah  |
          +----------+----------+-----------------+
""" - '\n'
    }

    def 'we can customize columns as follows'() {
        when: 'we define the columns one by one, we can configure them in closure'
        def layout = TextTable.withColumns("key")
              .column('value') { it.padding = 3 }
              .column('notes') { it.padding = 4 }

        and: 'we can configure or even remove any column already defined by looking it up from the map map'
        layout.allColumns.key.padding = 2

        and: 'when we build with some data'
        def table = layout.withData().rows([
                ['abc', 123, ''],
                ['foobar', 'bazqux', 'whatever blah']

        ]).buildTable()

        then:
        table.format(10, new StringBuilder()).toString() == """
          +----------+------------+---------------------+
          |  key     |   value    |    notes            |
          +----------+------------+---------------------+
          |  abc     |   123      |                     |
          |  foobar  |   bazqux   |    whatever blah    |
          +----------+------------+---------------------+
""" - '\n'
    }

    def 'padding and indent can be multicharacter string'() {
        setup:
        TextTable.padding = 'PADDING'
        TextTable.indent  = '_2_4_6_8_0'

        when: 'we define the columns one by one, we can configure them in closure'
        def layout = TextTable.withColumns()
              .column('key'  ) { it.padding = 2 }
              .column('value') { it.padding = 3 }
              .column('notes') { it.padding = 4 }

        and: 'when we build with some data'
        def table = layout.withData().rows([
                ['abc', 123, ''],
                ['foobar', 'bazqux', 'whatever blah']

        ]).buildTable()

        then:
        table.format(10, new StringBuilder()).toString() == """
_2_4_6_8_0+----------+------------+---------------------+
_2_4_6_8_0|PAkeyPADDI|PADvaluePADD|PADDnotesPADDINGPADDI|
_2_4_6_8_0+----------+------------+---------------------+
_2_4_6_8_0|PAabcPADDI|PAD123PADDIN|PADDINGPADDINGPADDING|
_2_4_6_8_0|PAfoobarPA|PADbazquxPAD|PADDwhatever blahPADD|
_2_4_6_8_0+----------+------------+---------------------+
""" - '\n'
    }

    def "you can use unicode tables"(TextTable.Style style, String expected) {
        setup:
        def table = TextTable.withColumns('first', 'second').withData().rows([
                ['foo bar baz qux', ''],
                ['qux', '123']
        ]).buildTable()

        when:
        TextTable.presetBox(style)
        TextTable.eol='\n'

        then:
        table.format(0, new StringBuilder()).toString() == expected.replaceAll(~/\n\s+/, '\n') + TextTable.eol

        where:
        style || expected
        TextTable.Box.ASCII          || '''+-----------------+--------+
                                           | first           | second |
                                           +-----------------+--------+
                                           | foo bar baz qux |        |
                                           | qux             | 123    |
                                           +-----------------+--------+'''
        TextTable.Box.UNICODE_THIN   || '''┌─────────────────┬────────┐
                                           │ first           │ second │
                                           ├─────────────────┼────────┤
                                           │ foo bar baz qux │        │
                                           │ qux             │ 123    │
                                           └─────────────────┴────────┘'''
        TextTable.Box.UNICODE_THICK  || '''┏━━━━━━━━━━━━━━━━━┳━━━━━━━━┓
                                           ┃ first           ┃ second ┃
                                           ┣━━━━━━━━━━━━━━━━━╋━━━━━━━━┫
                                           ┃ foo bar baz qux ┃        ┃
                                           ┃ qux             ┃ 123    ┃
                                           ┗━━━━━━━━━━━━━━━━━┻━━━━━━━━┛'''
        TextTable.Box.UNICODE_DOUBLE ||'''╔═════════════════╦════════╗
                                          ║ first           ║ second ║
                                          ╠═════════════════╬════════╣
                                          ║ foo bar baz qux ║        ║
                                          ║ qux             ║ 123    ║
                                          ╚═════════════════╩════════╝'''
    }

    def 'when we use Java, 2D arrays for data are nicer than collections'() {
        setup:
        def layout = TextTable.withColumns("key", "value", 'notes')

        expect: 'when we use Java, 2D arrays for data are nicer than collections'
        TextTableJavaDemo.tableForArrayData(layout).format(10, new StringBuilder()).toString()=="""
          +--------+--------+---------------+
          | key    | value  | notes         |
          +--------+--------+---------------+
          | abc    | 123    |               |
          | foobar | bazqux | whatever blah |
          +--------+--------+---------------+
""" - '\n'

    }

    def "two columns with the same name are not allowed"() {
        when: 'we have duplicate names (stripping whitespace)'
        TextTable.withColumns("same", "same ")

        then: 'an exception is thrown'
        IllegalArgumentException e = thrown()
        e.message == "Duplicate column 'same'"
    }

//    @Unroll def "all columns: #useCase"(useCase, row, String expected) {
    def "rows need to populate all mandatory columns"(useCase, row, String expected) {
        setup:
        // normalize line endings and trim leading space
        def expectedMesssage = expected.replace EOL, '\n' replaceAll ~/\n\s*/, '\n' replace '\n', EOL
        def layout = TextTable.withColumns('first', 'second', 'third')

        when:
        //noinspection GroovyAssignabilityCheck - dynamic dispatch can not statically infer arg type
        layout.withData().row(row)

        then:
        IllegalArgumentException e = thrown()

        e.message == expectedMesssage

        where:
        useCase                  | row                   || expected
        'too few columns'        | [1, 2]                || '''Data doesn't match the columns:
                                                               3 COLUMNS: [0-first, 1-second, 2-third]
                                                               2 DATA: [1, 2]'''
        'too many columns'       | [1, 2, 3, 4]          || '''Data doesn't match the columns:
                                                               3 COLUMNS: [0-first, 1-second, 2-third]
                                                               4 DATA: [1, 2, 3, 4]'''
        'missing mapped columns' | [first: 1, second: 2] || '''Non default column is null: 2-third
                                                               3 COLUMNS: [0-first, 1-second, 2-third]
                                                               3 DATA: {first=1, second=2}->[1, 2, null]'''
    }

    def 'specifying extra mapped columns is always ok (they are ignored)'() {
        setup:
        def layout = TextTable.withColumns('first', 'second')

        when:
        def table = layout.withData().row(first: 1, second: 2, extra: 'ignored').buildTable()

        then:
        notThrown(IllegalArgumentException)
        table.format(0, new StringBuilder()).toString() !=~ 'ignored'
    }

    def 'specifying extra row columns is an error with regular data builder'() {
        setup:
        def layout = TextTable.withColumns('first', 'second')

        when:
        layout.withData().row(1, 2, 'problem').buildTable()

        then:
        thrown(IllegalArgumentException)
    }

    def 'specifying extra row columns is OK with relaxed data builder'() {
        setup:
        def layout = TextTable.withColumns('first', 'second')

        when:
        def table = layout.withDataRelaxed().row(1, 2, 'ignored').buildTable()

        then:
        notThrown(IllegalArgumentException)
        table.format(0, new StringBuilder()).toString() !=~ 'ignored'
    }

    def 'in the case of sparse table, skipped columns in the middle are ignored, but extra past the end are an error'() {
        setup: 'Layout using 2nd, 3rd and 4th column in mixed order'
        def data = new TextTable.DataBuilder(
                true, // strict
                [
                        new TextTable.Column('family', 3),
                        new TextTable.Column('given', 1),
                        new TextTable.Column('middle', 2),
                ]
        )

        when: 'We format a 4 column row'
        def table = data
                .row('Mr.', 'Austin', 'Danger', 'Powers')
                .buildTable()

        then: 'The columns are in the expected order, the title is skipped'
        table.format(10, new StringBuilder()).toString()=='''
          +--------+--------+--------+
          | family | given  | middle |
          +--------+--------+--------+
          | Powers | Austin | Danger |
          +--------+--------+--------+
''' - '\n'

        when: 'We format a row with more than 4 columns'
        data.row('Mr.', 'Nigel', '', 'Powers', '(Austin\'s fasha)')

        then: 'exception is thrown (obvious unhandled data)'
        thrown(IllegalArgumentException)
    }

    def 'in the case of sparse table, extra columns past the end are also ignored'() {
        setup: 'Layout using 2nd, 3rd and 4th column in mixed order'
        def data = new TextTable.DataBuilder(
                false, // lenient
                [
                        new TextTable.Column('family', 3),
                        new TextTable.Column('given', 1),
                        new TextTable.Column('middle', 2),
                ]
        )

        when: 'We format a row with more than 4 columns'
        def table = data
                .row('Mr.', 'Nigel', '', 'Powers', '(Austin\'s fasha)')
                .buildTable()

        then: 'The columns are in the expected order, the title and note is skipped'
        table.format(10, new StringBuilder()).toString()=='''
          +--------+-------+--------+
          | family | given | middle |
          +--------+-------+--------+
          | Powers | Nigel |        |
          +--------+-------+--------+
''' - '\n'
    }

    def 'in the case of sparse table, it may turn out that the row is not long enough'() {
        setup: 'Layout using 2nd, 3rd and 4th column in mixed order'
        def data = new TextTable.DataBuilder(
                false, // lenient
                [
                        new TextTable.Column('family', 3),
                        new TextTable.Column('given', 1),
                        new TextTable.Column('middle', 2),
                ]
        )

        when: 'We format a row with less than 4 columns with lenient layout'
        data.row('Mr.', 'Nigel', 'Powers').buildTable()

        then: 'The columns are in the expected order, the title and note is skipped'
        IllegalArgumentException e = thrown()
        e.message == '''Column index out of range: 3-family
                        3 COLUMNS: [3-family, 1-given, 2-middle]
                        3 DATA: [Mr., Nigel, Powers]'''.replace(EOL, '\n').replaceAll(~/\n\s*/, '\n').replace('\n', EOL)
    }

    def 'we can make columns optional by setting up a default value supplier'(useCase, List<?> rows, String expected) {
        setup:
        def layout = TextTable.withColumns()
            .column('first')
            .column('second') { it.defaultValue = 'n/a' }
            .column('notes')  { it.defaultValue = new AtomicInteger().&getAndIncrement }

        when:
        def data = layout.withData()
        for (row in rows) data.row(row)

        def table = data.buildTable()

        then:
        useCase && table.format(0, new StringBuilder()) as String == expected.replaceAll(~/\n\s*/, '\n')

        where:
        useCase                  | rows                               || expected
        'missing array column'   | [[1, 2, 3], [1, null, 3         ]] || '''+-------+--------+-------+
                                                                            | first | second | notes |
                                                                            +-------+--------+-------+
                                                                            | 1     | 2      | 3     |
                                                                            | 1     | n/a    | 3     |
                                                                            +-------+--------+-------+
                                                                         '''
        'missing mapped columns' | [[1, 2, 3], [first: 1, second: 2]] || '''+-------+--------+-------+
                                                                            | first | second | notes |
                                                                            +-------+--------+-------+
                                                                            | 1     | 2      | 3     |
                                                                            | 1     | 2      | 1     |
                                                                            +-------+--------+-------+
                                                                         '''
        '2 missing columns'      | [[1, 2, null], [first: 1        ]] || '''+-------+--------+-------+
                                                                            | first | second | notes |
                                                                            +-------+--------+-------+
                                                                            | 1     | 2      | 1     |
                                                                            | 1     | n/a    | 2     |
                                                                            +-------+--------+-------+
                                                                         '''
    }

    def 'optional columns with tupple data replace nulls, but still require right number of elements'() {
        setup:
        def layout = TextTable.withColumns('first', 'second')
                              .column('third') { it.defaultValue = 'n/a' }

        when:
        layout.withData().row(1, 2)

        then:
        IllegalArgumentException e = thrown()
        e.message == '''Data doesn't match the columns:
                       3 COLUMNS: [0-first, 1-second, 2-third]
                       2 DATA: [1, 2]'''.replace(EOL, '\n').replaceAll(~/\n\s*/, '\n').replace('\n', EOL)
    }

    def "value alignment can be specified per column"(useCase, List<Double> align, String expected) {
        expect:
        TextTable.withColumns()
                 .column('first') { it.alignment = align[0] }
                 .column('second') { it.alignment = align[1] }
                 .withData().rows([
                     ['foo bar baz qux', ''],
                     ['qux', '123']
                 ])
                 .buildTable()
                 .format(0, new StringBuilder())
                 .toString()==expected

        where:
        useCase     | align
        'left'      | [0.0, 0.0]
        'right'     | [1.0, 1.0]
        'center'    | [0.5, 0.5]
        'mixed'     | [1.0, 0.5]
        'irregular' | [0.2, 0.7]

        expected << [
                '''+-----------------+--------+
                   | first           | second |
                   +-----------------+--------+
                   | foo bar baz qux |        |
                   | qux             | 123    |
                   +-----------------+--------+
                ''',
                '''+-----------------+--------+
                   |           first | second |
                   +-----------------+--------+
                   | foo bar baz qux |        |
                   |             qux |    123 |
                   +-----------------+--------+
                ''',
                '''+-----------------+--------+
                   |      first      | second |
                   +-----------------+--------+
                   | foo bar baz qux |        |
                   |       qux       |  123   |
                   +-----------------+--------+
                ''',
                '''+-----------------+--------+
                   |           first | second |
                   +-----------------+--------+
                   | foo bar baz qux |        |
                   |             qux |  123   |
                   +-----------------+--------+
                ''',
                '''+-----------------+--------+
                   |   first         | second |
                   +-----------------+--------+
                   | foo bar baz qux |        |
                   |   qux           |   123  |
                   +-----------------+--------+
                '''
        ].collect { it.replaceAll ~/\n\s*/, '\n' }
    }

    def 'we can disable the data profiling by setting `pendingAutoformat` to false'() {
        setup:
        def layout = TextTable.withColumns('A', 'B')

        when: 'we disable autoformat and try to put data longer than the column name in column A'
        def table = layout.withData().row(A: "123", B: 2).buildTable()
        table.pendingAutoformat = false
        table.format(10, new StringBuilder())

        then: 'we get exception'
        IllegalStateException e = thrown()
        e.message == "Formatted string '123'::length==3 > column 0-A::width==1"

        when: 'if we manually sized column A and put the same data (for illustration purposes only)'
        //noinspection GroovyAccessibility
        table.columns.find { it.name=='A' }.width = 4
        def formatted = table.format(10, new StringBuilder()).toString()

        then: 'all works'
        formatted == """
          +------+---+
          | A    | B |
          +------+---+
          | 123  | 2 |
          +------+---+
""" - '\n'
    }

    @SuppressWarnings("GroovyAssignabilityCheck")
    def "we can specify custom formatters"() {
        when:
        def table = TextTable.withColumns()
            .column('category')
            .column('count') {
                it.alignment=1
                it.formatter = {it in Number ? '(0x' + Long.toHexString(it) + ") " + it : it.toString() }
            }.column('class') {
                it.alignment=1
                it.formatter = { it.toString().toUpperCase() }
            }.column('mixed') { column ->
                def defaultFormatter = column.formatter
                column.formatter = { column.alignment = it in Number ? 1 : 0; defaultFormatter.apply(it) }
            }.withData().rows([
                    ['cats', 1432, 'Bad', 123],
                    ['pooches', 45, 'a+', 'abc']
            ]).buildTable()

        then:
        table.format(10, new StringBuilder()).toString()=="""
          +----------+--------------+-------+-------+
          | category |        count | class | mixed |
          +----------+--------------+-------+-------+
          | cats     | (0x598) 1432 |   BAD |   123 |
          | pooches  |    (0x2d) 45 |    A+ | abc   |
          +----------+--------------+-------+-------+
""" - '\n'
    }

    def "when formatters change alignment and padding, they are restored for the next row"() {
        when:
        def table = TextTable.withColumns().column('mixed') { column ->
                def defaultFormatter = column.formatter
                column.formatter = {
                    if (it in Number) column.alignment = 1
                    defaultFormatter.apply(it)
                }
            }.withData().rows([
                    [ 'abc'],
                    [  123 ],
                    [ 'abc']
            ]).buildTable()

        then:
        table.format(10, new StringBuilder()).toString()=="""
          +-------+
          | mixed |
          +-------+
          | abc   |
          |   123 |
          | abc   |
          +-------+
""" - '\n'
    }
}
