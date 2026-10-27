package edu.campustorrent.experiment;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Owner: Sambit Kumar Mohapatra. */
class ExperimentSupportTest {

    @Test
    void csvEscapesCommasQuotesAndNewlines() {
        assertEquals("plain", CsvWriter.escape("plain"));
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"));
        assertEquals("", CsvWriter.escape(null));
    }

    @Test
    void csvWritesHeaderOnceEvenWhenAppending(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("r.csv");
        try (CsvWriter w = new CsvWriter(f, List.of("a", "b"), false)) { w.writeRow(List.of("1", "2")); }
        try (CsvWriter w = new CsvWriter(f, List.of("a", "b"), true)) { w.writeRow(List.of("3", "4")); }
        assertEquals(List.of("a,b", "1,2", "3,4"), Files.readAllLines(f));
        try (CsvWriter w = new CsvWriter(f, List.of("a", "b"), false)) { w.writeRow(List.of("9", "9")); }
        assertEquals(List.of("a,b", "9,9"), Files.readAllLines(f), "non-append mode overwrites");
    }

    @Test
    void sampleFileGeneratorIsDeterministicAndExactSize(@TempDir Path dir) throws Exception {
        Path a = dir.resolve("a.bin");
        Path b = dir.resolve("b.bin");
        Path c = dir.resolve("c.bin");
        SampleFileGenerator.generate(a, 100_001, 5);
        SampleFileGenerator.generate(b, 100_001, 5);
        SampleFileGenerator.generate(c, 100_001, 6);
        assertEquals(100_001L, Files.size(a));
        assertEquals(-1L, Files.mismatch(a, b));
        assertTrue(Files.mismatch(a, c) >= 0);
        assertThrows(IllegalArgumentException.class, () -> SampleFileGenerator.generate(dir.resolve("z"), 0, 1));
    }
}
