package io.github.blackshadowhrd.gameloot.service;

import java.io.IOException;
import java.io.Writer;
import java.util.List;

public final class CsvWriter {
    private CsvWriter() { }

    public static void writeRow(Writer writer, List<String> fields) throws IOException {
        for (int index = 0; index < fields.size(); index++) {
            if (index > 0) writer.write(',');
            writer.write(escape(fields.get(index)));
        }
        writer.write("\r\n");
    }

    static String escape(String value) {
        if (value == null) return "";
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\r') < 0 && value.indexOf('\n') < 0) return value;
        return '"' + value.replace("\"", "\"\"") + '"';
    }
}
