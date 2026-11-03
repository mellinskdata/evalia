package br.edu.evalia.model;

public final class PlayerProfile {
    private final String username;
    private final String displayName;
    private final int rating;

    public PlayerProfile(String username, String displayName, int rating) {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Usuario obrigatorio.");
        this.username = username.trim();
        this.displayName = displayName == null || displayName.isBlank() ? username.trim() : displayName.trim();
        this.rating = Math.max(0, rating);
    }

    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public int getRating() { return rating; }
}
