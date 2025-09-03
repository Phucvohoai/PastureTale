package model;

public class Inventory {
    private int id;
    private int playerId;
    private String itemName;    // Tên vật phẩm: "rice", "egg", "bread", v.v.
    private int quantity;       // Số lượng vật phẩm
    private Integer durability; // Độ bền (null nếu không áp dụng)
    private Double weight;      // Khối lượng (null nếu không áp dụng)
    private String rarity;      // Phân loại (thường, hiếm, huyền thoại, siêu hiếm; null nếu không áp dụng)

    // Constructor cho vật phẩm không có durability, weight, rarity
    public Inventory(int id, int playerId, String itemName, int quantity) {
        this(id, playerId, itemName, quantity, null, null, null);
    }

    // Constructor cho vật phẩm có weight (dành cho cá)
    public Inventory(int id, int playerId, String itemName, int quantity, Double weight) {
        this(id, playerId, itemName, quantity, null, weight, null);
    }

    // Constructor cho vật phẩm có durability (dành cho công cụ)
    public Inventory(int id, int playerId, String itemName, int quantity, Integer durability) {
        this(id, playerId, itemName, quantity, durability, null, null);
    }

    // Constructor đầy đủ hỗ trợ tất cả các thuộc tính
    public Inventory(int id, int playerId, String itemName, int quantity, Integer durability, Double weight, String rarity) {
        this.id = id;
        this.playerId = playerId;
        this.itemName = itemName;
        this.quantity = quantity;
        this.durability = durability;
        this.weight = weight;
        this.rarity = rarity;
    }

    // Getters và Setters
    public int getId() {
        return id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getItemName() {
        return itemName;
    }

    public int getQuantity() {
        return quantity;
    }

    public Integer getDurability() {
        return durability;
    }

    public Double getWeight() {
        return weight;
    }

    public String getRarity() {
        return rarity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public void setDurability(Integer durability) {
        this.durability = durability;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public void setRarity(String rarity) {
        this.rarity = rarity;
    }

    public void addQuantity(int amount) {
        this.quantity += amount;
    }

    public void removeQuantity(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }

    @Override
    public String toString() {
        return itemName + ": " + quantity +
                (durability != null ? " (Durability: " + durability + ")" : "") +
                (weight != null ? " (" + weight + " kg)" : "") +
                (rarity != null ? " (" + rarity + ")" : "");
    }
}