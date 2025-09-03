package model;

public class Machine {
    private int id;
    private int playerId;
    private String machineType;    // Loại Máy: "Máy Phi Lê Cá", "Lò Bánh Mì", v.v.
    private String status;         // Trạng thái: "đang hoạt động", "rảnh", "đang nâng cấp" (có thể kèm mã màu)
    private int level;             // Cấp độ máy (1-5)
    private int productionSlots;   // Số ô sản xuất (3-8)

    // Constructor với trạng thái mặc định
    public Machine(int id, int playerId, String machineType, String status) {
        this.id = id;
        this.playerId = playerId;
        this.machineType = machineType;
        this.status = status;
        this.level = 1;            // Mặc định Level 1
        this.productionSlots = 3;  // Mặc định 3 ô sản xuất
    }

    // Constructor đầy đủ (nếu cần load từ database với level và slots cụ thể)
    public Machine(int id, int playerId, String machineType, String status, int level, int productionSlots) {
        this.id = id;
        this.playerId = playerId;
        this.machineType = machineType;
        this.status = (status != null) ? status : "rảnh"; // Giá trị mặc định
        this.level = level;
        this.productionSlots = productionSlots;
    }

    // Getters và Setters
    public int getId() {
        return id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getMachineType() {
        return machineType;
    }

    public void setMachineType(String machineType) {
        this.machineType = machineType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
        this.productionSlots = 3 + (level - 1) * 2; // Cập nhật số ô sản xuất: 3 + (level-1)*2
    }

    public int getProductionSlots() {
        return productionSlots;
    }

    public void setProductionSlots(int productionSlots) {
        this.productionSlots = productionSlots;
    }

    @Override
    public String toString() {
        return "Machine [Type: " + machineType + ", Status: " + status + ", Level: " + level + ", Slots: " + productionSlots + "]";
    }
}