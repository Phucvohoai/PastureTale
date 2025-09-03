package model;

public class Task {
    private int id;
    private int playerId;
    private String taskName; // Tên nhiệm vụ: "Make 3 breads", "Harvest 10 rice"
    private String status;   // Trạng thái: "completed", "incomplete"
    private int reward;      // Phần thưởng (xu hoặc vật phẩm)

    public Task(int id, int playerId, String taskName, String status, int reward) {
        this.id = id;
        this.playerId = playerId;
        this.taskName = taskName;
        this.status = status;
        this.reward = reward;
    }

    // Getters và setters
    public int getId() {
        return id;
    }

    public int getPlayerId() {
        return playerId;
    }

    public String getTaskName() {
        return taskName;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public int getReward() {
        return reward;
    }

    @Override
    public String toString() {
        return "Task: " + taskName + " [Status: " + status + ", Reward: " + reward + " coins]";
    }
}