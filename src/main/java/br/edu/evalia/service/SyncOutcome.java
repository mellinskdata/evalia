package br.edu.evalia.service;

import br.edu.evalia.model.SourcePlatform;

public record SyncOutcome(SourcePlatform platform, String username, int downloaded, int added) { }
