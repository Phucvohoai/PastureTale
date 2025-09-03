package model;

public class Farm {
    private int playerId;
    private int size; // Có thể không dùng, để 0 nếu không cần
    private String land;
    private String animals;

    public Farm(int playerId, int size, String land, String animals) {
        this.playerId = playerId;
        this.size = size;
        this.land = land;
        this.animals = animals;
    }

    // Getters và setters
    public int getPlayerId() {
        return playerId;
    }

    public String getLand() {
        return land;
    }

    public void setLand(String land) {
        this.land = land;
    }

    public String getAnimals() {
        return animals;
    }

    public void setAnimals(String animals) {
        this.animals = animals;
    }
}