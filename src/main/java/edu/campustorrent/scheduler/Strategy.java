package edu.campustorrent.scheduler;

/** Piece selection strategy. Owner: Priyanshu Nayak. */
public enum Strategy {
    RAREST_FIRST, RANDOM, SEQUENTIAL;

    public static Strategy parse(String s) {
        switch (s.toLowerCase().replace('_', '-')) {
            case "rarest-first": case "rarest": return RAREST_FIRST;
            case "random": return RANDOM;
            case "sequential": case "sequence": return SEQUENTIAL;
            default: throw new IllegalArgumentException("Unknown strategy (use rarest-first, random or sequential): " + s);
        }
    }

    public String label() { return name().toLowerCase().replace('_', '-'); }
}
