package db;

import java.sql.*;
import model.Player;
import model.Inventory;
import model.Machine;
import java.util.ArrayList;
import java.util.List;
import model.Task;
import java.util.Map;
import java.util.HashMap;
import model.Farm;
import java.util.Arrays;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
// Thêm các import cần thiết cho serialize/deserialize
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;

public class DatabaseHelper {
    private static final String DB_URL = "jdbc:sqlite:sheepvalley.db";
    private Gson gson = new Gson(); // Thêm Gson để xử lý JSON

    public DatabaseHelper() {
        createTables();
        updateTables(); // Thêm bước cập nhật bảng
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(DB_URL);
    }

    private void createTables() {
        String playersTable = "CREATE TABLE IF NOT EXISTS players (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "username TEXT UNIQUE, " +
                "password TEXT, " +
                "level INTEGER DEFAULT 1, " +
                "coins INTEGER DEFAULT 200, " +
                "exp INTEGER DEFAULT 0, " +
                "inventory_level INTEGER DEFAULT 1)";
        String farmTable = "CREATE TABLE IF NOT EXISTS farm (" +
                "player_id INTEGER PRIMARY KEY, " +
                "land TEXT, " +
                "animals TEXT)";
        String inventoryTable = "CREATE TABLE IF NOT EXISTS inventory (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_id INTEGER, " +
                "item_name TEXT, " +
                "quantity INTEGER, " +
                "durability INTEGER, " +
                "weight DOUBLE, " +
                "rarity TEXT)";
        String machinesTable = "CREATE TABLE IF NOT EXISTS machines (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_id INTEGER, " +
                "machine_type TEXT, " +
                "level INTEGER DEFAULT 1, " +           // Thêm cột level
                "production_slots INTEGER DEFAULT 3, " + // Thêm cột production_slots
                "status TEXT)";
        String machineProductsTable = "CREATE TABLE IF NOT EXISTS machine_products (" +
                "player_id INTEGER, " +
                "machine_type TEXT, " +
                "product_info TEXT, " +
                "PRIMARY KEY (player_id, machine_type))";
        String tasksTable = "CREATE TABLE IF NOT EXISTS tasks (" +
                "id INTEGER PRIMARY KEY, " +
                "player_id INTEGER, " +
                "task_name TEXT, " +
                "status TEXT, " +
                "reward INTEGER)";
        String cropTimersTable = "CREATE TABLE IF NOT EXISTS crop_timers (" +
                "player_id INTEGER, " +
                "crop_key TEXT, " +
                "end_time INTEGER, " +
                "PRIMARY KEY (player_id, crop_key))";
        String animalTimersTable = "CREATE TABLE IF NOT EXISTS animal_timers (" +
                "player_id INTEGER, " +
                "animal_key TEXT, " +
                "end_time INTEGER, " +
                "PRIMARY KEY (player_id, animal_key))";
        String truckOrdersTable = "CREATE TABLE IF NOT EXISTS truck_orders (" +
                "player_id INTEGER, " +
                "order_text TEXT, " +
                "PRIMARY KEY (player_id, order_text))";
        String truckOrderStatusTable = "CREATE TABLE IF NOT EXISTS truck_order_status (" +
                "player_id INTEGER, " +
                "order_text TEXT, " +
                "status TEXT, " +
                "PRIMARY KEY (player_id, order_text))";
        String timersTable = "CREATE TABLE IF NOT EXISTS timers (" +
                "player_id INTEGER, " +
                "type TEXT, " +
                "key TEXT, " +
                "value INTEGER, " +
                "PRIMARY KEY (player_id, type, key))";
        String resetCountTable = "CREATE TABLE IF NOT EXISTS reset_count (" +
                "player_id INTEGER PRIMARY KEY, " +
                "reset_count INTEGER DEFAULT 5, " +
                "last_reset INTEGER DEFAULT 0, " +
                "last_task_reset INTEGER DEFAULT 0, " +
                "last_truck_reset INTEGER DEFAULT 0, " +
                "last_shop_reset INTEGER DEFAULT 0, " +
                "last_random_shop_reset INTEGER DEFAULT 0)";
        String randomShopTable = "CREATE TABLE IF NOT EXISTS random_shop (" +
                "player_id INTEGER PRIMARY KEY, " +
                "items TEXT, " +
                "reset_time INTEGER, " +
                "quantities TEXT)";
        String playerLandTable = "CREATE TABLE IF NOT EXISTS player_land (" +
                "player_id INTEGER PRIMARY KEY, " +
                "total_land_plots INTEGER NOT NULL, " +
                "land_plots BLOB, " +
                "land_timers BLOB)";
        String npcOrdersTable = "CREATE TABLE IF NOT EXISTS npc_orders (" +
                "player_id INTEGER PRIMARY KEY, " +
                "orders TEXT, " +
                "last_reset INTEGER)";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(playersTable);
            stmt.execute(farmTable);
            stmt.execute(inventoryTable);
            stmt.execute(machinesTable);
            stmt.execute(machineProductsTable);
            stmt.execute(tasksTable);
            stmt.execute(cropTimersTable);
            stmt.execute(animalTimersTable);
            stmt.execute(truckOrdersTable);
            stmt.execute(truckOrderStatusTable);
            stmt.execute(timersTable);
            stmt.execute(resetCountTable);
            stmt.execute(randomShopTable);
            stmt.execute(playerLandTable); // Thêm bảng player_land
            stmt.execute(npcOrdersTable);
        } catch (SQLException e) {
            System.out.println("Lỗi khi tạo bảng: " + e.getMessage());
        }
    }

    // Các phương thức khác giữ nguyên
    private void updateTables() {
        String[] alterStatements = {
                "ALTER TABLE reset_count ADD COLUMN last_task_reset INTEGER DEFAULT 0",
                "ALTER TABLE reset_count ADD COLUMN last_truck_reset INTEGER DEFAULT 0",
                "ALTER TABLE reset_count ADD COLUMN last_shop_reset INTEGER DEFAULT 0",
                "ALTER TABLE reset_count ADD COLUMN last_random_shop_reset INTEGER DEFAULT 0",
                "ALTER TABLE players ADD COLUMN inventory_level INTEGER DEFAULT 1",
                "ALTER TABLE machines ADD COLUMN level INTEGER DEFAULT 1",
                "ALTER TABLE machines ADD COLUMN production_slots INTEGER DEFAULT 3",
                // Thêm các cột mới cho bảng npc_orders
                "ALTER TABLE npc_orders ADD COLUMN order_count INTEGER DEFAULT 0",          // Số lượng đơn hàng đã hoàn thành
                "ALTER TABLE npc_orders ADD COLUMN total_coins_earned INTEGER DEFAULT 0"   // Tổng xu kiếm được từ NPC
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : alterStatements) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    if (!e.getMessage().contains("duplicate column name")) {
                        System.out.println("Lỗi khi thêm cột: " + e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi cập nhật bảng: " + e.getMessage());
        }
    }

    public Map<String, Long> loadResetTimes(int playerId) {
        Map<String, Long> resetTimes = new HashMap<>();
        String sql = "SELECT last_task_reset, last_truck_reset, last_shop_reset, last_random_shop_reset FROM reset_count WHERE player_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                resetTimes.put("lastTaskReset", rs.getLong("last_task_reset"));
                resetTimes.put("lastTruckReset", rs.getLong("last_truck_reset"));
                resetTimes.put("lastShopReset", rs.getLong("last_shop_reset"));
                resetTimes.put("lastRandomShopReset", rs.getLong("last_random_shop_reset"));
            } else {
                resetTimes.put("lastTaskReset", 0L);
                resetTimes.put("lastTruckReset", 0L);
                resetTimes.put("lastShopReset", 0L);
                resetTimes.put("lastRandomShopReset", 0L);
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load reset times: " + e.getMessage());
            resetTimes.put("lastTaskReset", 0L);
            resetTimes.put("lastTruckReset", 0L);
            resetTimes.put("lastShopReset", 0L);
            resetTimes.put("lastRandomShopReset", 0L);
        }
        return resetTimes;
    }

    public void saveRandomShop(int playerId, List<String> items, long resetTime, Map<String, Integer> quantities) {
        String sql = "INSERT OR REPLACE INTO random_shop (player_id, items, reset_time, quantities) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setString(2, gson.toJson(items));
            pstmt.setLong(3, resetTime);
            pstmt.setString(4, gson.toJson(quantities));
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu random shop: " + e.getMessage());
        }
    }

    public Map<String, Object> loadRandomShop(int playerId) {
        Map<String, Object> result = new HashMap<>();
        String sql = "SELECT items, reset_time, quantities FROM random_shop WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String itemsJson = rs.getString("items");
                long resetTime = rs.getLong("reset_time");
                String quantitiesJson = rs.getString("quantities");

                List<String> items = gson.fromJson(itemsJson, new TypeToken<List<String>>(){}.getType());
                Map<String, Integer> quantities = gson.fromJson(quantitiesJson, new TypeToken<Map<String, Integer>>(){}.getType());

                result.put("items", items);
                result.put("resetTime", resetTime);
                result.put("quantities", quantities);
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load random shop: " + e.getMessage());
        }
        return result;
    }

    public void saveResetTimes(int playerId, long lastTaskReset, long lastTruckReset, long lastShopReset, long lastRandomShopReset) {
        String sql = "INSERT OR REPLACE INTO reset_count (player_id, reset_count, last_reset, last_task_reset, last_truck_reset, last_shop_reset, last_random_shop_reset) " +
                "VALUES (?, (SELECT reset_count FROM reset_count WHERE player_id = ?), (SELECT last_reset FROM reset_count WHERE player_id = ?), ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setInt(2, playerId);
            pstmt.setInt(3, playerId);
            pstmt.setLong(4, lastTaskReset);
            pstmt.setLong(5, lastTruckReset);
            pstmt.setLong(6, lastShopReset);
            pstmt.setLong(7, lastRandomShopReset);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu reset times: " + e.getMessage());
        }
    }

    public boolean registerPlayer(String username, String password) {
        String sql = "INSERT INTO players (username, password, coins, level, exp, inventory_level) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            pstmt.setInt(3, 200); // Số xu khởi đầu
            pstmt.setInt(4, 1);   // Level khởi đầu
            pstmt.setInt(5, 0);   // Exp khởi đầu
            pstmt.setInt(6, 1);   // Inventory level khởi đầu
            pstmt.executeUpdate();

            ResultSet rs = pstmt.getGeneratedKeys();
            if (rs.next()) {
                int playerId = rs.getInt(1);
                saveInventory(playerId, "Lúa mì", 10, null, null, null);
                saveInventory(playerId, "Cần câu cá", 1, 10, null, null);
                saveMachine(playerId, "Lò Bánh Mì", 1, 3, "rảnh");         // Cập nhật với level=1, productionSlots=3
                saveMachine(playerId, "Máy Cối Xay Gió", 1, 3, "rảnh");    // Cập nhật với level=1, productionSlots=3
                System.out.println("\u001B[33m>> \u001B[36mĐăng ký thành công! Bạn đã được cấp \u001B[33m10 Lúa Mì \u001B[36m, \u001B[33m1 Lò Bánh Mì\u001B[36m, \u001B[33m1 Máy Cối Xay Gió\u001B[36m, \u001B[33m10 mẫu đất\u001B[36m và \u001B[33m1 Cần Câu.\u001B[0m");
                System.out.println("");
                return true;
            }
        } catch (SQLException e) {
            System.out.println("\u001B[31mTên đăng nhập đã tồn tại!\u001B[0m");
            return false;
        }
        return false;
    }

    public Player loginPlayer(String username, String password) {
        String sql = "SELECT * FROM players WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, password);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Player(
                        rs.getInt("id"),
                        rs.getString("username"),
                        rs.getInt("level"),
                        rs.getInt("coins"),
                        rs.getInt("exp")
                );
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi đăng nhập: " + e.getMessage());
        }
        return null;
    }

    // Thêm phương thức loadInventoryLevel
    public int loadInventoryLevel(int playerId) {
        String sql = "SELECT inventory_level FROM players WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("inventory_level");
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi tải inventory level: " + e.getMessage());
        }
        return 1; // Mặc định level 1 nếu không tìm thấy
    }

    // Cập nhật savePlayer để lưu inventoryLevel
    public void savePlayer(int playerId, int coins, int level, int exp, int inventoryLevel) {
        String sql = "UPDATE players SET coins = ?, level = ?, exp = ?, inventory_level = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, coins);
            pstmt.setInt(2, level);
            pstmt.setInt(3, exp);
            pstmt.setInt(4, inventoryLevel);
            pstmt.setInt(5, playerId);
            int rowsAffected = pstmt.executeUpdate();
            if (rowsAffected == 0) {
                throw new SQLException("Không tìm thấy người chơi với ID: " + playerId);
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu dữ liệu người chơi: " + e.getMessage());
        }
    }

    public void saveInventory(int playerId, String itemName, int quantity, Integer durability, Double weight, String rarity) {
        String checkSql = "SELECT quantity FROM inventory WHERE player_id = ? AND item_name = ?";
        String insertSql = "INSERT INTO inventory (player_id, item_name, quantity, durability, weight, rarity) VALUES (?, ?, ?, ?, ?, ?)";
        String updateSql = "UPDATE inventory SET quantity = ?, durability = ?, weight = ?, rarity = ? WHERE player_id = ? AND item_name = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement checkStmt = conn.prepareStatement(checkSql)) {
                checkStmt.setInt(1, playerId);
                checkStmt.setString(2, itemName.toLowerCase());
                ResultSet rs = checkStmt.executeQuery();

                if (rs.next()) {
                    try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                        updateStmt.setInt(1, quantity);
                        updateStmt.setObject(2, durability);
                        updateStmt.setObject(3, weight);
                        updateStmt.setString(4, rarity);
                        updateStmt.setInt(5, playerId);
                        updateStmt.setString(6, itemName.toLowerCase());
                        updateStmt.executeUpdate();
                    }
                } else {
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                        insertStmt.setInt(1, playerId);
                        insertStmt.setString(2, itemName.toLowerCase());
                        insertStmt.setInt(3, quantity);
                        insertStmt.setObject(4, durability);
                        insertStmt.setObject(5, weight);
                        insertStmt.setString(6, rarity);
                        insertStmt.executeUpdate();
                    }
                }
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                System.out.println("Lỗi khi lưu inventory: " + e.getMessage());
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.out.println("Lỗi kết nối cơ sở dữ liệu: " + e.getMessage());
        }
    }

    public List<Inventory> loadInventory(int playerId) {
        List<Inventory> inventory = new ArrayList<>();
        String sql = "SELECT id, item_name, quantity, durability, weight, rarity FROM inventory WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                Integer durability = rs.getObject("durability") != null ? rs.getInt("durability") : null;
                Double weight = rs.getObject("weight") != null ? rs.getDouble("weight") : null;
                String rarity = rs.getString("rarity");
                inventory.add(new Inventory(rs.getInt("id"), playerId, rs.getString("item_name"),
                        rs.getInt("quantity"), durability, weight, rarity));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load inventory: " + e.getMessage());
        }
        return inventory;
    }

    public boolean isNewPlayer(int playerId) {
        String sql = "SELECT COUNT(*) FROM inventory WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1) == 0;
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi kiểm tra người chơi mới: " + e.getMessage());
        }
        return true;
    }

    public void saveFarm(int playerId, String land, String animals) {
        String sql = "INSERT OR REPLACE INTO farm (player_id, land, animals) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setString(2, land);
            pstmt.setString(3, animals);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu farm: " + e.getMessage());
        }
    }

    public Farm loadFarm(int playerId) {
        String sql = "SELECT land, animals FROM farm WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return new Farm(playerId, 0, rs.getString("land"), rs.getString("animals"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi tải farm: " + e.getMessage());
        }
        return null;
    }

    public void clearPlayerData(int playerId) {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM inventory WHERE player_id = " + playerId);
            stmt.execute("DELETE FROM machines WHERE player_id = " + playerId);
            stmt.execute("DELETE FROM tasks WHERE player_id = " + playerId);
            stmt.execute("DELETE FROM timers WHERE player_id = " + playerId);
            stmt.execute("DELETE FROM farm WHERE player_id = " + playerId);
        } catch (SQLException e) {
            System.out.println("Lỗi khi xóa dữ liệu cũ: " + e.getMessage());
        }
    }

    // Phiên bản cũ của saveMachine (3 tham số)
//    public void saveMachine(int playerId, String machineType, String status) {
//        String sql = "INSERT OR REPLACE INTO machines (id, player_id, machine_type, status) " +
//                "VALUES ((SELECT id FROM machines WHERE player_id = ? AND machine_type = ?), ?, ?, ?)";
//        try (Connection conn = getConnection();
//             PreparedStatement pstmt = conn.prepareStatement(sql)) {
//            pstmt.setInt(1, playerId);       // ? thứ 1: subquery (player_id)
//            pstmt.setString(2, machineType); // ? thứ 2: subquery (machine_type)
//            pstmt.setInt(3, playerId);       // ? thứ 3: player_id
//            pstmt.setString(4, status);      // ? thứ 4: status
//            pstmt.executeUpdate();
//        } catch (SQLException e) {
//            System.out.println("101 Lỗi khi lưu machine: " + e.getMessage());
//        }
//    }

    // Phiên bản mới của saveMachine (5 tham số)
    public void saveMachine(int playerId, String machineType, int level, int productionSlots, String status) {
        String sql = "INSERT OR REPLACE INTO machines (id, player_id, machine_type, level, production_slots, status) " +
                "VALUES ((SELECT id FROM machines WHERE player_id = ? AND machine_type = ?), ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);       // WHERE player_id
            pstmt.setString(2, machineType); // WHERE machine_type
            pstmt.setInt(3, playerId);       // player_id
            pstmt.setString(4, machineType); // machine_type (thêm dòng này)
            pstmt.setInt(5, level);         // level
            pstmt.setInt(6, productionSlots); // production_slots
            pstmt.setString(7, status);      // status
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("102 Lỗi khi lưu machine: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public List<Machine> loadMachines(int playerId) {
        List<Machine> machines = new ArrayList<>();
        String sql = "SELECT id, machine_type, level, production_slots, status FROM machines WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                String status = rs.getString("status");
                machines.add(new Machine(
                        rs.getInt("id"),
                        playerId,
                        rs.getString("machine_type"),
                        status,
                        rs.getInt("level"),
                        rs.getInt("production_slots")
                ));
            }
        } catch (SQLException e) {
            System.out.println("103 Lỗi khi load machines: " + e.getMessage());
        }
        return machines;
    }

    public void saveMachineProducts(int playerId, Map<String, String> machineProducts) {
        String sql = "INSERT OR REPLACE INTO machine_products (player_id, machine_type, product_info) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, String> entry : machineProducts.entrySet()) {
                pstmt.setInt(1, playerId);
                pstmt.setString(2, entry.getKey());
                pstmt.setString(3, entry.getValue());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("104 Lỗi khi lưu machine_products: " + e.getMessage());
        }
    }

    public Map<String, String> loadMachineProducts(int playerId) {
        Map<String, String> machineProducts = new HashMap<>();
        String sql = "SELECT machine_type, product_info FROM machine_products WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                machineProducts.put(rs.getString("machine_type"), rs.getString("product_info"));
            }
        } catch (SQLException e) {
            System.out.println("105 Lỗi khi load machine_products: " + e.getMessage());
        }
        return machineProducts;
    }

    public boolean updatePlayerUsername(int playerId, String newUsername) {
        String sql = "UPDATE players SET username = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newUsername);
            pstmt.setInt(2, playerId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE constraint failed")) {
                System.out.println("\u001B[31mTên '" + newUsername + "' đã tồn tại!\u001B[0m");
            } else {
                System.out.println("Lỗi khi đổi tên: " + e.getMessage());
            }
            return false;
        }
    }

    public void updatePlayerPassword(int playerId, String newPassword) {
        String sql = "UPDATE players SET password = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newPassword);
            pstmt.setInt(2, playerId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi đổi mật khẩu: " + e.getMessage());
        }
    }

    public void saveTasks(int playerId, List<Task> tasks) {
        String sql = "INSERT OR REPLACE INTO tasks (id, player_id, task_name, status, reward) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Task task : tasks) {
                pstmt.setInt(1, task.getId());
                pstmt.setInt(2, playerId);
                pstmt.setString(3, task.getTaskName());
                pstmt.setString(4, task.getStatus());
                pstmt.setInt(5, task.getReward());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu tasks: " + e.getMessage());
        }
    }

    public List<Task> loadTasks(int playerId) {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT id, task_name, status, reward FROM tasks WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                tasks.add(new Task(rs.getInt("id"), playerId, rs.getString("task_name"), rs.getString("status"), rs.getInt("reward")));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load tasks: " + e.getMessage());
        }
        return tasks;
    }

    public void saveCropTimers(int playerId, String type, Map<String, Long> timers) {
        String deleteSql = "DELETE FROM timers WHERE player_id = ? AND type = ?";
        String insertSql = "INSERT OR REPLACE INTO timers (player_id, type, key, value) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement deleteStmt = conn.prepareStatement(deleteSql)) {
                deleteStmt.setInt(1, playerId);
                deleteStmt.setString(2, type);
                deleteStmt.executeUpdate();
            }
            try (PreparedStatement insertStmt = conn.prepareStatement(insertSql)) {
                for (Map.Entry<String, Long> entry : timers.entrySet()) {
                    insertStmt.setInt(1, playerId);
                    insertStmt.setString(2, type);
                    insertStmt.setString(3, entry.getKey());
                    insertStmt.setLong(4, entry.getValue());
                    insertStmt.executeUpdate();
                }
            }
            conn.commit();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu timers: " + e.getMessage());
        }
    }

    public Map<String, Long> loadCropTimers(int playerId, String type) {
        Map<String, Long> timers = new HashMap<>();
        String sql = "SELECT key, value FROM timers WHERE player_id = ? AND type = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setString(2, type);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                timers.put(rs.getString("key"), rs.getLong("value"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load timers: " + e.getMessage());
        }
        return timers;
    }

    public void saveAnimalTimers(int playerId, Map<String, Long> animalTimers) {
        String sql = "INSERT OR REPLACE INTO animal_timers (player_id, animal_key, end_time) VALUES (?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (Map.Entry<String, Long> entry : animalTimers.entrySet()) {
                pstmt.setInt(1, playerId);
                pstmt.setString(2, entry.getKey());
                pstmt.setLong(3, entry.getValue());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu animal_timers: " + e.getMessage());
        }
    }

    public Map<String, Long> loadAnimalTimers(int playerId) {
        Map<String, Long> animalTimers = new HashMap<>();
        String sql = "SELECT animal_key, end_time FROM animal_timers WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                animalTimers.put(rs.getString("animal_key"), rs.getLong("end_time"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load animal_timers: " + e.getMessage());
        }
        return animalTimers;
    }

    public void saveTruckOrders(int playerId, List<String> truckOrders) {
        String sql = "INSERT OR REPLACE INTO truck_orders (player_id, order_text) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            for (String order : truckOrders) {
                pstmt.setInt(1, playerId);
                pstmt.setString(2, order);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu truck_orders: " + e.getMessage());
        }
    }

    public List<String> loadTruckOrders(int playerId) {
        List<String> truckOrders = new ArrayList<>();
        String sql = "SELECT order_text FROM truck_orders WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                truckOrders.add(rs.getString("order_text"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load truck_orders: " + e.getMessage());
        }
        return truckOrders;
    }

    public void clearTruckOrders(int playerId) {
        String sql = "DELETE FROM truck_orders WHERE player_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi xóa truck_orders: " + e.getMessage());
        }
    }

    public void deletePlayer(int playerId) {
        try (Connection conn = getConnection()) {
            String deleteInventory = "DELETE FROM inventory WHERE player_id = ?";
            String deleteMachines = "DELETE FROM machines WHERE player_id = ?";
            String deleteFarm = "DELETE FROM farm WHERE player_id = ?";
            String deletePlayer = "DELETE FROM players WHERE id = ?";

            PreparedStatement stmt;

            stmt = conn.prepareStatement(deleteInventory);
            stmt.setInt(1, playerId);
            stmt.executeUpdate();

            stmt = conn.prepareStatement(deleteMachines);
            stmt.setInt(1, playerId);
            stmt.executeUpdate();

            stmt = conn.prepareStatement(deleteFarm);
            stmt.setInt(1, playerId);
            stmt.executeUpdate();

            stmt = conn.prepareStatement(deletePlayer);
            stmt.setInt(1, playerId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi xóa tài khoản: " + e.getMessage());
        }
    }

    public void saveTruckOrderStatus(int playerId, Map<String, String> truckOrderStatus) {
        String sql = "INSERT OR REPLACE INTO truck_order_status (player_id, order_text, status) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (Map.Entry<String, String> entry : truckOrderStatus.entrySet()) {
                pstmt.setInt(1, playerId);
                pstmt.setString(2, entry.getKey());
                pstmt.setString(3, entry.getValue());
                pstmt.addBatch();
            }
            pstmt.executeBatch();
            conn.commit();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu truck_order_status: " + e.getMessage());
        }
    }

    public Map<String, String> loadTruckOrderStatus(int playerId) {
        Map<String, String> truckOrderStatus = new HashMap<>();
        String sql = "SELECT order_text, status FROM truck_order_status WHERE player_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                truckOrderStatus.put(rs.getString("order_text"), rs.getString("status"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load truck_order_status: " + e.getMessage());
        }
        return truckOrderStatus;
    }

    public void saveResetCount(int playerId, int resetCount, long lastResetCountReset) {
        String sql = "INSERT OR REPLACE INTO reset_count (player_id, reset_count, last_reset) VALUES (?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            pstmt.setInt(2, resetCount);
            pstmt.setLong(3, lastResetCountReset);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu reset_count: " + e.getMessage());
        }
    }

    public Map<String, Long> loadResetCount(int playerId) {
        Map<String, Long> resetData = new HashMap<>();
        String sql = "SELECT reset_count, last_reset FROM reset_count WHERE player_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                resetData.put("count", (long) rs.getInt("reset_count"));
                resetData.put("lastReset", rs.getLong("last_reset"));
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi load reset_count: " + e.getMessage());
        }
        return resetData;
    }

    public Map<String, Object> loadLandData(int playerId) {
        Map<String, Object> landData = new HashMap<>();
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT total_land_plots, land_plots, land_timers FROM player_land WHERE player_id = ?")) {
            stmt.setInt(1, playerId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                landData.put("totalLandPlots", rs.getInt("total_land_plots"));
                try {
                    landData.put("landPlots", (Map<Integer, String>) deserialize(rs.getBytes("land_plots")));
                    landData.put("landTimers", (Map<Integer, Long>) deserialize(rs.getBytes("land_timers")));
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Lỗi khi giải tuần tự hóa dữ liệu đất: " + e.getMessage());
                    e.printStackTrace();
                    // Trả về giá trị mặc định nếu có lỗi
                    landData.put("landPlots", new HashMap<Integer, String>());
                    landData.put("landTimers", new HashMap<Integer, Long>());
                }
            }
        } catch (SQLException e) {
            System.out.println("Lỗi SQL khi tải dữ liệu đất: " + e.getMessage());
            e.printStackTrace();
        }
        return landData;
    }

    public void saveLandData(int playerId, int totalLandPlots, Map<Integer, String> landPlots, Map<Integer, Long> landTimers) {
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "REPLACE INTO player_land (player_id, total_land_plots, land_plots, land_timers) VALUES (?, ?, ?, ?)")) {
            stmt.setInt(1, playerId);
            stmt.setInt(2, totalLandPlots);
            try {
                stmt.setBytes(3, serialize(landPlots));
                stmt.setBytes(4, serialize(landTimers));
            } catch (IOException e) {
                System.out.println("Lỗi khi tuần tự hóa dữ liệu đất: " + e.getMessage());
                e.printStackTrace();
                return; // Thoát nếu có lỗi tuần tự hóa
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi SQL khi lưu dữ liệu đất: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(bos);
        oos.writeObject(obj);
        oos.close();
        return bos.toByteArray();
    }

    private Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        if (data == null) return null;
        ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis);
        Object result = ois.readObject();
        ois.close();
        return result;
    }

    public void saveNPCOrders(int playerId, List<String> npcOrders, long lastReset) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "REPLACE INTO npc_orders (player_id, orders, last_reset) VALUES (?, ?, ?)")) {
            String ordersStr = String.join(";", npcOrders); // Lưu dưới dạng chuỗi phân cách bằng ";"
            pstmt.setInt(1, playerId);
            pstmt.setString(2, ordersStr);
            pstmt.setLong(3, lastReset);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.out.println("Lỗi khi lưu NPC orders: " + e.getMessage());
        }
    }

    public Map<String, Object> loadNPCOrders(int playerId) {
        Map<String, Object> result = new HashMap<>();
        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT orders, last_reset FROM npc_orders WHERE player_id = ?")) {
            pstmt.setInt(1, playerId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                String ordersStr = rs.getString("orders");
                long lastReset = rs.getLong("last_reset");
                List<String> orders = ordersStr.isEmpty() ? new ArrayList<>() : Arrays.asList(ordersStr.split(";"));
                result.put("orders", orders);
                result.put("lastReset", lastReset);
            }
        } catch (SQLException e) {
            System.out.println("Lỗi khi tải NPC orders: " + e.getMessage());
        }
        return result;
    }

}