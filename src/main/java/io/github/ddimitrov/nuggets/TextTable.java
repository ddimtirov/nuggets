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
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


public class TextTable {
    public static volatile Style style;
    public static volatile String eol;
    public static volatile String padding;
    public static volatile String indent;

    static {
        presetBox(Box.ASCII);
    }

    public static void presetBox(@NotNull Style style) {
        indent = " ";
        padding = " ";
        eol = System.lineSeparator();
        TextTable.style = style;
    }

    private final @NotNull List<@NotNull Column> columns;
    private final @NotNull List<@NotNull List<?>> data;

    public TextTable(@NotNull List<@NotNull Column> columns, @NotNull List<@NotNull List<?>> data) {
        this.columns = columns;
        this.data = data;
    }

    @Contract(pure = true)
    public static @NotNull LayoutBuilder withColumns(@NotNull String... columnNames) {
        LayoutBuilder layoutBuilder = new LayoutBuilder();
        for (String columnName : columnNames) {
            layoutBuilder.column(columnName);
        }
        return layoutBuilder;
    }

    public <F extends Appendable> @NotNull F format(int indent, @NotNull F out) throws IOException {
        autoformatFromContent();
        return formatAgain(indent, out);
    }

    public <F extends Appendable> @NotNull F formatAgain(int indent, @NotNull F out) throws IOException {
        StringBuilder indentPad = pad(new StringBuilder(indent), indent, TextTable.indent);

        out.append(indentPad);
        appendSeparator(out, style.joints(0));

        out.append(indentPad);
        appendRow(out, null);

        out.append(indentPad);
        appendSeparator(out, style.joints(1));

        for (List<?> row : data) {
            out.append(indentPad);
            appendRow(out, row);
        }

        out.append(indentPad);
        appendSeparator(out, style.joints(2));
        return out;
    }

    private void autoformatFromContent() {
        for (Column column : columns) {
            int maxValueLength = data.stream()
                                     .map(row -> row.get(column.index))
                                     .map((value) -> {
                                        try (Column.Memento ignored = column.new Memento()) {
                                            return column.format(value);
                                        }
                                     })
                                     .map(String::length)
                                     .max(Comparator.naturalOrder())
                                     .orElse(0);

            column.width = Math.max(maxValueLength, column.width);
        }
    }

    private void appendSeparator(@NotNull Appendable out, @NotNull String[] jointRow) throws IOException {
        for (int i = 0; i < columns.size(); i++) {
            Column column = columns.get(i);
            boolean lastColumn = i < columns.size() - 1;

            if (i == 0) out.append(jointRow[0]);
            pad(out, column.padding * 2 + column.width, style.horizontal());
            out.append(jointRow[lastColumn ? 1 : 2]);
        }
        out.append(eol);
    }

    private void appendRow(@NotNull Appendable out, @Nullable List<?> row) throws IOException {
        out.append(style.vertical());
        for (Column column : columns) {
            try (Column.Memento ignored = column.new Memento()) {
                String formatted;
                if (row == null) {
                    formatted = column.name;
                } else {
                    Object rawValue = row.get(column.index);
                    Object defaultedValue = rawValue == null ? column.defaultValue : rawValue;
                    formatted = column.format(defaultedValue);
                }

                if (formatted.length() > column.width) {
                    throw new IllegalStateException(
                            "Formatted string '" + formatted + "'::length==" + formatted.length() +
                                    " > column " + column + "::width==" + column.width);
                }

                if (formatted.isEmpty()) {
                    // in case of multi-char padding string, don't restart in the middle
                    pad(out, column.width + column.padding * 2, padding);
                } else {
                    int alignmentGap = column.width - formatted.length();
                    int leftAlignmentPad = (int) (alignmentGap * column.alignment);
                    int rightAlignmentPad = alignmentGap - leftAlignmentPad;
                    pad(out, leftAlignmentPad + column.padding, padding);
                    out.append(formatted);
                    pad(out, rightAlignmentPad + column.padding, padding);
                }
                out.append(style.vertical());
            }
        }
        out.append(eol);
    }

    public interface Style {
        @Contract(pure = true)
        @NotNull String horizontal();

        @Contract(pure = true)
        @NotNull String vertical();

        @Contract(pure = true)
        @NotNull String[] joints(int row);
    }

    public enum Box implements Style {
        ASCII("-", "|",
                new String[]{"+", "+", "+"},
                new String[]{"+", "+", "+"},
                new String[]{"+", "+", "+"}),
        UNICODE_THIN("\u2500", "\u2502",
                new String[]{"\u250c", "\u252c", "\u2510"},
                new String[]{"\u251c", "\u253c", "\u2524"},
                new String[]{"\u2514", "\u2534", "\u2518"}),
        UNICODE_THICK("\u2501", "\u2503",
                new String[]{"\u250f", "\u2533", "\u2513"},
                new String[]{"\u2523", "\u254b", "\u252b"},
                new String[]{"\u2517", "\u253b", "\u251b"}),
        UNICODE_DOUBLE("\u2550", "\u2551",
                new String[]{"\u2554", "\u2566", "\u2557"},
                new String[]{"\u2560", "\u256c", "\u2563"},
                new String[]{"\u255a", "\u2569", "\u255d"});

        private final @NotNull String horizontal;
        private final @NotNull String vertical;
        private final @NotNull String[][] joints;

        Box(@NotNull String horizontal, @NotNull String vertical, @NotNull String[]... joints) {
            this.horizontal = horizontal;
            this.vertical = vertical;
            this.joints = joints;
        }

        @Override
        @Contract(pure = true)
        public @NotNull String horizontal() {
            return horizontal;
        }

        @Override
        @Contract(pure = true)
        public @NotNull String vertical() {
            return vertical;
        }

        @Override
        @Contract(pure = true) // we are leaking the raw array - not worth creating garbage... please don't modify it!
        public @NotNull String[] joints(int row) {
            return joints[row];
        }
    }

    public static class Column {
        public final @NotNull String name;
        public final int index;
        public int width;
        public int padding = 1;
        public double alignment = 0d;
        public @NotNull Function<@Nullable Object, String> formatter = Column::defaultFormatter;
        public @Nullable Object defaultValue;

        protected class Memento implements AutoCloseable {
            private int width = Column.this.width;
            private int padding = Column.this.padding;
            private double alignment = Column.this.alignment;
            private @NotNull Function<@Nullable Object, String> formatter = Column.this.formatter;
            private @Nullable Object defaultValue = Column.this.defaultValue;

            // this is kinda novel - see how it works out... for starters we need to suppress a compiler warning
            @Override public void close() { restore(); }

            protected void restore() {
                Column.this.width = width;
                Column.this.padding = padding;
                Column.this.alignment = alignment;
                Column.this.formatter = formatter;
                Column.this.defaultValue = defaultValue;
            }
        }

        public Column(@NotNull String name, int index) {
            this.name = name;
            this.index = index;
            width = name.length();
        }

        @Contract(pure = true)
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
            // TODO: replace with multi-function in v0.2.0
            if (value instanceof Callable) return Exceptions.rethrow((Callable) value).toString();
            if (value instanceof Supplier) return ((Supplier) value).get().toString();
            return String.valueOf(value);
        }
    }

    public static class LayoutBuilder {
        private final @NotNull Map<String, Column> columns = new LinkedHashMap<>();

        @NotNull
        @Contract(pure = true)
        public DataBuilder withData() {
            return new DataBuilder(new ArrayList<>(columns.values()));
        }

        @NotNull
        public LayoutBuilder column(@NotNull String columnName) {
            return column(columnName, null);
        }

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

        @NotNull
        @Contract(pure = true)
        public Map<String, Column> getAllColumns() {
            return columns;
        }

        @Override
        @NotNull
        @Contract(pure = true)
        public String toString() {
            return String.format("TextTable.LayoutBuilder{columns=%s}", columns);
        }
    }

    public static class DataBuilder {
        private final @NotNull List<@NotNull Column> columns;
        private final @NotNull List<@NotNull List<?>> data = new ArrayList<>();

        public DataBuilder(@NotNull List<@NotNull Column> columns) {
            this.columns = columns;
        }

        @NotNull
        public DataBuilder row(@NotNull Map<@NotNull String, @Nullable Object> row) {
            List<Object> rowList = new ArrayList<>();
            for (Column column : columns) {
                if (!row.containsKey(column.name) && column.defaultValue == null) continue;
                rowList.add(row.getOrDefault(column.name, column.defaultValue));
            }
            checkRowSize(rowList.size(), row + "->" + rowList);
            data.add(rowList);
            return this;
        }

        private void checkRowSize(int rowSize, @Nullable Object rowDescription) {
            if (rowSize != columns.size()) {
                throw new IllegalArgumentException(String.format(
                        "Data doesn't match the columns: %n%d COLUMNS: %s%n%d DATA: %s",
                        columns.size(), columns, rowSize, rowDescription
                ));
            }
        }

        @NotNull
        public DataBuilder row(@NotNull List<?> row) {
            checkRowSize(row.size(), row);
            data.add(row);
            return this;
        }

        @NotNull
        public DataBuilder rows(@NotNull Collection<@NotNull List<?>> rows) {
            rows.forEach(this::row);
            return this;
        }

        @NotNull
        public DataBuilder rows(@NotNull Object[]... rows) {
            for (Object[] row : rows) row(row);
            return this;
        }

        @NotNull
        public DataBuilder row(@NotNull Object... row) {
            checkRowSize(row.length, Arrays.deepToString(row));
            data.add(Arrays.asList(row));
            return this;
        }

        @NotNull
        @Contract(pure = true)
        public TextTable buildTable() {
            return new TextTable(new ArrayList<>(columns), data);
        }
    }

    static <T extends Appendable> T pad(@NotNull T out, int length, @NotNull String pad) {
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
