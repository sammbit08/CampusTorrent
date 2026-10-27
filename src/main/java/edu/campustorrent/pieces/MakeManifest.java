package edu.campustorrent.pieces;

import edu.campustorrent.util.Args;

import java.nio.file.Path;

/** CLI: creates a manifest for a file. Usage: --file F --piece-size-kb 64 --out F.manifest. Owner: Priyanshu Nayak. */
public final class MakeManifest {
    private MakeManifest() { }

    public static void main(String[] argv) throws Exception {
        try {
            Args a = new Args(argv);
            Path file = Path.of(a.require("file"));
            int pieceSize = a.getInt("piece-size-kb", 64) * 1024;
            Path out = Path.of(a.get("out", file.toString() + ".manifest"));
            Manifest m = FileSplitter.createManifest(file, pieceSize);
            m.save(out);
            System.out.println("Manifest written to " + out);
            System.out.println("  name       = " + m.fileName());
            System.out.println("  size       = " + m.totalSize() + " bytes");
            System.out.println("  pieceSize  = " + m.pieceSize() + " bytes");
            System.out.println("  pieceCount = " + m.pieceCount());
            System.out.println("  fileId     = " + m.fileId());
        } catch (IllegalArgumentException e) {
            System.err.println("Error: " + e.getMessage());
            System.err.println("Usage: MakeManifest --file <path> [--piece-size-kb 64] [--out <manifest>]");
            System.exit(2);
        }
    }
}
