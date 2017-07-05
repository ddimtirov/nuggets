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

package io.github.ddimitrov.nuggets;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p><span class="badge green">Entry Point</span> Formats data as text-table, suitable for logging and printing. The recommended way is to
 * use a builder-based DSL (though you may use the classes directly to achieve advanced scenarios).
 * All following examples focus on the DSL:</p>
 *
 * <pre><code>
 * String table = TextTable.withColumns("key", "value", "notes")
 *                         .withData()
 *                         .row("OP", "Austin Powers", "International Man of Mystery")
 *                         .row("NP", "Nigel Powers", "NP complete")
 *                         .buildTable()
 *                         .format(10, new StringBuilder())
 *                         .toString();
 * </code></pre>
 *
 * <p>Which will be rendered as:</p>
 *
 * <pre><tt>
 * +-----+---------------+------------------------------+
 * | key | value         | notes                        |
 * +-----+---------------+------------------------------+
 * | OP  | Austin Powers | International Man of Mystery |
 * | NP  | Nigel Powers  | NP complete                  |
 * +-----+---------------+------------------------------+
 * </tt></pre>
 *
 * <a>We may as well specify different visual style like this:</a>
 *
 * <pre><code>
 * presetBox(Box.UNICODE_THIN); // will draw the table using nice thin box characters.
 * </code></pre>
 *
 *
 * <p>Which will change the rendering to a nice box using unicode's pseudo-graphic characters.
 * (Blame Java's encoding handling for not being able to include it in the Javadoc easily.)</p>
 *
 *
 * <p>Typically, we would want to define the table layout once and use it to format multiple
 * data sets:</p>
 *
 * <pre><code>
 * TextTable.LayoutBuilder layout = TextTable.withColumns("key", "value", "notes");
 *
 * layout.withData()
 *       .row("OP", "Austin Powers", "International Man of Mystery")
 *       .row("NP", "Nigel Powers", "NP complete")
 *       .buildTable()
 *       .format(10, System.err);
 *
 * layout.withData()
 *       .row("foo", "Fubar", "beyond all repair")
 *       .row("baz", "Bazqux", "")
 *       .buildTable()
 *       .format(10, System.err);
 * </code></pre>
 *
 * <p>Or we may keep the table object around and format it to different outputs:</p>
 *
 * <pre><code>
 * TextTable table = TextTable.withColumns("key", "value")
 *                            .column("notes") // columns can be specified one-by-one
 *                            .withData()
 *                            .rows( // add rows in bulk
 *                              new String[] { "OP", "Austin Powers", "International Man of Mystery" },
 *                              new String[] { "NP", "Nigel Powers", "NP complete" },
 *                            )
 *                            .row()
 *                            .buildTable();
 *
 * table.format(0, System.err);
 * table.format(10, System.out);
 * StringBuilder renderedTable = table.format(10, new StringBuilder());
 * </code></pre>
 *
 * <p>By default each column is mandatory, left-aligned and rendered as string with 1 space
 * padding. We can tweak all these per column with DSL like this:</p>
 *
 * <pre><code>
 * TextTable.LayoutBuilder layout = TextTable
 *      .withColumns()
 *      .column("key"  , c -&gt; c.alignment=1) // right-aligned
 *      .column("value", c -&gt; c.padding = 0) // remove padding
 *      .column("notes", c -&gt; {
 *            c.defaultValue="n/a";
 *            c.formatter = val -&gt; val.toString().toLowerCase(); // custom formatter
 *       });
 * </code></pre>
 *
 * <p>If a value is an instance of {@code Supplier} or {@code Callable} (or id the value
 * is {@code null} and the default value is instance of these interfaces), the functional
 * interface will be called and the result will be rendered.</p>
 *
 * <pre><code>
 * TextTable.LayoutBuilder layout = TextTable
 *      .withColumns()
 *      .column("key"  , c -&gt; c.alignment=1) // right-aligned
 *      .column("value", c -&gt; c.padding = 0) // remove padding
 *      .column("notes", c -&gt; {
 *            c.defaultValue="n/a;
 *            c.formatter = val -&gt; val.toString().toLowerCase(); // custom formatter
 *       });
 * </code></pre>
 *
 * <p>A formatter is allowed to change a column's metadata, which will be reset after each rendered value.
 * Also we can access the columns after they are defined as follows:</p>
 *
 * <pre><code>
 * TextTable.LayoutBuilder layout = TextTable.withColumns("Name", "Age", "Bio");
 * DecimalFormat df = new DecimalFormat("#,###");
 * layout.getAllColumns().each {c -&gt; { // for all columns
 *   c.formatter = value -&gt; {
 *       if (value instanceof Number) {
 *           c.alignment = 1; // right-align
 *           return df.format(((Number) value).longValue());
 *       }
 *       return String.valueOf(value).trim(); // will be left-aligned as default
 *   }
 *   c.defaultValue = new AtomicInteger::incrementAndGet;
 * }}
 * </code></pre>
 *
 * <p>There is no synchronization in any of the classes, but both the columns and table are practically
 * immutable once defined (unless the formatters change the column fields). This means that both tables
 * and layouts can be shared between threads. The builders on the other side are stateful and they are
 * best confined to a single thread.</p>
 */
public class TextTable {
    /**
     * Determines the characters used to draw the lines and corners of a table
     * Defaults to ASCII graphics.
     *
     * @see #presetBox(Style)
     * @see Box
     */
    public static volatile Style style;

    /**
     * End-of-line character used to separate the lines of the table.
     * Defaults to the OS-specific EOL character.
     *
     * @see #presetBox(Style)
     * @see System#lineSeparator()
     */
    public static volatile String eol;

    /**
     * Pattern used pad the table values when different from the column width.
     * Could be longer than one character, in which case they will be repeated
     * to fill the desired gap (useful for subtle counting patterns).
     * Defaults to space character {@code "\\u0020"}.
     *
     * @see #presetBox(Style)
     */
    public static volatile String padding;

    /**
     * Pattern used to indent the table if not starting from column zero.
     * Could be longer than one character, in which case they will be repeated
     * to fill the desired gap (useful for subtle counting patterns).
     * Defaults to space character {@code "\\u0020"}.
     *
     * @see #presetBox(Style)
     */
    public static volatile String indent;

    /**
     * Whether to draw a frame around the table or just leave the internal
     * heading and column separators.
     */
    public static volatile boolean outerFrame;

    static {
        presetBox(Box.ASCII);
    }

    // while technically not immutable, these fields are private and
    // the TextTable class will not modify the collections
    private final @NotNull List<@NotNull Column> columns;
    private final @NotNull List<@NotNull List<?>> data;
    private final @NotNull Map<Integer, Separator> separatorBefore;
    private final @NotNull List<SiblingAwareFormatter> siblingAwareFormatters;

    /**
     * If set to true, before calling {@link #format(int, Appendable)} all column widths
     * will be adjusted to fit the data, then this field will be reset to false.
     */
    public boolean pendingAutoformat = true;

    /**
     * Sets all static parameters of {@code TextTable}, affecting the visual style.
     * Typically used with one of the {@code Box} presets:
     * <pre><code>
     * presetBox(Box.UNICODE_THIN); // will draw the table using nice thin box characters.
     * </code></pre>
     *
     * @param style the box preset to use. The {@link #eol EOL}, {@link #indent}
     *              and {@link #padding} are always reset to the defaults.
     */
    public static void presetBox(@NotNull Style style) {
        indent = " ";
        padding = " ";
        outerFrame = true;
        eol = System.lineSeparator();
        TextTable.style = style;
    }

    /**
     * Starts a builder chain for defining a layout, which one can use to create a table.
     *
     * @param columnNames a list of column names, if no customizations are needed
     * @return a builder to customize the table layout
     * @see LayoutBuilder#column(String, Consumer)
     * @see LayoutBuilder#withData()
     */
    @Contract(pure = true)
    public static @NotNull LayoutBuilder withColumns(@NotNull String... columnNames) {
        LayoutBuilder layoutBuilder = new LayoutBuilder();
        for (String columnName : columnNames) {
            layoutBuilder.column(columnName);
        }
        return layoutBuilder;
    }

    /**
     * <p>Constructor to create a table object given prepared columns and data.
     * Typically one would use the builder API as follows:</p>
     * <pre><code>
     * TextTable table = TextTable.withColumns(
     *                   "key"   , "value"
     * ).withData().rows(
     *    new String[] { "abc"   ,  123     },
     *    new String[] { "foobar", "bazqux" },
     * ).buildTable();
     *
     * table.format(10, System.out);   // print to stdout with indentation 10 chars
     * logger.info(table.format(0, new StringBuilder()));   // log to a logger
     * </code></pre>
     *
     * @param columns         list of columns describing the layout of the table
     * @param data            collection of data rows
     * @param separatorBefore map of row index to separator style
     */
    public TextTable(@NotNull List<@NotNull Column> columns, @NotNull List<@NotNull List<?>> data, @Nullable TreeMap<Integer, Separator> separatorBefore) {
        this.columns = columns;
        this.data = data;
        this.separatorBefore = separatorBefore != null ? separatorBefore : Collections.emptyMap();

        List<SiblingAwareFormatter> safs = new ArrayList<>();
        for (Column column : columns) {
            if (column.formatter instanceof SiblingAwareFormatter) {
                SiblingAwareFormatter saf = (SiblingAwareFormatter) column.formatter;
                saf.lookup = new SiblingLookup(column.name);
                safs.add(saf);
            }
        }
        siblingAwareFormatters = safs;
    }

    /**
     * <p>Render the table as text, based on the layout, per-column formatter, alignment and padding; and
     * the {@code indent} parameter. If {@code pendingAutoformat} field is set to {@code true}, this
     * method will also adjust the widths of the columns, so that the data will fit in the table.</p>
     *
     * @param indent the number of characters to put to the left of the start of the table.
     * @param out the destination to which to append the text.
     * @param <F> captures the type of the {@code out} parameter, so we can return it.
     * @return the {@code out} parameter for chaining.
     * @throws IOException passing through exceptions from {@link Appendable#append(char)}
     *
     * @see #pendingAutoformat
     */
    public <F extends Appendable> @NotNull F format(int indent, @NotNull F out) throws IOException {
        return format(indent, out, outerFrame, true);
    }

    /**
     * <p>Render the table as text, based on the layout, per-column formatter, alignment and padding; and
     * the {@code indent} parameter. If {@code pendingAutoformat} field is set to {@code true}, this
     * method will also adjust the widths of the columns, so that the data will fit in the table.</p>
     *
     * @param indent the number of characters to put to the left of the start of the table.
     * @param out the destination to which to append the text.
     * @param outerFrame if {@code false} will not render the outer frame
     * @param includeHeader if {@code false} will not render the header and header-values separator
     * @param <F> captures the type of the {@code out} parameter, so we can return it.
     * @return the {@code out} parameter for chaining.
     * @throws IOException passing through exceptions from {@link Appendable#append(char)}
     *
     * @see #pendingAutoformat
     */
    public <F extends Appendable> @NotNull F format(int indent, @NotNull F out, boolean outerFrame, boolean includeHeader) throws IOException {
        if (pendingAutoformat) {
            autoformatFromContent();
            pendingAutoformat = false;
        }

        StringBuilder indentPad = pad(new StringBuilder(indent), indent, TextTable.indent);

        // outer-frame: top
        if (outerFrame) {
            out.append(indentPad);
            appendFrameHline(out, style.joints(0));
        }

        if (includeHeader) {
            // header row
            siblingAwareFormatters.forEach(saf -> saf.lookup.row=0);
            out.append(indentPad);
            appendRow(out, null, indentPad);

            // inner-frame: header/values separator
            out.append(indentPad);
            appendFrameHline(out, style.joints(1));
        }

        // rows
        int itemIdx=0;
        for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
            for (SiblingAwareFormatter saf : siblingAwareFormatters) saf.lookup.row=rowIdx;
            List<?> row = data.get(rowIdx);
            Separator sep = separatorBefore.get(itemIdx++);
            if (sep!=null) {
                out.append(indentPad);
                appendFrame(out, sep);
            }
            out.append(indentPad);
            appendRow(out, row, indentPad);
        }

        // outer-frame: bottom
        if (outerFrame) {
            out.append(indentPad);
            appendFrameHline(out, style.joints(2));
        }
        return out;
    }

    /**
     * Scan the table content and default column parameters, making sure
     * that each column is at least as wide as the longest rendered value.
     */
    @SuppressWarnings("try")
    private void autoformatFromContent() {
        for (Column column : columns) {
            SiblingAwareFormatter saf = column.formatter instanceof SiblingAwareFormatter
                    ? (SiblingAwareFormatter) column.formatter
                    : null;

            for (int rowIdx = 0; rowIdx < data.size(); rowIdx++) {
                List<?> row = data.get(rowIdx);
                if (saf!=null) saf.lookup.row = rowIdx;

                int cellWidth;
                try (Column.Memento ignored = column.new Memento()) {
                    Object rawCell = row.get(column.index);
                    String formattedCell = column.format(rawCell);
                    cellWidth = Arrays.stream(formattedCell.split(eol))
                                   .mapToInt(String::length)
                                   .max().orElse(0);
                }
                column.width = Math.max(column.width, cellWidth);

            }
        }
    }

    private void appendFrame(@NotNull Appendable out, @NotNull Separator separator) throws IOException {
        String horizontal = separator.horizontalGlyph != null ? separator.horizontalGlyph : style.horizontal();
        String[] jointRow = separator.junctionGlyphs != null ? separator.junctionGlyphs : style.joints(2);


        int leftBorderSize = outerFrame && jointRow[0] != null ? jointRow[0].length() : 0;
        int rightBorderSize = outerFrame && jointRow[2] != null ? jointRow[2].length() : 0;
        int jointSize = jointRow[1] != null ? jointRow[1].length() : 0;
        int size = columns.stream().mapToInt(x -> x.width + x.padding*2).sum()
                + leftBorderSize + jointSize * (columns.size()-1) + rightBorderSize;
        StringBuilder sb = new StringBuilder(size+16);
        appendHline(sb, jointRow, horizontal);
        assert sb.length()==size;

        if (separator.name!=null) {
            int maxTitleSize = Math.max(0, size - 2 * separator.padding);
            int alignGap = maxTitleSize - separator.name.length();
            if(alignGap>0) {
                int start = separator.padding + (int) (alignGap * separator.alignment);
                sb.replace(start, start+separator.name.length(), separator.name);
            } else {
                String title;
                if (maxTitleSize>9) {
                    title = separator.name.substring(0, (maxTitleSize-3) / 2);
                    title += "...";
                    title += separator.name.substring(separator.name.length()-(maxTitleSize-title.length()));
                } else {
                    title = separator.name.substring(0, maxTitleSize);
                }
                assert title.length()==maxTitleSize;
                sb.replace(separator.padding, size - separator.padding, title);
            }
            assert sb.length()==size;
        }

        out.append(sb).append(eol);
    }

    /**
     * Appends formatted text line for horizontal table separator, including line terminator.
     * Indentation is no appended as we want to reuse the rendering buffer
     * (see the implementation of {@link #format(int, Appendable)}).
     * @param out destination for the table row.
     * @param jointRow array of 3 values for left, middle and center joints.
     * @throws IOException if the {@param out} throws while appending.
     * @see Style#joints(int)
     */
    private void appendFrameHline(@NotNull Appendable out, String[] jointRow) throws IOException {
        String horizontal = style.horizontal();
        if (horizontal==null) return;

        appendHline(out, jointRow, horizontal);
        out.append(eol);
    }

    /**
     * Appends formatted text line for horizontal table separator, including line terminator.
     * Indentation is no appended as we want to reuse the rendering buffer
     * (see the implementation of {@link #format(int, Appendable)}).
     * @param out destination for the table row.
     * @param jointRow array of 3 values for left, middle and center joints.
     * @param horizontal the glyph used to draw the hlines of this table
     * @throws IOException if the {@param out} throws while appending.
     * @see Style#joints(int)
     */
    private void appendHline(@NotNull Appendable out, String[] jointRow, @Nullable String horizontal) throws IOException {
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);

            if (outerFrame && jointRow[0]!=null && i == 0) out.append(jointRow[0]);
            if (column.hidden) continue;
            pad(out, column.padding * 2 + column.width, horizontal !=null ? horizontal : " ");
            if (i >= columns.size() - 1) {
                String jointGlyph = jointRow[2];
                if (outerFrame && jointGlyph != null) out.append(jointGlyph);
            } else {
                String jointGlyph = jointRow[1];
                if (jointGlyph!=null) out.append(jointGlyph);
            }
        }
    }

    /**
     * Appends formatted text snipped for single table row or header, including padding and
     * line terminator. Indentation is not appended for the first line, but in case there are
     * cell values rendering to multi-line, it will be appnded on the continuation lines (see
     * the implementation of {@link #format(int, Appendable)}).
     *
     * @param out destination for the table row.
     * @param row the values to append. If {@code null} - append header with the column names.
     * @param continuationIndentPad the padding to use for
     * @throws IOException if the {@param out} throws while appending.
     */
    @SuppressWarnings("try")
    private void appendRow(@NotNull Appendable out, @Nullable List<?> row, StringBuilder continuationIndentPad) throws IOException {
        Column lastColumn = columns.get(columns.size() - 1);

        // rethink the approach if efficiency becomes an issue
        // we can have a fast-path for single-line values and lazy allocation of arrays
        int maxIndex = columns.stream().mapToInt(x->x.index).max().orElseThrow(() -> new IllegalStateException("No columns defined!"));
        String[][] multilineValues = new String[maxIndex+1][];
        Column.Memento[] multilineFormating = new Column.Memento[maxIndex+1];

        for (Column column : columns) {
            if (column.hidden) continue;

            try (Column.Memento ignored = column.new Memento()) {
                String formatted;
                if (row == null) {
                    formatted = column.name;
                } else {
                    Object rawValue = row.get(column.index);
                    Object defaultedValue = rawValue == null ? column.defaultValue : rawValue;
                    formatted = column.format(defaultedValue);
                }

                String[] lines = formatted.split(eol);
                multilineFormating[column.index] = column.new Memento();
                multilineValues[column.index] = lines;
            }
        }

        int maxLines = Arrays.stream(multilineValues).filter(Objects::nonNull).mapToInt(x->x.length).max().orElse(0);
        for (int lineIdx = 0; lineIdx < maxLines; lineIdx++) {
            if (lineIdx>0) out.append(continuationIndentPad);
            if (outerFrame && style.vertical()!=null) out.append(style.vertical());
            for (Column column : columns) {
                if (column.hidden) continue;

                Column.Memento paragraphFormatting = multilineFormating[column.index];
                String[] lines = multilineValues[column.index];
                String formatted = lines!=null && lines.length>lineIdx ? lines[lineIdx] : "";
                if (formatted.length() > column.width) {
                    throw new IllegalStateException(
                            "Formatted string '" + formatted + "'::length==" + formatted.length() +
                                    " > column " + column + "::width==" + column.width);
                }
                appendValue(out, formatted,
                        paragraphFormatting.width, paragraphFormatting.padding, paragraphFormatting.alignment,
                        paragraphFormatting.rightBorder, column != lastColumn);
            }
            out.append(eol);
        }
    }

    private void appendValue(@NotNull Appendable out, String formattedValue, int width, int padding, double alignment, @Nullable String rightBorder, boolean lastColumn) throws IOException {
        if (formattedValue.isEmpty()) {
            // in case of multi-char padding string, don't restart in the middle
            pad(out, width + padding * 2, TextTable.padding);
        } else {
            int alignmentGap = width - formattedValue.length();
            int leftAlignmentPad = (int) (alignmentGap * alignment);
            int rightAlignmentPad = alignmentGap - leftAlignmentPad;
            pad(out, leftAlignmentPad + padding, TextTable.padding);
            out.append(formattedValue);
            pad(out, rightAlignmentPad + padding, TextTable.padding);
        }
        String rightBorderGlyph = rightBorder ==null ? style.vertical() : rightBorder;
        if ((lastColumn || outerFrame) && rightBorderGlyph !=null) out.append(rightBorderGlyph);
    }

    /**
     * <p>Specifies the characters used to draw the lines and corners of a {@link TextTable table}.
     * As of now, the whole table is drawn with the same set of characters
     * (we don't allow to customize per row or per cell).</p>
     * <p>Typically you would want to use one of the {@link Box} values, though
     * you may provide your own styles too.</p>
     */
    public interface Style {
        /**
         * Determines the characters used to draw the horizontal lines of a table.
         * @return horizontal line string. If multiple characters, will be repeated
         *         and trimmed as required to reach the desired size.
         *         If {@code null}, horizontal separators will be skipped entirely.
         * @see TextTable#style
         * @see #presetBox(Style)
         */
        @Contract(pure = true)
        @Nullable String horizontal();

        /**
         * Determines the characters used to draw the vertical lines of a table.
         * @return vertical line string. If multiple characters, the column separators
         *         will be using the whole string (make sure to have matching T-joints
         *         and cross-joints).
         *         If {@code null}, vertical separators will be skipped entirely.
         * @see TextTable#style
         * @see #presetBox(Style)
         */
        @Contract(pure = true)
        @Nullable String vertical();

        /**
         * Determines the characters used to draw the joints and corners of a table.
         * The joints are defined as a virtual 3x3 array, with values as follows:
         * <pre>
         * String[][] joints = {
         *     { TOP_LEFT_CORNER    , TOP_T_JOINT    , TOP_RIGHT_CORNER    },
         *     { LEFT_T_JOINT       , CROSS_JOINT    , RIGHT_T_JOINT       },
         *     { BOTTOM_LEFT_CORNER , BOTTOM_T_JOINT , BOTTOM_RIGHT_CORNER },
         * }
         * </pre>
         * @param row which row of the 9-box to return.
         * @return array of 3 strings to be used as left corner, center and right-corner for the specified {@code row}.
         *         See examples in {@link Box}.
         * @see TextTable#style
         * @see #presetBox(Style)
         */
        @Contract(pure = true)
        @NotNull String[] joints(int row);
    }

    /**
     * Config object, modelling a table internal horizontal separator
     */
    public static class Separator {
        /**
         * Text that should be rendered in the separator line.
         * If you want start/end decorations (i.e. square brackets), make them part of the name.
         */
        public final @Nullable String name;

        /**
         * The glyph used to draw the hlines.
         * @see Style#horizontal()
         */
        public @Nullable String horizontalGlyph;

        /**
         * The glyphs used to draw left, center and right joints.
         * @see Style#joints(int)
         */
        public @Nullable String[] junctionGlyphs;

        /**
         * Title alignment - 0 for left; 0.5 for center; 1 for right
         * @see Column#alignment
         */
        public double alignment = 0;

        /**
         * Title padding - distance from the frame to the title.
         * @see Column#padding
         */
        public int padding = 3;

        private Separator(@Nullable String name, @Nullable String horizontalGlyph, @Nullable String[] junctionGlyphs) {
            this.name = name;
            this.horizontalGlyph = horizontalGlyph;
            this.junctionGlyphs = junctionGlyphs;
        }

        /**
         * Factory method fro separator instances.
         * @param name the title that should be rendered in the separator. If you want start/end
         *             decorations, make them part of the name.
         * @param horizontalGlyph the glyph used to draw the hlines.
         * @param junctionGlyphs same as in {@link Style#joints(int)}
         * @param config a closure allowing to set alignment, padding, etc.
         * @return a new instance of {@code Separator}
         */
        static Separator of(@Nullable String name, @Nullable String horizontalGlyph, @Nullable String[] junctionGlyphs, @Nullable Consumer<Separator> config) {
            Separator separator = new Separator(name, horizontalGlyph, junctionGlyphs);
            if (config!=null) config.accept(separator);
            return separator;
        }
    }

    /**
     * <p>Preset styles, specifying the characters used to draw the lines and corners
     * of a {@link TextTable table}. As of now, the whole table is drawn with the same
     * set of characters (we don't allow to customize per row or per cell).</p>
     * <p>You don't need to use this class if you prefer to provide your own styles.</p>
     * @see TextTable#presetBox(Style)
     */
    public enum Box implements Style {
        /** No decorations - good start if you want to customize */
        NO_BOX(null, null,
                new String[]{"NOT_USED", "NOT_USED", "NOT_USED"},
                new String[]{"NOT_USED", "NOT_USED", "NOT_USED"},
                new String[]{"NOT_USED", "NOT_USED", "NOT_USED"}),

        ASCII_HORIZONTAL("-", null,
                new String[]{"-", "-", "-"},
                new String[]{"-", "-", "-"},
                new String[]{"-", "-", "-"}),
        ASCII_HORIZONTAL_DOUBLE("=", null,
                new String[]{"=", "=", "="},
                new String[]{"=", "=", "="},
                new String[]{"=", "=", "="}),
        /** Plain ASCII - suitable for every log file */
        ASCII("-", "|",
                new String[]{"+", "+", "+"},
                new String[]{"+", "+", "+"},
                new String[]{"+", "+", "+"}),

        /** Stylish and slim, spruce your stdio with UNICODE_THIN */
        UNICODE_THIN("\u2500", "\u2502",
                new String[]{"\u250c", "\u252c", "\u2510"},
                new String[]{"\u251c", "\u253c", "\u2524"},
                new String[]{"\u2514", "\u2534", "\u2518"}),

        /** ...but how about drawing rectangles with rounded corners? Can we do that now, too? */
        UNICODE_THIN_ROUNDED("\u2500", "\u2502",
                new String[]{"\u256d", "\u252c", "\u256e"},
                new String[]{"\u251c", "\u253c", "\u2524"},
                new String[]{"\u2570", "\u2534", "\u256f"}),

        /** Sturdy and solid, nothing stands out quite like UNICODE_THICK */
        UNICODE_THICK("\u2501", "\u2503",
                new String[]{"\u250f", "\u2533", "\u2513"},
                new String[]{"\u2523", "\u254b", "\u252b"},
                new String[]{"\u2517", "\u253b", "\u251b"}),

        /** Because sometimes two is better than one */
        UNICODE_DOUBLE("\u2550", "\u2551",
                new String[]{"\u2554", "\u2566", "\u2557"},
                new String[]{"\u2560", "\u256c", "\u2563"},
                new String[]{"\u255a", "\u2569", "\u255d"});

        private final @Nullable String horizontal;
        private final @Nullable String vertical;

        @SuppressWarnings("ImmutableEnumChecker")
        private final @NotNull String[][] joints;

        Box(@Nullable String horizontal, @Nullable String vertical, @NotNull String[]... joints) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.joints = joints;
        }

        @Override
        @Contract(pure = true)
        public @Nullable String horizontal() {
            return horizontal;
        }

        @Override
        @Contract(pure = true)
        public @Nullable String vertical() {
            return vertical;
        }

        @Override
        @Contract(pure = true) // we are leaking the raw array - not worth creating garbage... please don't modify it!
        public @NotNull String[] joints(int row) {
            return joints[row];
        }
    }

    /**
     * Defines the formatting attributes for a single column of a {@link TextTable table}.
     */
    public static class Column {
        /**
         * The column name.
         */
        public final @NotNull String name;

        /**
         * The index of the data row that should be displayed in this column.
         * When you use a builder, this is managed automatically (auto-increment).
         */
        public final int index;

        /**
         * The column width (measured in characters)
         */
        public int width;

        /**
         * Optionally overrides the right border for the column. Can be changed per row.
         * If the value is {@code null}, {@link TextTable#style style.vertical()} will be used.
         */
        public @Nullable String rightBorder = null;

        /**
         * Padding to be left between the value and the column separator (measured in characters)
         */
        public int padding = 1;

        /**
         * Alignment of the rendered value (0-left, 1-right, 0.5-center, 0.75-slightly rightish)
         */
        public double alignment = 0d;

        /**
         * Function to convert from row values to string. The formatter may change any of
         * the mutable attributes of the column, impacting the rendering of the value
         * (they will be reset for the next row).
         */
        public @NotNull Function<@Nullable Object, String> formatter = Column::defaultFormatter;

        /**
         * Hidden columns will not be rendered, but their values can be accessed through
         * {@link SiblingLookup}.
         *
         * @see #withSiblingLookup(BiFunction)
         */
        public boolean hidden;

        /**
         * Used if the row value is {@code null}.
         */
        public @Nullable Object defaultValue;

        /**
         * <p>Memorizes the mutable fields of its enclosing class and restores them on
         * {@link #restore()} or {@link #close()}.</p>
         *
         * <p>As this class implements {@code Autocloseable}, it can be used in a
         * try-with-resources construct in RAII manner as follows:</p>
         *
         * <pre><code>
         * \@SuppressWarnings("try") // to prevent the compiler from complaining
         * void preserveColumnSettings() {
         *     try(Column.Memento ignore = column.new Memento()) {
         *         // do things that modify the fields `column`
         *     }
         *     // at this point all fields of `column`  will have their values from before the try statement
         * }
         * </code></pre>
         */
        protected class Memento implements AutoCloseable {
            private int width = Column.this.width;
            private int padding = Column.this.padding;
            private double alignment = Column.this.alignment;
            private @NotNull Function<@Nullable Object, String> formatter = Column.this.formatter;
            private @Nullable String rightBorder = Column.this.rightBorder;
            private @Nullable Object defaultValue = Column.this.defaultValue;

            /**
             * <p>Restore the mutable fields of the enclosing class to the values captured
             * at the time of instantiation.</p>
             *
             * <p>To be used in a try-with-resources construct in RAII manner as follows:</p>
             *
             * <pre><code>
             * \@SuppressWarnings("try") // to prevent the compiler from complaining
             * void preserveColumnSettings() {
             *     try(Column.Memento ignore = column.new Memento()) {
             *         // do things that modify the fields `column`
             *     }
             *     // at this point all fields of `column`  will have their values from before the try statement
             * }
             * </code></pre>
             */
            @Override public void close() { restore(); }

            /**
             * Restore the mutable fields of the enclosing class to the values captured
             * at the time of instantiation.
             * @see #close()
             */
            protected void restore() {
                Column.this.width = width;
                Column.this.padding = padding;
                Column.this.alignment = alignment;
                Column.this.formatter = formatter;
                Column.this.defaultValue = defaultValue;
                Column.this.rightBorder = rightBorder;
            }
        }

        /**
         * Construct a new column with specified name, taking data from the specified index.
         * @param name the name of this column (will be used in the table header and as key for mapped columns)
         * @param index the index of the data value within a row
         */
        public Column(@NotNull String name, int index) {
            this.name = name;
            this.index = index;
            width = name.length();
        }

        /**
         * Creates formatters that are aware of the row number, can reference sibling values from
         * the same row, or aggregates of all values in a column.
         *
         * @param siblingAwareFormatter a 2 argument function accepting the current value and sibling
         *                              lookup, and returning formatted value,
         * @return a normal formatter function.
         * @see #formatter
         */
        public @NotNull Function<@Nullable Object, String> withSiblingLookup(BiFunction<@Nullable Object, SiblingLookup, String> siblingAwareFormatter) {
            return new SiblingAwareFormatter(siblingAwareFormatter);
        }

        /**
         * Convert a data value into string.
         * @param value the input value (typically {@code row[column.index]})
         * @return the formatted value (length should be less or equal to
         *         the {@code column.width-column.padding})
         */
        @NotNull @Contract(pure = true)
        protected String format(@Nullable Object value) {
            return formatter.apply(value);
        }

        @Override
        @Contract(pure = true)
        public @NotNull String toString() {
            return index + "-" + name;
        }

        @SuppressWarnings("unchecked")
        @Contract(pure = true)
        private static @NotNull String defaultFormatter(@Nullable Object value) {
            if (value instanceof Callable) return defaultFormatter(Exceptions.rethrow((Callable) value));
            if (value instanceof Supplier) return defaultFormatter(((Supplier) value).get());
            if (value instanceof Optional) return defaultFormatter(((Optional) value).orElse(""));
            if (value instanceof Collection) return defaultFormatter(((Collection<?>) value).stream());
            if (value instanceof Object[]) return defaultFormatter(Arrays.stream((Object[]) value));
            if (value instanceof Stream) return ((Stream<?>) value).map(Column::defaultFormatter)
                                                                   .collect(Collectors.joining(eol));
            return String.valueOf(value);
        }
    }

    private static class SiblingAwareFormatter implements Function<@Nullable Object, String> {
        final BiFunction<@Nullable Object, SiblingLookup, String> delegate;
        SiblingLookup lookup;

        public SiblingAwareFormatter(BiFunction<@Nullable Object, SiblingLookup, String> delegate) {
            this.delegate = delegate;
        }

        @Override
        public String apply(@Nullable Object o) {
            return delegate.apply(o, lookup);
        }
    }

    /**
     * Used by renderers to lookup values from the same row, coordinates of te currently rendered cell,
     * as well as column aggregates.
     */
    // as the instance is per column we may also add memoization if it pops in profiling
    public class SiblingLookup {
        /**
         * The name of the column for the cell being rendered.
         */
        public final @NotNull String columnName;

        /**
         * The index of the row for the cell being rendered.
         */
        public int row;

        /**
         * Create new lookup for a column - typically called by {@link TextTable}.
         * We create a new instance for each column, to reduce the mutable state and allow
         * for more efficient caching.
         * @param columnName the current column name.
         */
        protected SiblingLookup(@NotNull String columnName) {
            this.columnName = columnName;
        }

        /**
         * Lookup aggregate value from the formatted values in a column.
         * @param columnName the column to look up.
         * @return a stream of the column's formatted values.
         */
        public @NotNull Stream<String> column(@NotNull String columnName) {
            Column column = columnByName(columnName);
            int originalRow = row;
            try {
                String[] c = new String[data.size()];
                for (row = 0; row < c.length; row++) {
                    Object cell = data.get(row).get(column.index);
                    c[row] = column.format(cell);
                }
                return Arrays.stream(c);
            } finally {
                row = originalRow;
            }
        }

        /**
         * Lookup aggregate value from the raw values in a column.
         * @param columnName the column to look up.
         * @return a stream of the column's raw values.
         */
        public @NotNull Stream<Object> columnRaw(@NotNull String columnName) {
            Column column = columnByName(columnName);
            return data.stream().map(r -> r.get(column.index));
        }

        /**
         * Lookup formatted value from another column on the same row as the currently rendered
         * cell.
         *
         * @param columnName the column to look up.
         * @return formatted value
         */
        public @NotNull String sibling(@NotNull String columnName) {
            Column column = columnByName(columnName);
            Object value = data.get(row).get(column.index);
            if (column.formatter instanceof SiblingAwareFormatter) {
                SiblingAwareFormatter saf = (SiblingAwareFormatter) column.formatter;
                saf.lookup.row = row;
            }
            return column.format(value);
        }

        /**
         * Lookup raw value from another column on the same row as the currently rendered
         * cell.
         *
         * @param columnName the column to look up.
         * @return raw value
         */
        public @Nullable Object siblingRaw(@NotNull String columnName) {
            Column column = columnByName(columnName);
            return data.get(row).get(column.index);
        }

        @NotNull
        private TextTable.@NotNull Column columnByName(@NotNull String columnName) {
            //noinspection ForLoopReplaceableByForEach - we want to save on the ArrayList iterator
            for (int i = 0; i < columns.size(); i++) {
                Column c = columns.get(i);
                if (Objects.equals(columnName, c.name)) return c;
            }
            throw new NoSuchElementException("Can't find a column named '" + columnName + "'");
        }

    }

    /**
     * <p>Provides a nice and compact DSL to build a set of columns for a {@link TextTable table}.</p>
     *
     * <p>A known limitation is that layouts built with this builder will be
     * isomorphic with the data rows (i.e. you can't have a 3 column layout,
     * displaying 1st, 15th and 8th value of the data row). While technically
     * achievable, I don't see the point, so if you need it feel free to extend
     * this builder or write your own.</p>
     */
    public static class LayoutBuilder {
        private final @NotNull Map<String, Column> columns = new LinkedHashMap<>();

        /**
         * Adds a column to the right, with the desired name and default attributes,
         * displaying the next data index.
         * @param columnName the name of the new column
         * @return reference to {@code this} for chaining.
         * @see #column(String, Consumer)
         */
        @NotNull
        public LayoutBuilder column(@NotNull String columnName) {
            return column(columnName, null);
        }

        /**
         * Adds a column to the right, with the desired name and configurable attributes,
         * displaying the next data index.
         * @param columnName the name of the new column
         * @param config a function that can configure the column. If {@code null} or missing,
         *               the default attributes will be used.
         * @return reference to {@code this} for chaining.
         * @see #column(String)
         */
        @NotNull
        public LayoutBuilder column(@NotNull String columnName, @Nullable Consumer<Column> config) {
            String name = columnName.trim();

            if (columns.containsKey(name)) {
                throw new IllegalArgumentException("Duplicate column '" + name + "'");
            }

            Column newColumn = new Column(name, columns.size());
            columns.put(name, newColumn);

            if (config != null) {
                config.accept(newColumn);
            }

            return this;
        }

        /**
         * Direct method to access all columns, can be used as lower level API to add and remove
         * configured columns or to inspect and change their configuration.
         * @return a map from column name to a column, ordered by their displayed order.
         */
        @NotNull @Contract(pure = true)
        public Map<String, Column> getAllColumns() {
            return columns;
        }

        /**
         * <p>Create a strict data builder with the specified layout, that is
         * used to build the data rows for the table. Each row should specify
         * non-null values for all non-defaulted columns, and no extra data.</p>
         *
         * <p>The layout of the data builder is decoupled from this builder.
         * This builder is still valid after creating a layout and can be used
         * to create multiple layout objects.</p>
         * @return the data builder.
         * @see DataBuilder#buildTable()
         */
        @NotNull @Contract(pure = true)
        public DataBuilder withData() {
            return new DataBuilder(true, new ArrayList<>(columns.values()));
        }

        /**
         * <p>Create a lenient data builder with the specified layout, that is
         * used to build the data rows for the table. Each row should specify
         * non-null values for all non-defaulted columns, but it may as well
         * specify extra data.</p>
         *
         * <p>The layout of the data builder is decoupled from this builder.
         * This builder is still valid after creating a layout and can be used
         * to create multiple layout objects.</p>
         *
         * @return the data builder.
         * @see DataBuilder#buildTable()
         */
        @NotNull @Contract(pure = true)
        public DataBuilder withDataRelaxed() {
            return new DataBuilder(false, new ArrayList<>(columns.values()));
        }

        @Override
        @NotNull @Contract(pure = true)
        public String toString() {
            return String.format("TextTable.LayoutBuilder{columns=%s}", columns);
        }
    }

    /**
     * This builder can be used to build a dataset, that can be used
     * together with the specified layout (a set of columns) to create
     * a {@link TextTable table}.
     */
    public static class DataBuilder {
        private final int maxColumnIndex;
        private final boolean strict;
        private final @NotNull List<@NotNull Column> columns;
        private final @NotNull List<@NotNull List<?>> data = new ArrayList<>();
        private final @NotNull Map<@NotNull Integer, @NotNull Separator> separatorBefore = new TreeMap<>();

        /**
         * Creates a builder for the specified layout.
         * @param strict if {@code true}, rows can not specify extra data (unmapped to columns)
         * @param columns the layout for hte table (defensively copied internally).
         */
        public DataBuilder(boolean strict, @NotNull List<@NotNull Column> columns) {
            // cloning in the LayoutBuilder.withData() as well, but better safe than sorry
            this.columns = new ArrayList<>(columns);
            this.strict = strict;
            this.maxColumnIndex = columns.stream()
                                         .mapToInt(it -> it.index).max()
                                         .orElseThrow(()-> new IllegalArgumentException("Empty layout"));
        }

        /**
         * Adds a single row to the dataset
         * @param row the row of values to add
         * @return reference to {@code this} for chaining.
         * @throws IllegalArgumentException if the row does not match in the layout
         */
        @NotNull
        public DataBuilder row(@NotNull List<@Nullable ?> row) {
            return appendRowChecked(row, row);
        }

        /**
         * Adds a single row to the dataset
         * @param row the row of values to add
         * @return reference to {@code this} for chaining.
         * @throws IllegalArgumentException if the row does not match in the layout
         */
        @NotNull
        public DataBuilder row(Object... row) { // we want not null row array, but nullable array components
            return appendRowChecked(Arrays.asList(row), Arrays.deepToString(row));
        }

        /**
         * Adds a single row to the dataset
         * @param row the row of values to add keyed by name
         * @return reference to {@code this} for chaining.
         * @throws IllegalArgumentException if the row does not match in the layout
         */
        @NotNull
        public DataBuilder row(@NotNull Map<@NotNull String, @Nullable Object> row) {
            Object[] rowArray = new Object[maxColumnIndex+1];
            for (Column column : columns) {
                if (!row.containsKey(column.name) && column.defaultValue == null) continue;
                rowArray[column.index] = row.getOrDefault(column.name, column.defaultValue);
            }
            return appendRowChecked(Arrays.asList(rowArray), row + "->" + Arrays.toString(rowArray));
        }

        /**
         * Adds multiple rows to the dataset
         * @param rows a collection of rows to add
         * @return reference to {@code this} for chaining.
         * @throws IllegalArgumentException if the row does not match in the layout
         */
        @NotNull
        public DataBuilder rows(@NotNull Collection<@NotNull List<@Nullable ?>> rows) {
            rows.forEach(this::row);
            return this;
        }

        /**
         * Adds multiple rows to the dataset
         * @param rows a collection of rows to add
         * @return reference to {@code this} for chaining.
         * @throws IllegalArgumentException if the row does not match in the layout
         */
        @NotNull
        public DataBuilder rows(@NotNull Object[]... rows) {
            for (Object[] row : rows) row(row);
            return this;
        }

        /**
         * Add a plain row separator (use table style, no title)
         * @return reference to {@code this} for chaining.
         */
        public @NotNull DataBuilder separator() { return separator(null, null); }

        /**
         * Add a row separator with optional title using the table inner joints style
         * @param name the separator title - it will be left-aligned and padded by default
         * @return reference to {@code this} for chaining.
         */
        public @NotNull DataBuilder separator(@Nullable String name) { return separator(name, null); }

        /**
         * Add a row separator with no title and optional config closure
         * @param config a closure to configure a {@code Separator} object,
         *               if {@code null} - use the table inner joints style
         * @return reference to {@code this} for chaining.
         */
        public @NotNull DataBuilder separator(@Nullable Consumer<Separator> config) { return separator(null, config); }

        /**
         * Add a row separator with optional title and config closure
         * @param name the separator title - it will be aligned and padded according to te config
         * @param config a closure to configure a {@code Separator} object,
         *               if {@code null} - use the table inner joints style
         * @return reference to {@code this} for chaining.
         */
        public @NotNull DataBuilder separator(@Nullable String name, @Nullable Consumer<Separator> config) {
            return separator(Separator.of(name, null, null, config));
        }

        /**
         * Add a prepared row separator
         * @param separator the configured separator instance. The separator is not copied -
         *                  take care if you modify after adding.
         * @return reference to {@code this} for chaining.
         */
        public @NotNull DataBuilder separator(Separator separator) {
            separatorBefore.put(data.size(), separator);
            return this;
        }

        private DataBuilder appendRowChecked(List<?> row, @Nullable Object rowDescription) {
            if (strict) {
                if (row.size() != maxColumnIndex + 1) {
                    throw new IllegalArgumentException(String.format(
                            "Data doesn't match the columns:%n%d COLUMNS: %s%n%d DATA: %s",
                            columns.size(), columns, row.size(), rowDescription
                    ));
                }
            }
            for (Column column : columns) {
                if (column.defaultValue != null) continue;

                if (column.index>=row.size()) {
                    throw new IllegalArgumentException(String.format(
                            "Column index out of range: %s%n%d COLUMNS: %s%n%d DATA: %s",
                            column, columns.size(), columns, row.size(), rowDescription
                    ));
                }

                if (row.get(column.index)==null) {
                    throw new IllegalArgumentException(String.format(
                            "Non default column is null: %s%n%d COLUMNS: %s%n%d DATA: %s",
                            column, columns.size(), columns, row.size(), rowDescription
                    ));
                }
            }
            data.add(row);
            return this;
        }

        /**
         * Create a text table with the specified layout and data, that can be used to render
         * the data in tabular format to any appendable (stream or buffer). Both the columns
         * and data are defensively shallow-copied, which means that changes to the columns
         * and rows will visible in the table.
         * @return the text table.
         * @see TextTable#format(int, Appendable)
         */
        @NotNull @Contract(pure = true)
        public TextTable buildTable() {
            return new TextTable(new ArrayList<>(columns), new ArrayList<>(data), new TreeMap<>(separatorBefore));
        }

        @Override
        public String toString() {
            return String.format(
                    "DataBuilder(%d columns, %d rows) { %s }",
                    columns.size(), data.size(), columns.stream().map(it -> it.name).collect(Collectors.joining(", "))
            );
        }
    }

    /**
     * <p>Adds {@code length} characters of padding to the {@code out} parameter.
     * The padding is filled by repeating the {@code pad} pattern as needed,
     * trimming the last occurrence to size.</p>
     * <p>For ecample, {@code pad(out, 8, "abc")} will result in <em>"abcabcab"</em>
     * (8 characters) appened to the {@code out} parameter.</p>
     * @param out where to put the padding.
     * @param length how many characters to pad.
     * @param pad what pattern to use for padding.
     * @param <T> captures the type of the {@code out} parameter, so we can return it.
     * @return the {@code out} parameter for chaining.
     */
    protected static <T extends Appendable> T pad(@NotNull T out, int length, @NotNull String pad) {
        try {
            int padLength = pad.length();
            for (int i = 0; i < length; /* incremented in loop */) {
                int charsToAppend = Math.min(padLength, length - i);
                out.append(pad, 0, charsToAppend);
                i += charsToAppend;
            }
            return out;
        } catch (IOException e) {
            return Exceptions.rethrow(e);
        }
    }
}
