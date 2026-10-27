package edu.campustorrent.experiment;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/** Minimal RFC-4180 style CSV writer. Owner: Sambit Kumar Mohapatra. */
public final class CsvWriter implements Closeable {
    private final BufferedWriter out;

    /** Opens the file; writes the header unless appending to a non-empty file. */
    public CsvWriter(Path file, List<String> header, boolean append) throws IOException {
        if (file.toAbsolutePath().getParent() != null) Files.createDirectories(file.toAbsolutePath().getParent());
        boolean writeHeader = !(append && Files.exists(file) && Files.size(file) > 0);
        out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        if (writeHeader) writeRow(header);
    }

    public void writeRow(List<String> cells) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        out.write(sb.toString());
        out.write("\n");
        out.flush();
    }

    static String escape(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    @Override
    public void close() throws IOException { out.close(); }
}
