package model;

public class Player {
    private int id;
    private String username;
    private int level;
    private int coins;
    private int exp;

    public Player(int id, String username, int level, int coins, int exp) {
        this.id = id;
        this.username = username;
        this.level = level;
        this.coins = coins;
        this.exp = exp;
    }

    // Getters và setters
    public int getId() { return id; }
    public String getUsername() { return username; }
    public int getLevel() { return level; }
    public int getCoins() { return coins; }
    public int getExp() { return exp; }
    public void addCoins(int amount) { this.coins += amount; }
    public void addExp(int amount) { this.exp += amount; }
    public void setUsername(String username) { this.username = username; } // Thêm setter cho username
}