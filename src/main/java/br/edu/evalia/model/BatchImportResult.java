package br.edu.evalia.model;

import java.util.List;

public record BatchImportResult(long read, long added, long duplicates, long rejected,
                                boolean cancelled, List<String> warnings) {
    public BatchImportResult { warnings = List.copyOf(warnings); }
}
