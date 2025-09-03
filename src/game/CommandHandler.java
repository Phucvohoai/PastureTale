package game;

import model.Player;
import model.Farm;
import model.Inventory;
import model.Machine;
import model.Task;
import game.SoundEffect;
import game.BackgroundSound;
import db.DatabaseHelper;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Scanner;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Optional;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream; // Thêm import này để dùng Stream
import java.util.stream.Collectors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;

public class CommandHandler {
    private Player currentPlayer;
    private DatabaseHelper db;
    private Farm farm;
    private List<Inventory> inventory;
    private List<Machine> machines;
    private List<Task> tasks;
    private Map<String, Long> cropTimers;      // Thêm khai báo
    private Map<String, Long> animalTimers;    // Thêm khai báo
    private Map<String, Long> machineTimers;   // Thêm khai báo
    private List<String> currentTruckOrders;   // Thêm khai báo
    private Scanner scanner;
    private Random random;
    private long lastResetTime; // Thời gian reset cuối cùng (giây)
    private static final long RESET_INTERVAL = 300; // 5 phút = 300 giây
    private Map<String, Integer> shopItemQuantities;
    private int totalLandPlots = 10; // Tổng số mảnh đất, mặc định 10
    private Map<Integer, String> landPlots = new HashMap<>(); // Lưu trạng thái từng mảnh đất (cây trồng hoặc "trống")
    private Map<Integer, Long> landTimers = new HashMap<>(); // Lưu thời gian hoàn thành của từng mảnh đất
    private int inventoryLevel = 1; // Thêm level kho đồ
    private int maxInventorySlots = 100; // Giới hạn slot mặc định ở level 1
    private List<String> currentNPCOrders = new ArrayList<>(); // Danh sách đơn hàng NPC hiện tại
    private long lastNPCReset = 0; // Thời gian reset NPC cuối cùng
    private static final long NPC_RESET_INTERVAL = 1800; // 1 giờ = 3600 giây
    private static final int MAX_NPC_ORDERS = 5; // Giới hạn 3 NPC

    private static final Map<Integer, Integer> machineUpgradeWoodCosts = new HashMap<>() {{
        put(2, 3);  // Level 1 → 2: 3 ván gỗ
        put(3, 5);  // Level 2 → 3: 5 ván gỗ
        put(4, 7);  // Level 3 → 4: 7 ván gỗ
        put(5, 9);  // Level 4 → 5: 9 ván gỗ
    }};

    private static final Map<Integer, Integer> machineUpgradeNailCosts = new HashMap<>() {{
        put(2, 1);  // Level 1 → 2: 1 đinh
        put(3, 3);  // Level 2 → 3: 3 đinh
        put(4, 5);  // Level 3 → 4: 5 đinh
        put(5, 7);  // Level 4 → 5: 7 đinh
    }};

    private static final Map<Integer, Integer> machineUpgradeTimes = new HashMap<>() {{
        put(2, 300);  // Level 1 → 2: 5 phút (300 giây)
        put(3, 600);  // Level 2 → 3: 10 phút (600 giây)
        put(4, 900);  // Level 3 → 4: 15 phút (900 giây)
        put(5, 1200); // Level 4 → 5: 20 phút (1200 giây)
    }};

    // Biến để theo dõi thời gian nâng cấp máy
    private Map<String, Long> machineUpgradeTimers = new HashMap<>();

    // Thời gian trồng cây (tính bằng giây)
    private final Map<String, Integer> cropGrowthTimes = new HashMap<>() {{
        put("bắp", 144);      // 2 phút 24 giây (giảm 20%) 144
        put("cà rốt", 480);   // 8 phút
        put("lúa mì", 60);    // 60 giây 60
        put("đậu nành", 960); // 16 phút
        put("mía", 1440);     // 24 phút
        put("bí ngô", 5760);  // 1 giờ 36 phút
        put("ớt", 1440);      // 24 phút
        put("dâu tây", 2400); // 40 phút
        put("cà chua", 1440); // 24 phút
        put("khoai tây", 960); // 16 phút
        put("dưa hấu", 5600); // 1 giờ 33 phút
        put("hành tây", 480); // 8 phút
        put("tỏi", 400);      // 6 phút 40 giây
        put("cải xanh", 560); // 9 phút 20 giây
        put("táo", 3600);     // 1 giờ (mới)
        put("bông", 7200);    // 2 giờ (mới)
        put("cacao", 8640);   // 2 giờ 24 phút (mới)
        put("lá trà", 4800);  // 1 giờ 20 phút (mới)
    }};

    // Thời gian thu hoạch vật nuôi (sau khi cho ăn, tính bằng giây)
    private final Map<String, Integer> animalHarvestTimes = new HashMap<>() {{
        put("gà", 360);    // 6 phút (trứng) 360
        put("bò", 1800);   // 30 phút (sữa) 1800
        put("heo", 3600);  // 1 giờ (thịt)
        put("cừu", 7200);  // 2 giờ (len)
    }};

    // Thời gian chế biến Máy móc (tính bằng giây)
    private final Map<String, Integer> machineProcessingTimes = new HashMap<>() {{
        put("Lò Bánh Mì", 240);      // 4 phút
        put("Nồi Bỏng Ngô", 1440);   // 24 phút
        put("Máy làm Thức ăn", 300); // 5 phút (lấy thời gian lâu nhất trong 4 loại thức ăn)
        put("Nhà Máy Sữa", 3600);    // 1 giờ (lấy thời gian lâu nhất: phô mai)
        put("Nhà Máy Đường", 4800);  // 1 giờ 20 phút (lấy thời gian lâu nhất: sirô)
        put("Máy May", 1800);        // 30 phút (lấy thời gian lâu nhất: vải cotton)
        put("Máy Làm Bánh Ngọt", 900);   // 15 phút (lấy thời gian lâu nhất: bánh dâu)
        put("Máy Làm Phô Mai", 880);     // 14 phút 40 giây
        put("Máy Làm Nước Ép", 1200);    // 20 phút (lấy thời gian lâu nhất: nước ép dâu)
        put("Máy Làm Mứt", 1200);        // 20 phút (lấy thời gian lâu nhất: mứt táo)
        put("Máy Làm Kem Tuyết", 680);   // 11 phút 20 giây
        put("Lò Nướng Bánh", 760);       // 12 phút 40 giây
        put("Máy Làm Sữa Chua", 960);    // 16 phút
        put("Máy Làm Bánh Quy", 560);    // 9 phút 20 giây
        put("Máy Làm Nước Sốt", 800);    // 13 phút 20 giây
        put("Máy Làm Kẹo", 1000);        // 16 phút 40 giây
        put("Máy Làm Bánh Pizza", 1280); // 21 phút 20 giây
        put("Máy Dệt Vải", 1440);        // 24 phút
        put("Máy Làm Socola", 1080);     // 18 phút
        put("Máy Làm Trà", 920);         // 15 phút 20 giây
        put("Máy Cối Xay Gió", 180);     // 3 phút (thêm mới)
        put("Máy Phi Lê Cá", 300); // 5 phút
    }};

    // Thời gian xây Máy móc (tính bằng giây)
    private final Map<String, Integer> machineBuildTimes = new HashMap<>() {{
        put("lò bánh mì", 480);
        put("nồi bỏng ngô", 1440);
        put("máy làm thức ăn", 240);  // Sửa thành chữ thường
        put("nhà máy sữa", 5760);
        put("nhà máy đường", 8640);
        put("máy may", 4800);
        put("máy làm bánh ngọt", 3600);
        put("máy làm phô mai", 4400);
        put("máy làm nước ép", 5200);
        put("máy làm mứt", 5600);
        put("máy làm kem tuyết", 3400);
        put("lò nướng bánh", 3800);
        put("máy làm sữa chua", 4800);
        put("máy làm bánh quy", 2800);
        put("máy làm nước sốt", 4000);
        put("máy làm kẹo", 5000);
        put("máy làm bánh pizza", 6400);
        put("máy dệt vải", 7200);
        put("máy làm socola", 5400);
        put("máy làm trà", 4600);
        put("máy cối xay gió", 100);
        put("máy phi lê cá", 600); // 10 phút để xây 600
    }};

    private List<String> currentShopItems = new ArrayList<>();
    private long lastShopReset = 0;

    // Danh sách nhiệm vụ (random mỗi ngày)
    private final String[] possibleTasks = {
            "Thu hoạch 10 lúa mì:50", "Thu hoạch 5 trứng:30", "Chế biến 3 bánh mì:70",
            "Cho 2 con bò ăn:40", "Thu hoạch 5 len:80", "Thu hoạch 15 bắp:60",
            "Chế biến 5 kem:90", "Cho 3 con gà ăn:25", "Thu hoạch 8 sữa:60",
            "Chế biến 4 đường:80", "Thu hoạch 20 đậu nành:85", "Chế biến 6 bỏng ngô:95",
            "Cho 2 con heo ăn:50", "Thu hoạch 10 thịt:70", "Thu hoạch 25 mía:90",
            "Chế biến 3 bánh pizza:100", "Thu hoạch 12 trứng:40", "Cho 2 con cừu ăn:35",
            "Thu hoạch 15 cà rốt:65", "Chế biến 7 bánh quy:110", "Thu hoạch 12 bí ngô:100",
            "Chế biến 5 sirô:85", "Cho 3 con bò ăn:55", "Thu hoạch 20 táo:60",
            "Chế biến 5 áo len:120", "Chế biến 3 socola:90", "Chế biến 4 trà xanh:75",
            // Thêm nhiệm vụ mới để thay thế
            "Thu hoạch 10 dâu tây:55", "Chế biến 4 mứt dâu:80", "Cho 4 con gà ăn:45",
            "Thu hoạch 15 ớt:70", "Chế biến 6 nước sốt:90", "Thu hoạch 8 lông vũ:60",
            "Câu 3kg cá:40", "Câu 5kg cá:60", "Câu 10kg cá:100"
    };
    private long lastTaskReset = 0;

    // Danh sách cây trồng trong shop chính
    private final String[] cropItems = {
            "Bắp:35", "Cà Rốt:42", "Lúa Mì:21", "Đậu Nành:49",
            "Mía:56", "Bí Ngô:70", "Dâu Tây:84", "Cà Chua:62", "Khoai Tây:42",
            "Ớt:77", "Dưa Hấu:98", "Hành Tây:35", "Tỏi:28", "Cải Xanh:49",
            "Phân Bón:28", "Táo:62", "Bông:98", "Cacao:112", "Lá Trà:70"
    };

    // Danh sách động vật trong shop chính
    private final String[] animalItems = {
            "Gà:100", "Bò:350", "Heo:480", "Cừu:620",
            "Thức Ăn Cho Gà:21", "Thức Ăn Cho Bò:28", "Thức Ăn Cho Lợn:35", "Thức Ăn Cho Cừu:42" // Cập nhật
    };

    // Danh sách Máy móc trong shop chính (sẽ dùng lệnh build để xây)
    private final String[] machineItems = {
            "Lò Bánh Mì:700", "Nồi Bỏng Ngô:1120", "Máy làm Thức ăn:420", "Nhà Máy Sữa:1400", "Nhà Máy Đường:1680",
            "Máy May:2100", "Máy Làm Bánh Ngọt:1260", "Máy Làm Phô Mai:1540", "Máy Làm Nước Ép:1820",
            "Máy Làm Mứt:1960", "Máy Làm Kem Tuyết:1190", "Lò Nướng Bánh:1330", "Máy Làm Sữa Chua:1680",
            "Máy Làm Bánh Quy:980", "Máy Làm Nước Sốt:1400", "Máy Làm Kẹo:1750", "Máy Làm Bánh Pizza:2240",
            "Máy Dệt Vải:2520", "Máy Làm Socola:1890", "Máy Làm Trà:1610", "Máy Cối Xay Gió:900","Máy Phi Lê Cá:1500"
    };


    // Danh sách vật phẩm ngẫu nhiên cho lệnh buy (giá rẻ hơn 10%)
    private final String[] randomShopItems = {
            "Bắp:32", "Cà Rốt:38", "Lúa Mì:19", "Đậu Nành:44",
            "Mía:50", "Bí Ngô:63", "Dâu Tây:76", "Cà Chua:56", "Khoai Tây:38",
            "Ớt:69", "Dưa Hấu:88", "Hành Tây:32", "Tỏi:25", "Cải Xanh:44",
            "Gà:90", "Bò:315", "Heo:432", "Cừu:558",
            "Phân Bón:25", "Thức Ăn Cho Gà:19", "Thức Ăn Cho Bò:25", "Thức Ăn Cho Lợn:32", "Thức Ăn Cho Cừu:38",
            "Táo:56", "Bông:89", "Cacao:101", "Lá Trà:63"
    };
    private List<String> currentRandomShopItems = new ArrayList<>();
    private long lastRandomShopReset = 0;

    // Danh sách đơn hàng xe tải (random mỗi ngày)
// Danh sách đơn hàng xe tải (random mỗi ngày, giá rẻ hơn sell 10%)
    private String generateRandomTruckOrder() {
        String[] crops = {"lúa mì:20", "bắp:30", "cà rốt:40", "đậu nành:50", "mía:60", "bí ngô:80", "dâu tây:100", "cà chua:60", "khoai tây:40", "ớt:80", "dưa hấu:120", "hành tây:30", "tỏi:30", "cải xanh:50"};
        String[] animals = {"trứng:20", "sữa:60", "thịt:100", "len:150"};
        String[] factory = {"bánh mì:22", "bỏng ngô:120", "thức ăn cho bò:37",  "thức ăn cho gà:20", "thức ăn cho lợn:45", "thức ăn cho cừu:52", "kem:100", "đường:120", "bánh ngọt:140", "phô mai:160", "nước ép:180", "mứt:150", "kem tuyết:130", "bánh nướng:110", "sữa chua:140", "bánh quy:80", "nước sốt:120", "kẹo:150", "bánh pizza:250", "vải dệt:200", "socola:160", "trà:140"};

        Random rand = new Random();
        int numItems = rand.nextInt(3) + 2; // 2-4 vật phẩm
        StringBuilder order = new StringBuilder();

        for (int i = 0; i < numItems; i++) {
            String[] category = (i % 3 == 0) ? crops : (i % 3 == 1) ? animals : factory;
            String[] itemParts = category[rand.nextInt(category.length)].split(":");
            String item = itemParts[0];
            int basePrice = Integer.parseInt(itemParts[1]);
            int maxQty = (category == crops) ? 10 : 5;
            int qty = rand.nextInt(maxQty) + 1;
            int reward = (int) (basePrice * qty * 0.7); // Giảm 30%

            if (i > 0) order.append(", ");
            order.append(qty).append(" ").append(item).append(":").append(reward);
        }
        return order.toString();
    }

    private long lastTruckReset = 0;
    private Map<String, String> truckOrderStatus = new HashMap<>();

    public CommandHandler(DatabaseHelper db) {
        this.db = db;
        this.inventory = new ArrayList<>();
        this.machines = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.cropTimers = new HashMap<>();
        this.animalTimers = new HashMap<>();
        this.machineTimers = new HashMap<>();
        this.currentTruckOrders = new ArrayList<>();
        this.truckOrderStatus = new HashMap<>(); // Thêm khởi tạo
        this.scanner = new Scanner(System.in);
        this.random = new Random();
    }

    public CommandHandler(Player currentPlayer, DatabaseHelper db) throws InterruptedException {
        this.currentPlayer = currentPlayer;
        this.db = db;
        this.farm = new Farm(1, currentPlayer.getId(), "trống", "không có");
        this.inventory = new ArrayList<>();
        this.machines = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.cropTimers = new HashMap<>();
        this.animalTimers = new HashMap<>();
        this.machineTimers = new HashMap<>();
        this.currentTruckOrders = new ArrayList<>();
        this.truckOrderStatus = new HashMap<>();
        this.scanner = new Scanner(System.in);
        this.random = new Random();
        this.lastTaskReset = 0;
        this.lastTruckReset = 0;
        this.lastShopReset = 0;
        this.lastRandomShopReset = 0;
        this.resetCount = 5;
        this.lastResetCountReset = 0;
        cropTimers = db.loadCropTimers(currentPlayer.getId(), "crop");
        animalTimers = db.loadCropTimers(currentPlayer.getId(), "animal");
        machineTimers = db.loadCropTimers(currentPlayer.getId(), "machine");
        farm = db.loadFarm(currentPlayer.getId());
        this.totalLandPlots = 10; // Mặc định 10 mảnh đất
        this.landPlots = new HashMap<>();
        this.landTimers = new HashMap<>();
        loadLandData();
        setCurrentPlayer(currentPlayer); // Chỉ tải dữ liệu từ DB, không cập nhật
    }

    // Hàm hiển thị văn bản với hiệu ứng gõ chữ
    private void typeEffect(String text, int delay) throws InterruptedException {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            System.out.flush();
            Thread.sleep(delay);
        }
        System.out.println();
    }

    public void setCurrentPlayer(Player player) {
        this.currentPlayer = player;
        loadPlayerData();
    }

    public void loadPlayerData() {
        inventory.clear();
        inventory.addAll(db.loadInventory(currentPlayer.getId()));
        for (Inventory item : inventory) {
            // System.out.println("Tải item: " + item.getItemName() + ", rarity: " + item.getRarity()); // Log nếu cần
        }
        machines.clear();
        machines.addAll(db.loadMachines(currentPlayer.getId()));
        tasks.clear();
        tasks.addAll(db.loadTasks(currentPlayer.getId()));
        currentTruckOrders.clear();
        currentTruckOrders.addAll(db.loadTruckOrders(currentPlayer.getId()));
        truckOrderStatus.clear();
        truckOrderStatus.putAll(db.loadTruckOrderStatus(currentPlayer.getId()));
        farm = db.loadFarm(currentPlayer.getId());
        if (farm == null) {
            farm = new Farm(currentPlayer.getId(), 0, "trống", "không có");
        }
        cropTimers.clear();
        cropTimers.putAll(db.loadCropTimers(currentPlayer.getId(), "crop"));
        animalTimers.clear();
        animalTimers.putAll(db.loadCropTimers(currentPlayer.getId(), "animal"));
        machineTimers.clear();
        machineTimers.putAll(db.loadCropTimers(currentPlayer.getId(), "machine"));
        machineProducts.clear(); // Thêm dòng này
        machineProducts.putAll(db.loadMachineProducts(currentPlayer.getId())); // Tải machineProducts
        Map<String, Long> resetData = db.loadResetCount(currentPlayer.getId());
        resetCount = resetData.getOrDefault("count", 5L).intValue();
        lastResetCountReset = resetData.getOrDefault("lastReset", 0L);
        inventoryLevel = db.loadInventoryLevel(currentPlayer.getId());
        maxInventorySlots = 100 + (inventoryLevel - 1) * 25;
        Map<String, Object> npcData = db.loadNPCOrders(currentPlayer.getId());
        currentNPCOrders.clear();
        currentNPCOrders.addAll((List<String>) npcData.getOrDefault("orders", new ArrayList<>()));
        lastNPCReset = (Long) npcData.getOrDefault("lastReset", 0L);
    }

    private void savePlayerData() {
        db.savePlayer(currentPlayer.getId(), currentPlayer.getCoins(), currentPlayer.getLevel(), currentPlayer.getExp(), inventoryLevel);
        for (Inventory item : inventory) {
            // System.out.println("Lưu item trước khi save: " + item.getItemName() + ", rarity: " + item.getRarity()); // Log nếu cần
            db.saveInventory(currentPlayer.getId(), item.getItemName(), item.getQuantity(), item.getDurability(), item.getWeight(), item.getRarity());
        }
        for (Machine machine : machines) {
            db.saveMachine(currentPlayer.getId(), machine.getMachineType(), machine.getLevel(), machine.getProductionSlots(), machine.getStatus()); // Sửa để lưu đầy đủ thông tin
        }
        db.saveTasks(currentPlayer.getId(), tasks);
        db.saveCropTimers(currentPlayer.getId(), "crop", cropTimers);
        db.saveCropTimers(currentPlayer.getId(), "animal", animalTimers);
        db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);
        db.saveMachineProducts(currentPlayer.getId(), machineProducts); // Lưu machineProducts
        db.saveFarm(currentPlayer.getId(), farm.getLand(), farm.getAnimals());
        db.saveTruckOrders(currentPlayer.getId(), currentTruckOrders);
        db.saveTruckOrderStatus(currentPlayer.getId(), truckOrderStatus);
        db.saveResetCount(currentPlayer.getId(), resetCount, lastResetCountReset);
        db.saveNPCOrders(currentPlayer.getId(), currentNPCOrders, lastNPCReset);
    }


    private void loadLandData() {
        Map<String, Object> landData = db.loadLandData(currentPlayer.getId());
        if (!landData.isEmpty()) {
            this.totalLandPlots = (int) landData.getOrDefault("totalLandPlots", 10);
            this.landPlots = (Map<Integer, String>) landData.getOrDefault("landPlots", new HashMap<>());
            this.landTimers = (Map<Integer, Long>) landData.getOrDefault("landTimers", new HashMap<>());
        } else {
            // Nếu chưa có dữ liệu, khởi tạo mặc định 10 mảnh đất trống
            for (int i = 1; i <= totalLandPlots; i++) {
                landPlots.put(i, "trống");
            }
        }
    }

    private void saveLandData() {
        db.saveLandData(currentPlayer.getId(), totalLandPlots, landPlots, landTimers);
    }

    private void updateTasksAndOrders() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;

        // Kiểm tra và thay thế nhiệm vụ hoàn thành
        List<Task> completedTasks = tasks.stream()
                .filter(t -> t.getStatus().equals("completed"))
                .toList();

        if (!completedTasks.isEmpty()) {
            for (Task completedTask : completedTasks) {
                tasks.remove(completedTask);
                // Lấy danh sách nhiệm vụ chưa có trong tasks
                List<String> availableTasks = Arrays.stream(possibleTasks)
                        .filter(possibleTask -> tasks.stream().noneMatch(t -> t.getTaskName().equals(possibleTask.split(":")[0])))
                        .collect(Collectors.toList());

                if (!availableTasks.isEmpty()) {
                    String[] parts = availableTasks.get(random.nextInt(availableTasks.size())).split(":");
                    int taskId = tasks.size() + 1;
                    Task newTask = new Task(taskId, currentPlayer.getId(), parts[0], "\u001B[33mChưa hoàn thành\u001B[0m", Integer.parseInt(parts[1]));
                    tasks.add(newTask);
                    typeEffect("\u001B[36m>> \u001B[33mNhiệm vụ mới:\u001B[0m " + newTask.getTaskName() + " - Thưởng\u001B[33m " + newTask.getReward() + "\u001B[0m xu\u001B[0m", 2);
                }
            }
            db.saveTasks(currentPlayer.getId(), tasks);
        }

        // Kiểm tra và thay thế đơn hàng xe tải hoàn thành
        List<String> completedOrders = currentTruckOrders.stream()
                .filter(order -> "completed".equals(truckOrderStatus.getOrDefault(order, "incomplete")))
                .toList();

        if (!completedOrders.isEmpty()) {
            for (String completedOrder : completedOrders) {
                currentTruckOrders.remove(completedOrder);
                truckOrderStatus.remove(completedOrder);
                String newOrder = generateRandomTruckOrder();
                currentTruckOrders.add(newOrder);
                truckOrderStatus.put(newOrder, "incomplete");
                typeEffect("\u001B[33mĐơn hàng mới: " + newOrder + "\u001B[0m", 2);
            }
        }

        // Giới hạn tối đa 6 đơn hàng
        while (currentTruckOrders.size() > 6) {
            String orderToRemove = currentTruckOrders.get(0);
            currentTruckOrders.remove(0);
            truckOrderStatus.remove(orderToRemove);
        }

        if (currentTruckOrders.isEmpty()) {
            while (currentTruckOrders.size() < 6) {
                String newOrder = generateRandomTruckOrder();
                if (!currentTruckOrders.contains(newOrder)) {
                    currentTruckOrders.add(newOrder);
                    truckOrderStatus.put(newOrder, "incomplete");
                }
            }
        }

        db.saveTruckOrders(currentPlayer.getId(), currentTruckOrders);
        db.saveTruckOrderStatus(currentPlayer.getId(), truckOrderStatus);
    }

    // 3 hàm reset quests
    private int resetCount = 5; // Số lượt reset còn lại
    private long lastResetCountReset = 0; // Thời gian reset số lượt gần nhất
    private static final long ONE_DAY_IN_SECONDS = 24 * 60 * 60; // 24 giờ

    private void resetContent() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;

        // Reset số lượt mỗi 24 giờ
        if (currentTime - lastResetCountReset >= ONE_DAY_IN_SECONDS) {
            resetCount = 10;
            lastResetCountReset = currentTime;
            db.saveResetCount(currentPlayer.getId(), resetCount, lastResetCountReset);
        }

        typeEffect("\u001B[33m\n===\u001B[0m Reset nội dung \u001B[33m===\u001B[0m", 2);
        typeEffect("Số lượt reset còn lại: \u001B[33m" + resetCount + "/10\u001B[0m", 2);
        typeEffect("1. Reset nhiệm vụ", 2);
        typeEffect("2. Reset đơn hàng xe tải", 2);
        typeEffect("3. Quay lại", 2);
        System.out.print("\u001B[36m>> Chọn (1-3): \u001B[0m");
        String input = scanner.nextLine().trim();

        if (resetCount <= 0) {
            typeEffect("\u001B[31mBạn đã hết lượt reset hôm nay!\u001B[0m", 2);
            return;
        }

        try {
            int choice = Integer.parseInt(input);
            switch (choice) {
                case 1: // Reset nhiệm vụ
                    tasks.clear();
                    for (int i = 0; i < 3; i++) {
                        String[] parts = possibleTasks[random.nextInt(possibleTasks.length)].split(":");
                        Task newTask = new Task(tasks.size() + 1, currentPlayer.getId(), parts[0], "\u001B[33mChưa hoàn thành\u001B[0m", Integer.parseInt(parts[1]));
                        if (!tasks.contains(newTask)) {
                            tasks.add(newTask);
                        }
                    }
                    db.saveTasks(currentPlayer.getId(), tasks);
                    resetCount--;
                    typeEffect("\u001B[32mĐã reset nhiệm vụ! Còn " + resetCount + " lượt reset.\u001B[0m", 2);
                    break;
                case 2: // Reset đơn hàng xe tải
                    currentTruckOrders.clear();
                    truckOrderStatus.clear();
                    for (int i = 0; i < 6; i++) {
                        String newOrder = generateRandomTruckOrder();
                        if (!currentTruckOrders.contains(newOrder)) {
                            currentTruckOrders.add(newOrder);
                            truckOrderStatus.put(newOrder, "incomplete");
                        }
                    }
                    db.saveTruckOrders(currentPlayer.getId(), currentTruckOrders);
                    db.saveTruckOrderStatus(currentPlayer.getId(), truckOrderStatus);
                    resetCount--;
                    typeEffect("\u001B[32mĐã reset đơn hàng xe tải! Còn " + resetCount + " lượt reset.\u001B[0m", 2);
                    break;
                case 3: // Quay lại
                    typeEffect("\u001B[33mQuay lại!\u001B[0m", 2);
                    break;
                default:
                    typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 2);
            }
            savePlayerData(); // Lưu trạng thái sau khi reset
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mVui lòng nhập số hợp lệ!\u001B[0m", 2);
        }
    }

    public void resetDailyContent() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;
        Map<String, Long> resetTimes = db.loadResetTimes(currentPlayer.getId());
        long lastTaskReset = resetTimes.getOrDefault("lastTaskReset", 0L);
        long lastTruckReset = resetTimes.getOrDefault("lastTruckReset", 0L);

        // Khởi tạo nhiệm vụ mặc định cho người chơi mới hoặc reset sau 24h
        if (tasks.isEmpty() || currentTime - lastTaskReset >= 24 * 60 * 60) {
            tasks.clear();
            while (tasks.size() < 3) {
                String[] parts = possibleTasks[random.nextInt(possibleTasks.length)].split(":");
                int taskId = tasks.size() + 1;
                Task newTask = new Task(taskId, currentPlayer.getId(), parts[0], "\u001B[33mChưa hoàn thành\u001B[0m", Integer.parseInt(parts[1]));
                if (!tasks.contains(newTask)) { // Tránh trùng lặp nhiệm vụ
                    tasks.add(newTask);
                }
            }
            db.saveTasks(currentPlayer.getId(), tasks);
            db.saveResetTimes(currentPlayer.getId(), currentTime, lastTruckReset, resetTimes.get("lastShopReset"), resetTimes.get("lastRandomShopReset"));
        }

        // Reset đơn hàng xe tải sau 24h hoặc khởi tạo nếu rỗng
        if (currentTruckOrders.isEmpty() || currentTime - lastTruckReset >= 24 * 60 * 60) {
            currentTruckOrders.clear();
            truckOrderStatus.clear();
            while (currentTruckOrders.size() < 6) {
                String newOrder = generateRandomTruckOrder();
                if (!currentTruckOrders.contains(newOrder)) {
                    currentTruckOrders.add(newOrder);
                    truckOrderStatus.put(newOrder, "incomplete");
                }
            }
            db.saveTruckOrders(currentPlayer.getId(), currentTruckOrders);
            db.saveTruckOrderStatus(currentPlayer.getId(), truckOrderStatus);
            db.saveResetTimes(currentPlayer.getId(), lastTaskReset, currentTime, resetTimes.get("lastShopReset"), resetTimes.get("lastRandomShopReset"));
        }
    }

    public void xuLyLenh(String command) throws InterruptedException {
        String[] parts = command.split(" ");
        String action = parts[0].toLowerCase();

        savePlayerData();
        updateTimers();

        switch (action) {
            case "farm":
                hienThongTinTrangTrai();
                break;
            case "plant":
                xuLyPlant(parts);
                break;
            case "fer":
                xuLyFertilize(parts);
                break;
            case "feed":
                xuLyFeed(parts);
                // Sau khi cho ăn, kiểm tra nhiệm vụ
                break;
            case "collect":
                collect();
                break;
            case "fish":
                try {
                    xuLyFish();
                } catch (IOException e) {
                    typeEffect("\u001B[31m<!> Lỗi khi xử lý câu cá: " + e.getMessage() + "\u001B[0m", 5);
                }
                break;
            case "craft":
                xuLyCraft(parts);
                break;
            case "sell":
                xuLySell(parts);
                break;
            case "buy":
                xuLyBuy(parts);
                break;
            case "shop":
                xemCuaHang();
                break;
            case "price":
                xuLyPrice(parts);
                break;
            case "build":
                xuLyBuild(parts);
                break;
            case "order":
                xulyOrder();
                break;
            case "truck":
                updateTasksAndOrders();
                giaoHangXeTai();
                break;
            case "tasks":
                updateTasksAndOrders();
                xemNhiemVu();
                break;
            case "reset":
                resetContent();
                break;
            case "check":
                updateTasksAndOrders();
                check();
                break;
            case "config":
                config();
                break;
            case "tool":
                handleToolCommand();
                break;
            case "logout":
                savePlayerData();
                typeEffect("\u001B[33m>> \u001B[31mĐăng Xuất Thành Công!\u001B[33m <<\u001B[0m", 5);
                currentPlayer = null;
                break;
            case "help":
                hienDanhSachLenh();
                break;
            case "exit":
                exit();
                savePlayerData();
                BackgroundSound.stop();
                break;
            default:
                typeEffect("\u001B[33m<!> \u001B[31mLệnh không hợp lệ! Gõ '\u001B[33mhelp\u001B[31m' để xem danh sách lệnh.\u001B[0m", 5);
        }
    }

    public void exit() throws InterruptedException {
        if (currentPlayer != null) {
            // Lưu inventory
            for (Inventory item : inventory) {
                db.saveInventory(currentPlayer.getId(), item.getItemName(), item.getQuantity(), item.getDurability(), item.getWeight(), item.getRarity());
            }

            // Lưu machines
            for (Machine machine : machines) {
                db.saveMachine(currentPlayer.getId(), machine.getMachineType(), machine.getLevel(), machine.getProductionSlots(), machine.getStatus());
            }

            // Lưu tasks
            db.saveTasks(currentPlayer.getId(), tasks);

            // Lưu farm (nếu có)
            if (farm != null) {
                db.saveFarm(currentPlayer.getId(), farm.getLand(), farm.getAnimals());
            }

            // Lưu timers (cropTimers, animalTimers, machineTimers)
            db.saveCropTimers(currentPlayer.getId(), "crop", cropTimers);
            db.saveCropTimers(currentPlayer.getId(), "animal", animalTimers);
            db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);

            typeEffect("\u001B[32mĐã lưu toàn bộ thông tin. Tạm biệt!\u001B[0m", 5);
        } else {
            typeEffect("\u001B[31mKhông có người chơi để lưu!\u001B[0m", 5);
        }
        System.exit(0);
    }


    private void config() throws InterruptedException {
        System.out.print("\u001B[34m\n────────┤\u001B[0m Cấu hình thông tin người chơi \u001B[34m├────────\u001B[0m");
        typeEffect("\u001B[34m\n         ───────────────────────────────\u001B[0m", 5);
        typeEffect("Tên người chơi: " + currentPlayer.getUsername(), 5);
        typeEffect("Mật khẩu: [ẩn]", 5); // Không hiển thị mật khẩu trực tiếp
        typeEffect("Cấp độ: " + currentPlayer.getLevel(), 5);
        typeEffect("Xu: " + currentPlayer.getCoins(), 5);
        typeEffect("Kinh nghiệm: " + currentPlayer.getExp(), 5);

        typeEffect("\u001B[36m>> Chọn hành động:\u001B[0m", 5);
        typeEffect("1. Đổi tên người chơi", 5);
        typeEffect("2. Đổi mật khẩu", 5);
        typeEffect("3. Xóa tài khoản", 5);
        typeEffect("4. Quay lại", 5);
        System.out.print("\u001B[33mNhập lựa chọn (1-4): \u001B[0m");
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1": // Đổi tên
                System.out.print("\u001B[33mNhập tên mới: \u001B[0m");
                String newUsername = scanner.nextLine().trim();
                if (newUsername.isEmpty()) {
                    typeEffect("\u001B[31mTên không được để trống!\u001B[0m", 5);
                    return;
                }
                System.out.print("\u001B[33mXác nhận đổi tên thành '" + newUsername + "'? (Y/N): \u001B[0m");
                String confirmName = scanner.nextLine().trim().toUpperCase();
                if (confirmName.equals("Y")) {
                    if (db.updatePlayerUsername(currentPlayer.getId(), newUsername)) {
                        currentPlayer.setUsername(newUsername);
                        typeEffect("\u001B[32mĐã đổi tên thành '" + newUsername + "'!\u001B[0m", 5);
                    } else {
                        typeEffect("\u001B[31mTên đã tồn tại hoặc lỗi khi đổi tên!\u001B[0m", 5);
                    }
                } else {
                    typeEffect("\u001B[31mĐã hủy đổi tên!\u001B[0m", 5);
                }
                break;

            case "2": // Đổi mật khẩu
                System.out.print("\u001B[33mNhập mật khẩu mới: \u001B[0m");
                String newPassword = scanner.nextLine().trim();
                if (newPassword.isEmpty()) {
                    typeEffect("\u001B[31mMật khẩu không được để trống!\u001B[0m", 5);
                    return;
                }
                System.out.print("\u001B[33mXác nhận đổi mật khẩu? (Y/N): \u001B[0m");
                String confirmPass = scanner.nextLine().trim().toUpperCase();
                if (confirmPass.equals("Y")) {
                    db.updatePlayerPassword(currentPlayer.getId(), newPassword);
                    typeEffect("\u001B[32mĐã đổi mật khẩu thành công!\u001B[0m", 5);
                } else {
                    typeEffect("\u001B[31mĐã hủy đổi mật khẩu!\u001B[0m", 5);
                }
                break;

            case "3": // Xóa tài khoản
                typeEffect("\u001B[33mCẢNH BÁO:\u001B[31m Xóa tài khoản sẽ xóa toàn bộ dữ liệu của bạn (nông trại, kho, máy móc, v.v.)!\u001B[0m", 5);
                System.out.print("\u001B[33mXác nhận xóa tài khoản '" + currentPlayer.getUsername() + "'? (Y/N): \u001B[0m");
                String confirmDelete = scanner.nextLine().trim().toUpperCase();
                if (confirmDelete.equals("Y")) {
                    db.deletePlayer(currentPlayer.getId());
                    typeEffect("\u001B[32mĐã xóa tài khoản! Chương trình sẽ thoát.\u001B[0m", 5);
                    System.exit(0); // Thoát chương trình sau khi xóa
                } else {
                    typeEffect("\u001B[31mĐã hủy xóa tài khoản!\u001B[0m", 5);
                }
                break;

            case "4": // Quay lại
                typeEffect("\u001B[32mQuay lại trò chơi!\u001B[0m", 5);
                break;

            default:
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
        }
    }

    private void check() throws InterruptedException {
        updateTasksAndOrders();
        long currentTime = System.currentTimeMillis() / 1000;

        typeEffect("\u001B[34m\n──────────┤ \u001B[0mTrạng thái hoạt động \u001B[34m├──────────\u001B[0m", 2);

        // Kiểm tra đất trồng
        typeEffect("\u001B[33mĐất trồng:\u001B[0m", 2);
        boolean hasCrops = false;
        for (int i = 1; i <= totalLandPlots; i++) {
            String crop = landPlots.getOrDefault(i, "trống");
            if (!crop.equals("trống")) {
                hasCrops = true;
                Long endTime = landTimers.get(i);
                if (endTime != null && currentTime < endTime) {
                    long timeLeft = endTime - currentTime;
                    typeEffect(" - Đất " + i + ": " + crop + " (Còn \u001B[33m" + timeLeft + "\u001B[0m giây)", 2);
                } else {
                    typeEffect(" - Đất " + i + ": " + crop + " \u001B[32m(sẵn sàng thu hoạch)\u001B[0m", 2);
                }
            }
        }
        if (!hasCrops) {
            typeEffect("\u001B[33m <->\u001B[0m Không có cây nào đang trồng.", 2);
        }
        typeEffect("\u001B[34m────────────────────────────────────────────\u001B[0m", 2);

        // Kiểm tra vật nuôi
        typeEffect("\u001B[33mVật nuôi:\u001B[0m", 5);
        if (!animalTimers.isEmpty()) {
            boolean hasActiveAnimals = false;
            for (Map.Entry<String, Long> entry : animalTimers.entrySet()) {
                long timeLeft = entry.getValue() - (System.currentTimeMillis() / 1000); // Giả định currentTime là currentTimeMillis/1000
                if (timeLeft > 0) {
                    String key = entry.getKey();
                    String animalName = key.split(": ")[0].trim(); // Trích xuất tên vật nuôi từ key
                    String sanPham = animalName.equals("gà") ? "trứng" :
                            animalName.equals("bò") ? "sữa" :
                                    animalName.equals("heo") ? "thịt" :
                                            animalName.equals("cừu") ? "len" : "không xác định";
                    typeEffect(" - " + key + " (Sản phẩm: \u001B[33m" + sanPham + "\u001B[0m): Còn \u001B[33m" + timeLeft + "\u001B[0m giây", 5);
                    hasActiveAnimals = true;
                }
            }
            if (!hasActiveAnimals) {
                typeEffect("\u001B[33m <->\u001B[0m Không có vật nuôi nào đang hoạt động.", 5);
            }
        } else {
            typeEffect("\u001B[33m <->\u001B[0m Không có vật nuôi nào đang hoạt động.", 5);
        }
        typeEffect("\u001B[34m────────────────────────────────────────────\u001B[0m", 2);

        // Kiểm tra máy móc
        typeEffect("\u001B[33mMáy móc:\u001B[0m", 2);
        if (!machines.isEmpty()) {
            for (Machine machine : machines) {
                String machineType = machine.getMachineType();
                String status = machine.getStatus();
                String timerKey = machineTimers.keySet().stream()
                        .filter(key -> key.startsWith(machineType + ":"))
                        .findFirst().orElse(null);

                if (timerKey != null) {
                    long timeLeft = machineTimers.get(timerKey) - currentTime;
                    if (timeLeft > 0) {
                        String[] parts = timerKey.split(":");
                        String product = parts[1];
                        if (product.equals("build")) {
                            typeEffect(" - \u001B[33m" + machineType + "\u001B[0m: Đang xây, còn \u001B[33m" + timeLeft + "\u001B[0m giây", 2);
                        } else {
                            int quantity = Integer.parseInt(parts[2]);
                            int totalQuantity = quantity * (machineType.equals("Máy làm Thức ăn") ? 3 : 1);
                            typeEffect(" - " + machineType + ": Đang chế biến \u001B[33m" + totalQuantity + " " + product + "\u001B[0m, còn \u001B[33m" + timeLeft + "\u001B[0m giây", 2);
                        }
                    }
                } else {
                    typeEffect(" - " + machineType + ": " + status, 2);
                }
            }
        } else {
            typeEffect(" - Không có máy móc nào.", 2);
        }
        typeEffect("\u001B[34m────────────────────────────────────────────\u001B[0m", 2);

        // Bảng nhiệm vụ hiện tại
        typeEffect("\u001B[36m>> Nhiệm vụ hiện tại:\u001B[0m", 2);
        if (tasks.isEmpty()) {
            typeEffect(" - Không có nhiệm vụ nào.", 2);
        } else {
            // Định nghĩa độ rộng cố định cho bảng nhiệm vụ
            int sttWidth = 5;      // STT: 5 ký tự
            int taskWidth = 25;    // Nhiệm vụ: 25 ký tự
            int statusWidth = 20;  // Trạng thái: 20 ký tự
            int rewardWidth = 10;  // Thưởng: 10 ký tự

            // Tiêu đề bảng nhiệm vụ
            String taskHeader = String.format(
                    "┌─────┬─────────────────────────┬────────────────────┬──────────┐\n" +
                            "│ \u001B[33m%-3s\u001B[0m │ \u001B[33m%-23s\u001B[0m │ \u001B[33m%-18s\u001B[0m │ \u001B[33m%-8s\u001B[0m │\n" +
                            "├─────┼─────────────────────────┼────────────────────┼──────────┤",
                    "STT", "Nhiệm vụ", "Trạng thái", "Thưởng"
            );
            typeEffect(taskHeader, 2);

            // Nội dung bảng nhiệm vụ
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                String taskName = task.getTaskName();
                String status = task.getStatus();
                String reward = task.getReward() + " xu";

                // Cắt ngắn nếu quá dài
                taskName = taskName.length() > taskWidth - 2 ? taskName.substring(0, taskWidth - 5) + "..." : taskName;
                status = status.replaceAll("\u001B\\[[0-9;]*m", "").length() > statusWidth - 2 ? status.substring(0, statusWidth - 5) + "..." : status;

                String row = String.format(
                        "│ %-3d │ %-23s │ %-18s    │ \u001B[33m%-8s\u001B[0m │",
                        (i + 1), taskName, status, reward
                );
                typeEffect(row, 2);
            }

            // Chân bảng nhiệm vụ
            typeEffect("└─────┴─────────────────────────┴────────────────────┴──────────┘", 2);
        }

        // Khoảng cách giữa hai bảng
        typeEffect("", 2);

        // Bảng đơn hàng xe tải
        typeEffect("\u001B[36m>> Đơn hàng xe tải:\u001B[0m", 2);
        if (currentTruckOrders.isEmpty()) {
            typeEffect(" - Không có đơn hàng xe tải nào.", 2);
        } else {
            // Định nghĩa độ rộng cố định cho bảng đơn hàng
            int sttWidth = 5;         // STT: 5 ký tự
            int itemsWidth = 80;      // Vật phẩm: tăng lên 80 ký tự
            int rewardWidth = 12;     // Thưởng: 12 ký tự

            // Tiêu đề bảng đơn hàng
            String truckHeader = String.format(
                    "┌─────┬%s┬%s┐\n" +
                            "│ \u001B[33m%-3s\u001B[0m │ \u001B[33m%-" + itemsWidth + "s\u001B[0m │ \u001B[33m%-" + (rewardWidth - 2) + "s\u001B[0m   │\n" +
                            "├─────┼%s┼%s┤",
                    "─".repeat(itemsWidth + 2),
                    "─".repeat(rewardWidth + 2),
                    "STT", "Vật phẩm", "Thưởng",
                    "─".repeat(itemsWidth + 2),
                    "─".repeat(rewardWidth + 2)
            );
            typeEffect(truckHeader, 2);

            // Nội dung bảng đơn hàng
            for (int i = 0; i < currentTruckOrders.size(); i++) {
                String order = currentTruckOrders.get(i);
                String[] items = order.split(", ");
                StringBuilder displayItems = new StringBuilder();
                int totalReward = 0;

                for (int j = 0; j < items.length; j++) {
                    String[] parts = items[j].split(":");
                    if (parts.length == 2) {
                        String itemQtyName = parts[0].trim();
                        int reward = Integer.parseInt(parts[1]);
                        if (j > 0) displayItems.append(", ");
                        displayItems.append(itemQtyName);
                        totalReward += reward;
                    }
                }

                // Không cần cắt nữa vì bảng đủ dài
                String itemsText = displayItems.toString();

                String row = String.format(
                        "│ %-3d │ %-"+itemsWidth+"s │ \u001B[33m%-" + (rewardWidth - 2) + "s\u001B[0m   │",
                        (i + 1), itemsText, totalReward + " xu"
                );
                typeEffect(row, 2);
            }

            // Chân bảng đơn hàng
            String footer = String.format("└─────┴%s┴%s┘",
                    "─".repeat(itemsWidth + 2),
                    "─".repeat(rewardWidth + 2)
            );
            typeEffect(footer, 2);
        }
    }

    private void updateTimers() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;

        // Cập nhật timer cây trồng
        cropTimers.entrySet().removeIf(entry -> {
            if (currentTime >= entry.getValue()) {
                String[] parts = entry.getKey().split(":");
                String cayTrong = parts[0];
                int plotId = Integer.parseInt(parts[1]);
                return true;
            }
            return false;
        });

        // Cập nhật timer vật nuôi
        boolean updated = false;
        String currentAnimals = farm.getAnimals();
        if (!currentAnimals.equals("không có")) {
            String[] animalEntries = currentAnimals.split("; ");
            StringBuilder newAnimals = new StringBuilder();
            for (String animalEntry : animalEntries) {
                String[] parts = animalEntry.split(": ");
                String animalName = parts[0].trim();
                String quantityStatus = parts[1].trim();
                String quantityStr = quantityStatus.split(" ")[0];
                String statusCleaned = quantityStatus.replaceAll("\\u001B\\[[;\\d]*m", "").trim();
                Long completionTime = animalTimers.get(animalName + ": " + quantityStr);

                if ((completionTime != null && currentTime >= completionTime) ||
                        (completionTime == null && statusCleaned.contains("sẵn sàng sau"))) {
                    if (newAnimals.length() > 0) newAnimals.append("; ");
                    String sanPham = switch (animalName) {
                        case "gà" -> "trứng";
                        case "bò" -> "sữa";
                        case "heo" -> "thịt";
                        case "cừu" -> "len";
                        default -> "sản phẩm";
                    };
                    newAnimals.append(animalName).append(": ").append(quantityStr).append(" (đã sẵn sàng cho ").append(sanPham).append(")");
                    animalTimers.remove(animalName + ": " + quantityStr);
                    updated = true;
                } else {
                    if (newAnimals.length() > 0) newAnimals.append("; ");
                    newAnimals.append(animalEntry);
                }
            }
            if (updated) {
                farm.setAnimals(newAnimals.toString());
                db.saveFarm(currentPlayer.getId(), farm.getLand(), farm.getAnimals());
            }
        }

// Cập nhật timer máy móc (không xóa, chỉ cập nhật trạng thái)
        for (Map.Entry<String, Long> entry : machineTimers.entrySet()) {
            if (currentTime >= entry.getValue()) {
                String[] parts = entry.getKey().split(":");
                String machineType = parts[0];
                Machine machine = machines.stream()
                        .filter(m -> m.getMachineType().equals(machineType))
                        .findFirst()
                        .orElse(null);
                if (machine == null) {
                    typeEffect("\u001B[31mKhông tìm thấy máy: " + machineType + "!\u001B[0m", 5);
                    machineTimers.remove(entry.getKey());
                    continue;
                }

                if (parts.length == 2 && parts[1].equals("build")) {
                    machine.setStatus("rảnh");
                    typeEffect("\u001B[32m" + machineType + " đã xây xong!\u001B[0m", 5);
                    db.saveMachine(currentPlayer.getId(), machineType, machine.getLevel(), machine.getProductionSlots(), "rảnh");
                    machineTimers.remove(entry.getKey()); // Xóa timer xây máy
                } else if (parts.length == 3) {
//            System.out.println("Debug: Setting " + machineType + " to 'đã sẵn sàng', timerKey = " + entry.getKey());
                    machine.setStatus("đã sẵn sàng");
                    db.saveMachine(currentPlayer.getId(), machineType, machine.getLevel(), machine.getProductionSlots(), "đã sẵn sàng");
                } else {
                    typeEffect("\u001B[31mDữ liệu timer máy móc không hợp lệ: " + entry.getKey() + "\u001B[0m", 5);
                    machineTimers.remove(entry.getKey());
                }
            }
        }
    }


    private void checkTaskCompletion(String taskName) throws InterruptedException {
        tasks.stream()
                .filter(t -> t.getTaskName().equals(taskName) && t.getStatus().equals("\u001B[33mChưa hoàn thành\u001B[0m"))
                .findFirst()
                .ifPresent(task -> {
                    task.setStatus("completed");
                    int reward = task.getReward();
                    currentPlayer.addCoins(reward);
                    try {
                        typeEffect("\u001B[36m>> \u001B[32mNhiệm vụ \u001B[0m'" + taskName + "' \u001B[32mHoàn Thành\u001B[0m! Nhận \u001B[33m" + reward + "\u001B[0m xu!\u001B[0m", 5);
                        db.saveTasks(currentPlayer.getId(), tasks);
                        savePlayerData();
                        updateTasksAndOrders(); // Tạo nhiệm vụ mới ngay sau khi hoàn thành
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
//                        System.err.println("Lỗi khi hoàn thành nhiệm vụ: " + e.getMessage());
                    }
                });

        // Kiểm tra nếu hoàn thành 3 nhiệm vụ thì làm mới (tùy chọn)
        long completedTasks = tasks.stream().filter(t -> t.getStatus().equals("completed")).count();
        if (completedTasks >= 3) {
            updateTasksAndOrders();
            typeEffect("\u001B[33mĐã hoàn thành 3 nhiệm vụ! Nhiệm vụ mới đã được tạo.\u001B[0m", 5);
        }
    }

    private void hienThongTinTrangTrai() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;
        System.out.println("\u001B[33m ┌────────────────────────────┐\u001B[0m");
        System.out.println("\u001B[33m─┤\u001B[36m    Thông Tin Trang Trại   \u001B[33m ├─\u001B[0m");
        System.out.println("\u001B[33m └────────────────────────────┘\u001B[0m");
        typeEffect("\u001B[33m>>\u001B[0m Người chơi: \u001B[36m" + currentPlayer.getUsername(), 5);
        typeEffect("\u001B[33m>> \u001B[0mXu: \u001B[33m" + currentPlayer.getCoins(), 5);
        int emptyPlots = (int) landPlots.values().stream().filter(plot -> plot.equals("trống")).count();
        typeEffect("\u001B[33m>>\u001B[0m Tổng số đất: \u001B[33m" + totalLandPlots + "\u001B[0m - Đất trống: \u001B[33m" + emptyPlots + "\u001B[0m", 5);

        typeEffect("\u001B[36mĐất trồng:\u001B[0m", 5);
        boolean hasCrops = false;
        for (int i = 1; i <= totalLandPlots; i++) {
            String crop = landPlots.getOrDefault(i, "trống");
            if (!crop.equals("trống")) {
                hasCrops = true;
                Long endTime = landTimers.get(i);
                if (endTime != null && currentTime < endTime) {
                    long timeLeft = endTime - currentTime;
                    typeEffect(" - Đất " + i + ": " + crop + " (còn \u001B[33m" + timeLeft + "\u001B[0m giây)", 5);
                } else {
                    typeEffect(" - Đất " + i + ": " + crop + "\u001B[32m đã sẵn sàng\u001B[0m", 5);
                }
            }
        }
        if (!hasCrops) {
            typeEffect("\u001B[33m - Không có cây nào đang trồng.", 5);
        }

        // Hiển thị chuồng
        typeEffect("\u001B[36m>> Chuồng:\u001B[0m", 2);
        String animals = farm.getAnimals();
        int index = 1;
        boolean hasAnimals = false;

        if (!animals.equals("không có") && !animals.equals("Chuồng trống!")) {
            String[] animalEntries = animals.split("; ");
            for (String entry : animalEntries) {
                String[] parts = entry.split(": ");
                String animalName = parts[0].trim();
                String quantityStatus = parts[1].trim();
                String quantityStr = quantityStatus.split(" ")[0];
                int quantity = Integer.parseInt(quantityStr);

                String sanPham = switch (animalName) {
                    case "gà" -> "trứng";
                    case "bò" -> "sữa";
                    case "heo" -> "thịt";
                    case "cừu" -> "len";
                    default -> "không xác định";
                };

                Long completionTime = animalTimers.get(animalName + ": " + quantityStr);
                if (completionTime != null) {
                    long remainingTime = completionTime - currentTime;
                    if (remainingTime > 0) {
                        typeEffect(" " + index + ". " + animalName + ": " + quantity + " (sẵn sàng sau \u001B[33m" + remainingTime + "\u001B[0m giây)", 2);
                    } else {
                        typeEffect(" " + index + ". " + animalName + ": " + quantity + " \u001B[32m(đã sẵn sàng cho \u001B[33m" + sanPham + "\u001B[32m)\u001B[0m", 2);
                    }
                } else if (quantityStatus.contains("đang đói")) {
                    typeEffect(" " + index + ". " + animalName + ": " + quantity + " \u001B[33m(đang đói)\u001B[0m", 2);
                } else {
                    typeEffect(" " + index + ". " + animalName + ": " + quantity + " \u001B[32m(đã sẵn sàng cho \u001B[33m" + sanPham + "\u001B[32m)\u001B[0m", 2);
                }
                index++;
                hasAnimals = true;
            }
        }

        if (!hasAnimals) {
            typeEffect("\u001B[31m - Chuồng trống!\u001B[0m", 2);
        }

        // Danh sách cá để kiểm tra phân loại và màu sắc
        String[] commonFish = {
                "Cá rô phi", "Cá tra", "Cá basa", "Cá mè trắng", "Cá trắm cỏ", "Cá chép",
                "Cá lóc", "Cá trê", "Cá diêu hồng", "Cá nục", "Cá thu", "Cá ngân",
                "Cá đối", "Cá mòi", "Cá bạc má", "Cá sòng", "Cá bống", "Cá kèo",
                "Cá linh", "Cá cơm", "Cá sơn", "Cá ét", "Cá hanh", "Cá ngát"
        };
        String[] uncommonFish = {
                "Cá bống tượng", "Cá lăng", "Cá mú", "Cá chim trắng", "Cá tai tượng", "Cá bớp",
                "Cá sặc rằn", "Cá hồng", "Cá trèn", "Cá he", "Cá thác lác", "Cá úc",
                "Cá chạch", "Cá mối", "Cá nhồng", "Cá dìa"
        };
        String[] legendaryFish = {
                "Cá chình", "Cá hô", "Cá anh vũ", "Cá leo", "Cá chiên", "Cá bông lau",
                "Cá ngựa", "Cá heo nước ngọt", "Cá rồng", "Cá mặt trăng", "Cá sấu", "Cá tầm"
        };
        String[] superRareFish = {
                "Cá tra dầu", "Cá sủ vàng", "Cá mập bò", "Cá đuối sông", "Cá lưỡi trâu", "Cá hải tượng",
                "Cá chép Koi", "Cá hề", "Cá mó", "Cá thòi lòi", "Cá bống sao", "Cá vồ cờ"
        };

        // Hiển thị kho hàng dạng bảng
        int totalItems = inventory.stream()
                .filter(item -> item.getQuantity() > 0)
                .mapToInt(Inventory::getQuantity)
                .sum();
        System.out.println("\u001B[36m┌─────────────────────────┐\u001B[0m");
        typeEffect("\u001B[36m├─ \u001B[0m  Kho hàng (\u001B[32m" + totalItems + "/\u001B[33m" + maxInventorySlots + "\u001B[36m)   ─┤\u001B[0m", 2);
        System.out.println("\u001B[36m└─────────────────────────┘\u001B[0m");

        List<Inventory> nonZeroInventory = inventory.stream()
                .filter(item -> item.getQuantity() > 0)
                .toList();

        if (nonZeroInventory.isEmpty()) {
            typeEffect("\u001B[31m - Kho trống!\u001B[0m", 2);
        } else {
            String tableFormat = "│ %3d │ %-25s │ %8d │"; // Định dạng bảng: STT (3), Vật Phẩm (25), Số Lượng (8)

            // Sản phẩm của động vật
            List<Inventory> animalProducts = nonZeroInventory.stream()
                    .filter(item -> animalHarvestTimes.containsKey(item.getItemName().toLowerCase()) ||
                            item.getItemName().equalsIgnoreCase("trứng") ||
                            item.getItemName().equalsIgnoreCase("sữa") ||
                            item.getItemName().equalsIgnoreCase("thịt") ||
                            item.getItemName().equalsIgnoreCase("len") ||
                            item.getItemName().toLowerCase().contains("thức ăn cho"))
                    .toList();
            if (!animalProducts.isEmpty()) {
                System.out.println();
                typeEffect("\u001B[33m─────────┤\u001B[36m Sản phẩm của động vật \u001B[33m├─────────\u001B[0m", 2);
                typeEffect("\u001B[33m┌─────┬───────────────────────────┬──────────┐\u001B[0m", 2);
                typeEffect("\u001B[36m│ STT │ Vật Phẩm                  │ Số Lượng │\u001B[0m", 2);
                typeEffect("\u001B[33m├─────┼───────────────────────────┼──────────┤\u001B[0m", 2);
                int stt = 1;
                for (Inventory item : animalProducts) {
                    typeEffect(String.format(tableFormat, stt++, item.getItemName(), item.getQuantity()), 2);
                }
                typeEffect("\u001B[33m└─────┴───────────────────────────┴──────────┘\u001B[0m", 2);
            }

            // Cây trồng
            List<Inventory> crops = nonZeroInventory.stream()
                    .filter(item -> cropGrowthTimes.containsKey(item.getItemName().toLowerCase()))
                    .toList();
            if (!crops.isEmpty()) {
                System.out.println();
                typeEffect("\u001B[32m─────────┤ \u001B[33mCây trồng\u001B[32m ├─────────\u001B[0m", 2);
                typeEffect("\u001B[32m┌─────┬───────────────────────────┬──────────┐\u001B[0m", 2);
                typeEffect("\u001B[33m│ STT │ Vật Phẩm                  │ Số Lượng │\u001B[0m", 2);
                typeEffect("\u001B[32m├─────┼───────────────────────────┼──────────┤\u001B[0m", 2);
                int stt = 1;
                for (Inventory item : crops) {
                    typeEffect(String.format(tableFormat, stt++, item.getItemName(), item.getQuantity()), 2);
                }
                typeEffect("\u001B[32m└─────┴───────────────────────────┴──────────┘\u001B[0m", 2);
            }

            // Sản phẩm nhà máy
            List<Inventory> factoryProducts = nonZeroInventory.stream()
                    .filter(item -> machineProcessingTimes.keySet().stream()
                            .anyMatch(machine -> item.getItemName().toLowerCase().equals(machine.toLowerCase()) ||
                                    item.getItemName().toLowerCase().equals(getProductFromMachine(machine).toLowerCase())) ||
                            item.getItemName().equalsIgnoreCase("phân bón"))
                    .toList();
            if (!factoryProducts.isEmpty()) {
                System.out.println();
                typeEffect("\u001B[34m─────────┤\u001B[0m Sản phẩm nhà máy \u001B[34m├─────────\u001B[0m", 2);
                typeEffect("\u001B[34m┌─────┬───────────────────────────┬──────────┐\u001B[0m", 2);
                typeEffect("│ STT │ Vật Phẩm                  │ Số Lượng │", 2);
                typeEffect("\u001B[34m├─────┼───────────────────────────┼──────────┤\u001B[0m", 2);
                int stt = 1;
                for (Inventory item : factoryProducts) {
                    typeEffect(String.format(tableFormat, stt++, item.getItemName(), item.getQuantity()), 2);
                }
                typeEffect("\u001B[34m└─────┴───────────────────────────┴──────────┘\u001B[0m", 2);
            }

            // Cá
            List<Inventory> fishItems = nonZeroInventory.stream()
                    .filter(item -> {
                        String itemName = item.getItemName().toLowerCase();
                        return itemName.contains("cá") &&
                                !itemName.contains("cần câu cá") &&
                                !itemName.equals("cá phi lê");
                    })
                    .toList();

            if (!fishItems.isEmpty()) {
                System.out.println();
                typeEffect("\u001B[36m─────────┤\u001B[33m Cá \u001B[36m├─────────\u001B[0m", 2);

                int maxNameLength = "Vật Phẩm".length();
                int maxTypeLength = "Phân loại".length();
                int maxWeightLength = "Khối lượng".length();
                for (Inventory item : fishItems) {
                    String fishName = item.getItemName();
                    String fishType = item.getRarity() != null ? item.getRarity() : "thường";
                    maxNameLength = Math.max(maxNameLength, fishName.length());
                    maxTypeLength = Math.max(maxTypeLength, fishType.length());
                    if (item.getWeight() != null) {
                        String weightStr = item.getWeight() + " kg";
                        maxWeightLength = Math.max(maxWeightLength, weightStr.length());
                    }
                }

                String fishTableFormat = "│ %3d │ %-" + maxNameLength + "s │ %-" + maxTypeLength + "s │ %-" + maxWeightLength + "s │ %8d │";
                String headerFormat = "│ %3s │ %-" + maxNameLength + "s │ %-" + maxTypeLength + "s │ %-" + maxWeightLength + "s │ %8s │";
                String lineFormat = "├─────┼─" + "─".repeat(maxNameLength) + "─┼─" + "─".repeat(maxTypeLength) + "─┼─" + "─".repeat(maxWeightLength) + "─┼──────────┤";
                String topLine = "┌─────┬─" + "─".repeat(maxNameLength) + "─┬─" + "─".repeat(maxTypeLength) + "─┬─" + "─".repeat(maxWeightLength) + "─┬──────────┐";
                String bottomLine = "└─────┴─" + "─".repeat(maxNameLength) + "─┴─" + "─".repeat(maxTypeLength) + "─┴─" + "─".repeat(maxWeightLength) + "─┴──────────┘";

                typeEffect("\u001B[36m" + topLine + "\u001B[0m", 2);
                typeEffect("\u001B[33m" + String.format(headerFormat, "STT", "Vật Phẩm", "Phân loại", "Khối lượng", "Số Lượng") + "\u001B[0m", 2);
                typeEffect("\u001B[36m" + lineFormat + "\u001B[0m", 2);

                int stt = 1;
                for (Inventory item : fishItems) {
                    String fishName = item.getItemName();
                    String fishType = item.getRarity() != null ? item.getRarity() : "thường";
                    String plainWeight = item.getWeight() != null ? item.getWeight() + " kg" : "";
                    String fishColor = "\u001B[37m";

                    String coloredName;
                    String coloredType;

                    switch (fishType) {
                        case "hiếm":
                            fishColor = "\u001B[32m";
                            coloredName = fishColor + fishName + "\u001B[0m";
                            coloredType = fishColor + fishType + "\u001B[0m";
                            break;
                        case "siêu hiếm":
                            fishColor = "\u001B[35m";
                            coloredName = fishColor + fishName + "\u001B[0m";
                            coloredType = fishColor + fishType + "\u001B[0m";
                            break;
                        case "huyền thoại":
                            coloredName = rainbowText(fishName);
                            coloredType = rainbowText(fishType);
                            break;
                        default:
                            coloredName = "\u001B[37m" + fishName + "\u001B[0m";
                            coloredType = "\u001B[37m" + fishType + "\u001B[0m";
                            break;
                    }

                    String paddedName = coloredName + " ".repeat(maxNameLength - fishName.length());
                    String paddedType = coloredType + " ".repeat(maxTypeLength - fishType.length());
                    String paddedWeight = item.getWeight() != null
                            ? "\u001B[32m" + plainWeight + "\u001B[0m" + " ".repeat(maxWeightLength - plainWeight.length())
                            : " ".repeat(maxWeightLength);

                    String coloredLine = "│ " + String.format("%3d", stt++) + " │ " + paddedName + " │ " +
                            paddedType + " │ " + paddedWeight + " │ " + String.format("%8d", item.getQuantity()) + " │";

                    typeEffect(coloredLine, 2);
                }

                typeEffect("\u001B[36m" + bottomLine + "\u001B[0m", 2);
            }

            // Công cụ
            List<Inventory> tools = nonZeroInventory.stream()
                    .filter(item -> {
                        String itemName = item.getItemName().toLowerCase();
                        return itemName.equals("cần câu cá") ||
                                itemName.equals("đinh") ||
                                itemName.equals("ván gỗ");
                    })
                    .toList();
            if (!tools.isEmpty()) {
                System.out.println();
                typeEffect("─────────┤\u001B[36m Công cụ \u001B[0m├─────────", 2);
                typeEffect("┌─────┬───────────────────────────┬──────────┐", 2);
                typeEffect("\u001B[36m│ STT │ Vật Phẩm                  │ Số Lượng │\u001B[0m", 2);
                typeEffect("├─────┼───────────────────────────┼──────────┤", 2);
                int stt = 1;
                for (Inventory item : tools) {
                    String displayName = item.getItemName();
                    String extraInfo = "";
                    if (item.getDurability() != null) {
                        if (item.getDurability() == 0) {
                            extraInfo += " \u001B[31m(hỏng)\u001B[0m";
                        } else {
                            extraInfo += " \u001B[33m(Độ Bền: " + item.getDurability() + ")\u001B[0m";
                        }
                    }
                    typeEffect(String.format(tableFormat, stt++, displayName, item.getQuantity()) + extraInfo, 2);
                }
                typeEffect("└─────┴───────────────────────────┴──────────┘", 2);
            }

            typeEffect("\u001B[32m──────────────────────────────────────────────\u001B[0m", 2);
        }

// Hiển thị máy móc
        typeEffect("\u001B[33mMáy móc:\u001B[0m", 2);
        if (machines.isEmpty()) {
            typeEffect(" - Không có máy móc nào!", 2);
        } else {
            // Tính độ dài tối đa cho từng cột
            int maxMachineLength = "Máy Móc".length();
            int maxLevelLength = "Level".length();
            int maxFreeSlotsLength = "Ô trống".length();
            int maxStatusLength = "Trạng Thái".length();
            int maxProductLength = "Sản Phẩm".length();
            int maxTimeLength = "Thời gian còn lại".length();

            List<String[]> machineData = new ArrayList<>();

            for (Machine machine : machines) {
                int maxSlots = machine.getLevel() * 3; // Giả định mỗi level có 3 ô
                String machineName = machine.getMachineType() + ""; // Hiển thị số ô tối đa
                String levelStr = String.valueOf(machine.getLevel());
                int totalSlots = maxSlots; // Tổng số ô là maxSlots, không phải productionSlots
                int busySlots = 0;
                String statusCleaned = (machine.getStatus() != null)
                        ? machine.getStatus().replaceAll("\\u001B\\[[;\\d]*m", "").trim()
                        : "Không xác định";
                String product = "-";
                String remainingTime = "-";

                if (statusCleaned.equals("đang hoạt động")) {
                    String keyPrefix = machine.getMachineType() + ":";
                    Map.Entry<String, Long> activeTask = machineTimers.entrySet().stream()
                            .filter(entry -> entry.getKey().startsWith(keyPrefix))
                            .findFirst().orElse(null);

                    if (activeTask != null) {
                        String[] parts = activeTask.getKey().split(":");
                        String sanPham = parts[1]; // Tên sản phẩm
                        int quantity = Integer.parseInt(parts[2]); // Số lượng
                        long endTime = activeTask.getValue();
                        long timeLeft = Math.max(0, endTime - currentTime);

                        product = quantity + " " + sanPham;
                        remainingTime = timeLeft + " giây";
                        busySlots = quantity; // Số ô bị chiếm = số lượng sản phẩm đang chế biến
                    }
                }

                int freeSlots = machine.getProductionSlots(); // Ô trống hiện tại
                if (freeSlots < 0) freeSlots = totalSlots; // Sửa nếu ô trống âm
                String freeSlotsStr = String.valueOf(freeSlots);

                // Cập nhật độ dài tối đa
                maxMachineLength = Math.max(maxMachineLength, machineName.length());
                maxLevelLength = Math.max(maxLevelLength, levelStr.length());
                maxFreeSlotsLength = Math.max(maxFreeSlotsLength, freeSlotsStr.length());
                maxStatusLength = Math.max(maxStatusLength, statusCleaned.length());
                maxProductLength = Math.max(maxProductLength, product.length());
                maxTimeLength = Math.max(maxTimeLength, remainingTime.length());

                machineData.add(new String[]{machineName, levelStr, freeSlotsStr, machine.getStatus(), product, remainingTime});
            }

            // Định dạng bảng
            String headerFormat = "│ %3s │ %-" + maxMachineLength + "s │ %-" + maxLevelLength + "s │ %-" + maxFreeSlotsLength + "s │ %-" + maxStatusLength + "s │ %-" + maxProductLength + "s │ %-" + maxTimeLength + "s │";
            String rowFormat = "│ %3d │ %-" + maxMachineLength + "s │ %-" + maxLevelLength + "s │ %-" + maxFreeSlotsLength + "s │ %-" + maxStatusLength + "s │ %-" + maxProductLength + "s │ %-" + maxTimeLength + "s │";
            String lineFormat = "├─────┼─" + "─".repeat(maxMachineLength) + "─┼─" + "─".repeat(maxLevelLength) + "─┼─" + "─".repeat(maxFreeSlotsLength) + "─┼─" + "─".repeat(maxStatusLength) + "─┼─" + "─".repeat(maxProductLength) + "─┼─" + "─".repeat(maxTimeLength) + "─┤";
            String topLine = "┌─────┬─" + "─".repeat(maxMachineLength) + "─┬─" + "─".repeat(maxLevelLength) + "─┬─" + "─".repeat(maxFreeSlotsLength) + "─┬─" + "─".repeat(maxStatusLength) + "─┬─" + "─".repeat(maxProductLength) + "─┬─" + "─".repeat(maxTimeLength) + "─┐";
            String bottomLine = "└─────┴─" + "─".repeat(maxMachineLength) + "─┴─" + "─".repeat(maxLevelLength) + "─┴─" + "─".repeat(maxFreeSlotsLength) + "─┴─" + "─".repeat(maxStatusLength) + "─┴─" + "─".repeat(maxProductLength) + "─┴─" + "─".repeat(maxTimeLength) + "─┘";

            // In bảng
            typeEffect("\u001B[36m" + topLine + "\u001B[0m", 2);
            typeEffect("\u001B[33m" + String.format(headerFormat, "STT", "Máy Móc", "Level", "Ô trống", "Trạng Thái", "Sản Phẩm", "Thời gian còn lại") + "\u001B[0m", 2);
            typeEffect("\u001B[36m" + lineFormat + "\u001B[0m", 2);

            int stt = 1;
            for (String[] data : machineData) {
                String machineName = data[0];
                String level = data[1];
                String freeSlots = data[2];
                String status = data[3]; // Giữ mã màu nếu có
                String product = data[4];
                String time = data[5];
                typeEffect(String.format(rowFormat, stt++, machineName, level, freeSlots, status, product, time), 2);
            }
            typeEffect("\u001B[36m" + bottomLine + "\u001B[0m", 2);
        }
    }

    // Hàm phụ để lấy sản phẩm từ máy móc (cập nhật để dùng MACHINE_PRODUCTS)
    private String getProductFromMachine(String machine) {
        String[][] products = MACHINE_PRODUCTS.get(machine);
        return (products != null && products.length > 0) ? products[0][0] : "";
    }

    private void handleInventoryItem(Inventory item) throws InterruptedException {
        typeEffect("\u001B[34m\n=== Vật phẩm: \u001B[0m" + item.getItemName() + "\u001B[34m ===\u001B[0m", 5);
        typeEffect("Số lượng: \u001B[33m" + item.getQuantity(), 5);
        typeEffect("\u001B[36m>> Chọn hành động:\u001B[0m", 5);
        typeEffect("1. Bán vật phẩm", 5);
        typeEffect("2. Quay lại", 5);
        System.out.print("\u001B[33mNhập lựa chọn (1-2): \u001B[0m");
        String choice = scanner.nextLine().trim();

        switch (choice) {
            case "1": // Bán vật phẩm
                System.out.print("\u001B[33m>> \u001B[0mNhập số lượng muốn bán \u001B[33m(mặc định 1, tối đa " + item.getQuantity() + "): \u001B[0m");
                String sellQuantityInput = scanner.nextLine().trim();
                int sellQuantity;
                try {
                    sellQuantity = sellQuantityInput.isEmpty() ? 1 : Integer.parseInt(sellQuantityInput);
                    if (sellQuantity <= 0) {
                        typeEffect("\u001B[31mSố lượng phải lớn hơn 0!\u001B[0m", 5);
                        return;
                    }
                    if (sellQuantity > item.getQuantity()) {
                        typeEffect("\u001B[31mKhông đủ " + item.getItemName() + " trong kho!\u001B[0m", 5);
                        return;
                    }
                } catch (NumberFormatException e) {
                    typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
                    return;
                }

                int pricePerUnit = getItemPrice(item.getItemName()); // Giá bán mỗi đơn vị
                int totalPrice = pricePerUnit * sellQuantity;
                System.out.print("\u001B[33m>> \u001B[0mXác nhận bán \u001B[33m" + sellQuantity + " " + item.getItemName() + "\u001B[0m với giá \u001B[33m" + totalPrice + "\u001B[0m xu? \u001B[33m(Y/N): \u001B[0m");
                String confirm = scanner.nextLine().trim().toUpperCase();
                if (confirm.equals("Y")) {
                    capNhatKhoHang(item.getItemName(), -sellQuantity);
                    currentPlayer.addCoins(totalPrice);
                    typeEffect("\u001B[32m>> \u001B[0mĐã bán \u001B[33m" + sellQuantity + " " + item.getItemName() + "\u001B[33m và kiếm được \u001B[33m" + totalPrice + "\u001B[0m xu!", 5);
                } else {
                    typeEffect("\u001B[31m>> Đã hủy bán!\u001B[0m", 5);
                }
                break;

            case "2": // Quay lại
                typeEffect("\u001B[32m>> Quay lại trang trại!\u001B[0m", 5);
                break;

            default:
                typeEffect("\u001B[31m>> Lựa chọn không hợp lệ!\u001B[0m", 5);
        }
    }

    private static final Map<String, Integer> basePrices = new HashMap<>() {{
        // Cây trồng
        put("lúa mì", 16); put("bắp", 28); put("cà rốt", 40); put("đậu nành", 55); put("mía cây", 70);
        put("bí ngô", 170); put("ớt", 70); put("dâu tây", 100); put("cà chua", 70); put("khoai tây", 55);
        put("dưa hấu", 165); put("hành tây", 40); put("tỏi", 35); put("cải xanh", 45); put("táo", 120);
        put("bông", 200); put("cacao", 240); put("lá trà", 160);

        // Sản phẩm từ vật nuôi
        put("trứng", 21); put("sữa", 80); put("thịt", 260); put("len", 385);

        // Sản phẩm từ máy móc (đã có trong danh sách cũ)
        put("bánh mì", 22); put("bỏng ngô", 86); put("thức ăn cho gà", 20); put("kem", 140); put("đường", 150);
        put("thức ăn cho bò", 37); put("thức ăn cho lợn", 45); put("thức ăn cho cừu", 52);
        put("vải cotton", 230); put("bánh dâu", 130); put("phô mai", 120); put("nước ép dâu", 140); put("mứt táo", 150);
        put("kem tuyết", 100); put("bánh nướng", 110); put("sữa chua", 130); put("bánh quy", 90); put("nước sốt", 95);
        put("kẹo", 120); put("bánh pizza", 160); put("vải", 260); put("socola", 300); put("trà xanh", 200); put("cá phi lê", 85);

        // Cá - Common (24 loài, giá từ 10-34 xu)
        put("Cá rô phi", 10); put("Cá tra", 12); put("Cá basa", 13); put("Cá mè trắng", 14);
        put("Cá trắm cỏ", 15); put("Cá chép", 16); put("Cá lóc", 17); put("Cá trê", 18);
        put("Cá diêu hồng", 19); put("Cá nục", 20); put("Cá thu", 21); put("Cá ngân", 22);
        put("Cá đối", 23); put("Cá mòi", 24); put("Cá bạc má", 25); put("Cá sòng", 26);
        put("Cá bống", 27); put("Cá kèo", 28); put("Cá linh", 29); put("Cá cơm", 30);
        put("Cá sơn", 31); put("Cá ét", 32); put("Cá hanh", 33); put("Cá ngát", 34);

        // Cá - Uncommon (16 loài, giá từ 40-85 xu)
        put("Cá bống tượng", 40); put("Cá lăng", 43); put("Cá mú", 46); put("Cá chim trắng", 49);
        put("Cá tai tượng", 52); put("Cá bớp", 55); put("Cá sặc rằn", 58); put("Cá hồng", 61);
        put("Cá trèn", 64); put("Cá he", 67); put("Cá thác lác", 70); put("Cá úc", 73);
        put("Cá chạch", 76); put("Cá mối", 79); put("Cá nhồng", 82); put("Cá dìa", 85);

        // Cá - Super Rare (12 loài, giá từ 150-300 xu)
        put("Cá chình", 170); put("Cá ngựa", 180); put("Cá rồng", 195); put("Cá sấu", 205);
        put("Cá mập bò", 225); put("Cá lưỡi trâu", 235); put("Cá hải tượng", 250); put("Cá chép Koi", 270);
        put("Cá hề", 305); put("Cá mó", 375); put("Cá thòi lòi", 420); put("Cá bống sao", 610);

        // Cá - Legendary (12 loài, giá từ 400-1000 xu)
        put("Cá tra dầu", 1200); put("Cá sủ vàng", 1350); put("Cá đuối sông", 1500); put("Cá hô", 1650);
        put("Cá anh vũ", 1800); put("Cá chiên", 1950); put("Cá heo nước ngọt", 2100); put("Cá leo", 2250);
        put("Cá bông lau", 2400); put("Cá mặt trăng", 2550); put("Cá tầm", 2700); put("Cá vồ cờ", 3000);

        // Sản phẩm từ nhà máy (giá đề xuất - thay đổi nếu bạn có giá chính xác)
        put("sirô", 180);           // 2 mía (70) + giá trị gia tăng
        put("áo len", 450);         // 2 len (385) + giá trị gia tăng
        put("bánh ngọt", 80);       // 2 bột mì + 1 trứng + 1 đường
        put("phô mai dê", 130);     // 2 sữa dê (giả định giá tương tự sữa bò)
        put("nước ép táo", 150);    // 3 táo (120) + giá trị gia tăng
        put("mứt dâu", 140);        // 2 dâu tây + 1 đường
        put("kem tuyết dâu", 110);  // 1 sữa + 1 dâu tây
        put("sữa chua dâu", 135);   // 1 sữa + 1 dâu tây
        put("nước sốt cà chua", 95); // 2 cà chua (70) + giá trị gia tăng
        put("kẹo caramel", 125);    // 1 đường + 1 sữa
        put("vải dệt", 290);        // 3 len (385) + giá trị gia tăng
        put("bột mì", 20);          // 2 lúa mì (16) + giá trị gia tăng
    }};

    private int getItemPrice(String itemName) {
        // Chuẩn hóa itemName để loại bỏ khoảng trắng thừa
        itemName = itemName.trim();

        // Thử tìm giá với nhiều biến thể của tên
        Integer basePrice = basePrices.get(itemName);
        if (basePrice == null) {
            // Thử tên viết thường
            basePrice = basePrices.get(itemName.toLowerCase());
        }
        if (basePrice == null) {
            // Thử tên viết hoa chữ cái đầu
            String normalizedName = itemName.substring(0, 1).toUpperCase() + itemName.substring(1).toLowerCase();
            basePrice = basePrices.get(normalizedName);
            if (basePrice == null) {
//                System.out.println("Debug: Không tìm thấy giá cho '" + itemName + "' (đã thử '" + normalizedName + "') trong basePrices");
                basePrice = 1; // Giá mặc định nếu không tìm thấy
            }
        }

        // Đặc biệt cho "mía"
        if (itemName.equalsIgnoreCase("mía")) {
            basePrice = basePrices.get("mía cây");
        }

        // Tính giá biến động theo ngày
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        int dayOfYear = today.getDayOfYear();
        Random random = new Random(dayOfYear + itemName.hashCode());

        double rand = random.nextDouble();
        double fluctuation;

        if (rand < 0.4) {
            // 40% Không đổi
            fluctuation = 0.0;
        } else if (rand < 0.7) {
            // 30% dao động nhẹ ±1% → ±3%
            fluctuation = -0.03 + (random.nextDouble() * 0.06); // (-0.03 đến +0.03)
        } else if (rand < 0.9) {
            // 20% dao động vừa ±4% → ±7%
            fluctuation = -0.07 + (random.nextDouble() * 0.14); // (-0.07 đến +0.07)
        } else {
            // 10% dao động mạnh ±8% → ±17%
            fluctuation = -0.08 + (random.nextDouble() * 0.25); // (-0.08 đến +0.17)
        }

        int finalPrice = (int) Math.round(basePrice * (1 + fluctuation));
        return Math.max(finalPrice, 1);
    }

    private void xuLyPlant(String[] parts) throws InterruptedException {
        // Đếm số đất trống
        int emptyPlotsCount = 0;
        for (int i = 1; i <= totalLandPlots; i++) {
            if (landPlots.getOrDefault(i, "trống").equals("trống")) {
                emptyPlotsCount++;
            }
        }
        if (emptyPlotsCount == 0) {
            typeEffect("\u001B[31mKhông còn đất trống để trồng!\u001B[0m", 5);
            return;
        }

        // Lấy danh sách cây trồng từ kho của người chơi
        List<Inventory> availableCropsInInventory = inventory.stream()
                .filter(item -> cropGrowthTimes.containsKey(item.getItemName().toLowerCase()) && item.getQuantity() > 0)
                .toList();

        if (availableCropsInInventory.isEmpty()) {
            typeEffect("\u001B[31mBạn không có cây trồng nào trong kho để trồng!\u001B[0m", 5);
            return;
        }

        // Hiển thị danh sách cây trồng trong kho dạng bảng
        typeEffect("\u001B[34m\n=== Cây Trồng Trong Kho ===\u001B[0m", 5);
        String header = String.format(
                "┌─────┬────────────────────┬──────────┬────────────────────────┐\n" +
                        "\u001B[34m│ %-3s │ %-18s │ %-8s │ %-22s │\u001B[0m\n" +
                        "├─────┼────────────────────┼──────────┼────────────────────────┤",
                "STT", "Cây trồng", "Số lượng", "Thời gian trồng (giây)"
        );
        typeEffect(header, 5);

        for (int i = 0; i < availableCropsInInventory.size(); i++) {
            Inventory item = availableCropsInInventory.get(i);
            String cropName = item.getItemName();
            int quantity = item.getQuantity();
            int time = cropGrowthTimes.get(cropName.toLowerCase());
            String row = String.format(
                    "│ %-3d │ %-18s │ %-8d │ %-22d │",
                    (i + 1), cropName, quantity, time
            );
            typeEffect(row, 5);
        }
        typeEffect("└─────┴────────────────────┴──────────┴────────────────────────┘", 5);

        // Chọn cây trồng
        typeEffect("\u001B[36m>> Chọn cây trồng (1-" + availableCropsInInventory.size() + " hoặc nhập tên): \u001B[0m", 5);
        String input = scanner.nextLine().trim().toLowerCase();

        String cayTrong = null;
        Inventory selectedCrop = null;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < availableCropsInInventory.size()) {
                selectedCrop = availableCropsInInventory.get(index);
                cayTrong = selectedCrop.getItemName().toLowerCase();
            }
        } catch (NumberFormatException e) {
            selectedCrop = availableCropsInInventory.stream()
                    .filter(item -> item.getItemName().toLowerCase().equals(input))
                    .findFirst()
                    .orElse(null);
            if (selectedCrop != null) {
                cayTrong = selectedCrop.getItemName().toLowerCase();
            }
        }

        if (cayTrong == null || selectedCrop == null) {
            typeEffect("\u001B[31mCây trồng không hợp lệ hoặc không có trong kho!\u001B[0m", 5);
            return;
        }

        // Hỏi số lượng muốn trồng
        int maxQuantity = Math.min(emptyPlotsCount, selectedCrop.getQuantity());
        typeEffect("\u001B[33mNhập số lượng muốn trồng (tối đa " + maxQuantity + ", mặc định 1): \u001B[0m", 5);
        String soLuongInput = scanner.nextLine().trim();
        int soLuong;
        try {
            soLuong = soLuongInput.isEmpty() ? 1 : Integer.parseInt(soLuongInput);
            if (soLuong <= 0) {
                typeEffect("\u001B[31mSố lượng phải lớn hơn 0!\u001B[0m", 5);
                return;
            }
            if (soLuong > maxQuantity) {
                typeEffect("\u001B[31mSố lượng vượt quá số đất trống hoặc số cây trong kho! Tối đa là " + maxQuantity + ".\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
            return;
        }

        // Trồng cây trên các mảnh đất trống và trừ kho
        long baseTime = cropGrowthTimes.get(cayTrong);
        long completionTime = System.currentTimeMillis() / 1000 + baseTime;
        int plantedCount = 0;

        for (int i = 1; i <= totalLandPlots && plantedCount < soLuong; i++) {
            if (landPlots.getOrDefault(i, "trống").equals("trống")) {
                landPlots.put(i, cayTrong);
                landTimers.put(i, completionTime);
                plantedCount++;
            }
        }

        // Trừ số lượng cây khỏi kho
        selectedCrop.setQuantity(selectedCrop.getQuantity() - plantedCount);
        if (selectedCrop.getQuantity() <= 0) {
            inventory.remove(selectedCrop);
        }

        saveLandData();
        savePlayerData(); // Lưu lại kho sau khi trừ
        typeEffect("\u001B[32mĐã trồng " + plantedCount + " " + cayTrong + " trên " + plantedCount + " mảnh đất! Sẵn sàng thu hoạch sau " + baseTime + " giây.\u001B[0m", 5);
    }

    private void xuLyFertilize(String[] parts) throws InterruptedException {
        // Kiểm tra kho có phân bón không
        int fertilizerCount = 0;
        for (Inventory item : inventory) {
            if (item.getItemName().equalsIgnoreCase("Phân Bón")) {
                fertilizerCount = item.getQuantity();
                break;
            }
        }
        if (fertilizerCount == 0) {
            typeEffect("\u001B[33mKhông có phân bón trong kho! Hãy mua thêm ở 'shop' nhé!\u001B[0m", 5);
            return;
        }

        // Kiểm tra có cây nào đang trồng không
        List<Integer> growingPlots = new ArrayList<>();
        long currentTime = System.currentTimeMillis() / 1000;
        for (int i = 1; i <= totalLandPlots; i++) {
            String crop = landPlots.getOrDefault(i, "trống");
            Long endTime = landTimers.get(i);
            if (!crop.equals("trống") && endTime != null && endTime > currentTime) {
                growingPlots.add(i);
            }
        }
        if (growingPlots.isEmpty()) {
            typeEffect("\u001B[31mKhông có cây nào đang trồng! Nhập 'plant' để trồng cây đi!\u001B[0m", 5);
            return;
        }

        // Hiển thị danh sách cây đang trồng
        typeEffect("\u001B[34m\n=== Cây đang trồng ===\u001B[0m", 5);
        for (int i = 0; i < growingPlots.size(); i++) {
            int plotId = growingPlots.get(i);
            String cayTrong = landPlots.get(plotId);
            long remainingTime = landTimers.get(plotId) - currentTime;
            typeEffect((i + 1) + ". Đất " + plotId + ": " + cayTrong + " - Còn " + remainingTime + " giây", 5);
        }
        typeEffect((growingPlots.size() + 1) + ". Bón tất cả (" + growingPlots.size() + " cây, bạn có " + fertilizerCount + " phân bón)", 5);
        typeEffect("\u001B[36m>> Chọn (1-" + (growingPlots.size() + 1) + "): \u001B[0m", 5);
        String input = scanner.nextLine().trim();

        int choice;
        try {
            choice = Integer.parseInt(input);
            if (choice < 1 || choice > growingPlots.size() + 1) {
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
            return;
        }

        // Xử lý bón phân
        if (choice <= growingPlots.size()) {
            // Bón cho một cây cụ thể
            int plotId = growingPlots.get(choice - 1);
            String cayTrong = landPlots.get(plotId);
            long remainingTime = landTimers.get(plotId) - currentTime;
            long timeReduction = (long) (remainingTime * 0.1); // Giảm 10%
            long newCompletionTime = landTimers.get(plotId) - timeReduction;

            capNhatKhoHang("Phân Bón", -1);
            landTimers.put(plotId, newCompletionTime);
            saveLandData();
            typeEffect("\u001B[32mĐã bón phân cho " + cayTrong + " trên đất " + plotId + "! Thời gian thu hoạch giảm " + timeReduction + " giây, còn " + (newCompletionTime - currentTime) + " giây.\u001B[0m", 5);
        } else {
            // Bón tất cả
            int maxFertilizable = Math.min(growingPlots.size(), fertilizerCount);
            int fertilizedCount = 0;

            for (int i = 0; i < maxFertilizable; i++) {
                int plotId = growingPlots.get(i);
                long remainingTime = landTimers.get(plotId) - currentTime;
                long timeReduction = (long) (remainingTime * 0.3);
                long newCompletionTime = landTimers.get(plotId) - timeReduction;
                landTimers.put(plotId, newCompletionTime);
                fertilizedCount++;
            }

            if (fertilizedCount > 0) {
                capNhatKhoHang("Phân Bón", -fertilizedCount);
                saveLandData();
                int unfertilizedCount = growingPlots.size() - fertilizedCount;
                typeEffect("\u001B[32mĐã bón phân cho " + fertilizedCount + " cây! " + (unfertilizedCount > 0 ? "Còn " + unfertilizedCount + " cây chưa được bón phân do thiếu phân bón." : "") + "\u001B[0m", 5);
            }
        }

        savePlayerData(); // Lưu dữ liệu người chơi sau khi bón phân
    }

    private void xuLyFish() throws InterruptedException, IOException {
        String fishingTool = "Cần Câu Cá";
        int durability = getFishingToolDurability();

        if (durability == -1) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mBạn cần 1 Cần câu cá để bắt đầu!\u001B[0m", 5);
            return;
        }

        if (durability == 0) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m>> \u001B[0mCần câu của bạn đã hỏng, bạn có muốn sửa với giá \u001B[33m185\u001B[0m xu không? \u001B[33m(Y/N)\u001B[0m", 5);
            String choice = scanner.nextLine().trim().toUpperCase();
            if (choice.equals("Y")) {
                if (currentPlayer.getCoins() >= 185) {
                    currentPlayer.addCoins(-185);
                    updateFishingToolDurability(fishingTool, 10);
                    typeEffect("\u001B[32m>>\u001B[0m Cần câu đã được sửa, độ bền trở lại \u001B[33m10\u001B[0m!", 5);
                    savePlayerData();
                    durability = getFishingToolDurability();
                } else {
                    typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mBạn không đủ xu để sửa cần câu (cần 185 xu)!\u001B[0m", 5);
                    return;
                }
            } else {
                typeEffect("\u001B[33m>> Đã hủy sửa cần câu.\u001B[0m", 5);
                return;
            }
        }

        typeEffect(">>\u001B[36m Đang Câu Cá \u001B[33m...\u001B[0m", 10);
        char[] spinner = {'|', '/', '-', '\\'};
        long startTime = System.currentTimeMillis();
        Random rand = new Random();
        long duration = 5000 + (long)(rand.nextDouble() * (19000 - 5000 + 1));
        int spinnerIndex = 0;

        // Chạy spinner và kiểm tra input sớm
        while (System.currentTimeMillis() - startTime < duration) {
            System.out.print("\b" + spinner[spinnerIndex]);
            System.out.flush();
            spinnerIndex = (spinnerIndex + 1) % spinner.length;
            if (System.in.available() > 0) { // Kiểm tra nếu có input sẵn sàng
                scanner.nextLine(); // Đọc và bỏ qua input
                typeEffect("\u001B[31m> Cá chưa cắn câu đâu, vội gì mà kéo!\u001B[0m", 5);
                durability--;
                updateFishingToolDurability(fishingTool, durability);
                if (durability == 0) {
                    typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mCần câu cá của bạn đã \u001B[31mhỏng!\u001B[0m", 5);
                }
                typeEffect("\u001B[34m>> \u001B[0mĐộ bền cần câu còn: \u001B[33m" + durability, 5);
                return;
            }
            Thread.sleep(200);
        }

        System.out.print("\b ");
        System.out.println();
        SoundEffect.playSound("fishing.wav"); // Phát âm thanh cá cắn câu
        Thread.sleep(500); // Tăng delay lên để "đợi" tiếng phát rõ
        typeEffect("\u001B[33m>\u001B[36m Cá đã cắn câu!! Ấn \u001B[33mEnter\u001B[36m để câu cá!\u001B[0m", 1);

        // Quyết định độ hiếm trước để xác định thời gian chờ
        int rarityRoll = rand.nextInt(100);
        long reactionTime;
        String rarityForReaction;
        if (rarityRoll < 1) { // Huyền thoại
            reactionTime = 550; // 0.55 giây
            rarityForReaction = "huyền thoại";
        } else if (rarityRoll < 4) { // Siêu hiếm
            reactionTime = 900; // 0.9 giây
            rarityForReaction = "siêu hiếm";
        } else if (rarityRoll < 35) { // Hiếm
            reactionTime = 1250; // 1.25 giây
            rarityForReaction = "hiếm";
        } else { // Thường
            reactionTime = 1850; // 1.85 giây
            rarityForReaction = "thường";
        }

        // Đo thời gian người chơi phản ứng
        long reactionStart = System.currentTimeMillis();
        boolean fishCaught = false;
        while (System.currentTimeMillis() - reactionStart < reactionTime) {
            if (System.in.available() > 0) {
                scanner.nextLine(); // Đọc và bỏ qua input
                fishCaught = true;
                break;
            }
            Thread.sleep(10); // Giảm CPU usage
        }

        if (!fishCaught) { // Nếu người chơi không nhấn Enter kịp
            typeEffect("\u001B[31m>\u001B[33m Cá đã thoát mất! Bạn phải kéo cần nhanh hơn nữa!\u001B[0m", 5);
            durability--;
            updateFishingToolDurability(fishingTool, durability);
            if (durability == 0) {
                typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mCần câu cá của bạn đã \u001B[31mhỏng!\u001B[0m", 5);
            }
            typeEffect("\u001B[34m>> \u001B[0mĐộ bền cần câu còn: \u001B[33m" + durability, 5);
            return; // Thoát hàm nếu hụt cá
        }

        // Nếu người chơi nhấn Enter kịp, tiếp tục xử lý
        durability--;
        updateFishingToolDurability(fishingTool, durability);
        if (durability == 0) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mCần câu cá của bạn đã \u001B[31mhỏng!\u001B[0m", 5);
        }

        int roll = rand.nextInt(100);
        if (roll < 8) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mDây câu bị đứt, bạn không câu được cá nào!\u001B[0m", 5);
            typeEffect("\u001B[34m>> Độ bền cần câu còn: \u001B[33m" + durability, 5);
            return;
        }

        roll = rand.nextInt(100); // Roll lại để xác định loại cá
        String fishType;
        String fishColor;
        String rarity;
        double minWeight, maxWeight;

        if (roll < 1) { // Legendary: 0.5% (0-0) - Hiếm nhất
            String[] legendaryFish = {
                    "Cá tra dầu", "Cá sủ vàng", "Cá đuối sông", "Cá hô", "Cá anh vũ", "Cá chiên",
                    "Cá heo nước ngọt", "Cá leo", "Cá bông lau", "Cá mặt trăng", "Cá tầm", "Cá vồ cờ"
            };
            fishType = legendaryFish[rand.nextInt(legendaryFish.length)];
            fishColor = ""; // Không cần màu vì sẽ dùng cầu vồng
            rarity = "huyền thoại";
            typeEffect(rainbowText("✨✨ Bạn vừa bắt được cá huyền thoại: " + fishType + "!! ✨✨"), 3);
        } else if (roll < 4) { // 4 Super Rare: 2.5% (1-3)
            String[] superRareFish = {
                    "Cá chình", "Cá ngựa", "Cá rồng", "Cá sấu", "Cá mập bò", "Cá lưỡi trâu",
                    "Cá hải tượng", "Cá chép Koi", "Cá hề", "Cá mó", "Cá thòi lòi", "Cá bống sao"
            };
            fishType = superRareFish[rand.nextInt(superRareFish.length)];
            fishColor = "\u001B[35m";
            rarity = "siêu hiếm";
        } else if (roll < 35) { // 35
            String[] uncommonFish = {
                    "Cá bống tượng", "Cá lăng", "Cá mú", "Cá chim trắng", "Cá tai tượng", "Cá bớp",
                    "Cá sặc rằn", "Cá hồng", "Cá trèn", "Cá he", "Cá thác lác", "Cá úc",
                    "Cá chạch", "Cá mối", "Cá nhồng", "Cá dìa"
            };
            fishType = uncommonFish[rand.nextInt(uncommonFish.length)];
            fishColor = "\u001B[32m";
            rarity = "hiếm";
        } else { // Common: 60% (41-99)
            String[] commonFish = {
                    "Cá rô phi", "Cá tra", "Cá basa", "Cá mè trắng", "Cá trắm cỏ", "Cá chép",
                    "Cá lóc", "Cá trê", "Cá diêu hồng", "Cá nục", "Cá thu", "Cá ngân",
                    "Cá đối", "Cá mòi", "Cá bạc má", "Cá sòng", "Cá bống", "Cá kèo",
                    "Cá linh", "Cá cơm", "Cá sơn", "Cá ét", "Cá hanh", "Cá ngát"
            };
            fishType = commonFish[rand.nextInt(commonFish.length)];
            fishColor = "\u001B[37m";
            rarity = "thường";
        }

        // Gán khoảng khối lượng thực tế cho từng loại cá
        switch (fishType) {
            // Legendary Fish (Hiếm nhất)
            case "Cá tra dầu": minWeight = 10.0; maxWeight = 300.0; break;
            case "Cá sủ vàng": minWeight = 2.0; maxWeight = 100.0; break;
            case "Cá đuối sông": minWeight = 5.0; maxWeight = 600.0; break;
            case "Cá hô": minWeight = 10.0; maxWeight = 150.0; break;
            case "Cá anh vũ": minWeight = 0.5; maxWeight = 15.0; break;
            case "Cá chiên": minWeight = 5.0; maxWeight = 100.0; break;
            case "Cá heo nước ngọt": minWeight = 10.0; maxWeight = 200.0; break;
            case "Cá leo": minWeight = 1.0; maxWeight = 40.0; break;
            case "Cá bông lau": minWeight = 2.0; maxWeight = 50.0; break;
            case "Cá mặt trăng": minWeight = 50.0; maxWeight = 1000.0; break;
            case "Cá tầm": minWeight = 2.0; maxWeight = 100.0; break;
            case "Cá vồ cờ": minWeight = 2.0; maxWeight = 50.0; break;

            // Super Rare Fish
            case "Cá chình": minWeight = 0.5; maxWeight = 20.0; break;
            case "Cá ngựa": minWeight = 0.01; maxWeight = 0.1; break;
            case "Cá rồng": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá sấu": minWeight = 5.0; maxWeight = 50.0; break;
            case "Cá mập bò": minWeight = 5.0; maxWeight = 150.0; break;
            case "Cá lưỡi trâu": minWeight = 1.0; maxWeight = 20.0; break;
            case "Cá hải tượng": minWeight = 5.0; maxWeight = 200.0; break;
            case "Cá chép Koi": minWeight = 0.3; maxWeight = 15.0; break;
            case "Cá hề": minWeight = 0.05; maxWeight = 0.5; break;
            case "Cá mó": minWeight = 0.3; maxWeight = 5.0; break;
            case "Cá thòi lòi": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá bống sao": minWeight = 0.1; maxWeight = 0.5; break;

            // Uncommon Fish
            case "Cá bống tượng": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá lăng": minWeight = 1.0; maxWeight = 20.0; break;
            case "Cá mú": minWeight = 0.5; maxWeight = 50.0; break;
            case "Cá chim trắng": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá tai tượng": minWeight = 1.0; maxWeight = 20.0; break;
            case "Cá bớp": minWeight = 2.0; maxWeight = 100.0; break;
            case "Cá sặc rằn": minWeight = 0.2; maxWeight = 2.0; break;
            case "Cá hồng": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá trèn": minWeight = 0.5; maxWeight = 5.0; break;
            case "Cá he": minWeight = 0.5; maxWeight = 5.0; break;
            case "Cá thác lác": minWeight = 0.2; maxWeight = 2.0; break;
            case "Cá úc": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá chạch": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá mối": minWeight = 0.2; maxWeight = 3.0; break;
            case "Cá nhồng": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá dìa": minWeight = 0.3; maxWeight = 5.0; break;

            // Common Fish
            case "Cá rô phi": minWeight = 0.2; maxWeight = 2.0; break;
            case "Cá tra": minWeight = 0.5; maxWeight = 20.0; break;
            case "Cá basa": minWeight = 0.5; maxWeight = 20.0; break;
            case "Cá mè trắng": minWeight = 1.0; maxWeight = 30.0; break;
            case "Cá trắm cỏ": minWeight = 1.0; maxWeight = 40.0; break;
            case "Cá chép": minWeight = 0.5; maxWeight = 20.0; break;
            case "Cá lóc": minWeight = 0.3; maxWeight = 5.0; break;
            case "Cá trê": minWeight = 0.2; maxWeight = 3.0; break;
            case "Cá diêu hồng": minWeight = 0.3; maxWeight = 3.0; break;
            case "Cá nục": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá thu": minWeight = 0.5; maxWeight = 10.0; break;
            case "Cá ngân": minWeight = 0.2; maxWeight = 2.0; break;
            case "Cá đối": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá mòi": minWeight = 0.05; maxWeight = 0.5; break;
            case "Cá bạc má": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá sòng": minWeight = 0.2; maxWeight = 2.0; break;
            case "Cá bống": minWeight = 0.05; maxWeight = 0.5; break;
            case "Cá kèo": minWeight = 0.05; maxWeight = 0.3; break;
            case "Cá linh": minWeight = 0.01; maxWeight = 0.1; break;
            case "Cá cơm": minWeight = 0.01; maxWeight = 0.05; break;
            case "Cá sơn": minWeight = 0.1; maxWeight = 1.0; break;
            case "Cá ét": minWeight = 0.05; maxWeight = 0.5; break;
            case "Cá hanh": minWeight = 0.2; maxWeight = 3.0; break;
            case "Cá ngát": minWeight = 0.2; maxWeight = 5.0; break;

            default:
                minWeight = 0.2; maxWeight = 5.0; break;
        }

        // Tính khối lượng với phân phối không đều (thiên về cá nhỏ)
        double lambda = 2.0;
        double randomExp = -Math.log(1 - rand.nextDouble()) / lambda;
        double weight = minWeight + (maxWeight - minWeight) * Math.min(randomExp, 1.0);
        weight = Math.round(weight * 10) / 10.0;
        int fishCount = 1;
        if (isInventoryFull(1)) {
            typeEffect("\u001B[31mKho đã đầy! Không thể bắt thêm cá!\u001B[0m", 5);
            return;
        }

        capNhatKhoHang(fishType, fishCount, weight, rarity);
        fishWeightForTask += weight;

        if (!rarity.equals("huyền thoại")) {
            typeEffect("\u001B[32m>>\u001B[0m Bạn đã câu được \u001B[36m1 " + fishColor + fishType + "\u001B[0m (" + rarity + ", " + weight + " kg)!\u001B[0m", 5);        }
        typeEffect("\u001B[34m>> \u001B[0mĐộ bền cần câu còn: \u001B[33m" + durability, 5);

        if (rand.nextDouble() < 0.0345) {
            if (!isInventoryFull(1)) {
                capNhatKhoHang("ván gỗ", 1, null, null);
                typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Ván Gỗ!\u001B[0m", 5);
            }
        }
        if (rand.nextDouble() < 0.0035) {
            if (!isInventoryFull(1)) {
                capNhatKhoHang("đinh", 1, null, null);
                typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Đinh!\u001B[0m", 5);
            }
        }

        double totalFishWeight = getTotalFishWeight();
        for (Task task : tasks) {
            if (task.getStatus().equals("\u001B[33mChưa hoàn thành\u001B[0m") && task.getTaskName().startsWith("Câu ")) {
                String taskName = task.getTaskName().replaceAll("\\u001B\\[[;\\d]*m", "").trim();
                String[] taskParts = taskName.split(" ", 3);
                if (taskParts.length == 3 && taskParts[2].equals("cá")) {
                    try {
                        double requiredWeight = Double.parseDouble(taskParts[1].replace("kg", ""));
                        if (totalFishWeight >= requiredWeight) {
                            task.setStatus("completed");
                            int reward = task.getReward();
                            currentPlayer.addCoins(reward);
                            typeEffect("\u001B[36m>> \u001B[32mNhiệm vụ \u001B[0m'" + taskName + "' \u001B[32mHoàn Thành\u001B[0m! Nhận \u001B[33m" + reward + "\u001B[0m xu!\u001B[0m", 5);
                            db.saveTasks(currentPlayer.getId(), tasks);
                            savePlayerData();
                            updateTasksAndOrders();
                            fishWeightForTask = 0.0;
                            break;
                        } else {
                            typeEffect("\u001B[33mTổng trọng lượng cá đã câu: " + totalFishWeight + " kg (cần " + requiredWeight + " kg để hoàn thành nhiệm vụ)\u001B[0m", 5);
                        }
                    } catch (NumberFormatException | InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        savePlayerData();
    }

    private double fishWeightForTask = 0.0; // Tổng trọng lượng cá cho nhiệm vụ hiện tại
    private double getTotalFishWeight() {
        return fishWeightForTask; // Trả về tổng trọng lượng cá đã câu cho nhiệm vụ hiện tại
    }

    // Helper: Lấy độ bền hiện tại của Cần Câu Cá
    private int getFishingToolDurability() {
        for (Inventory item : inventory) {
            if (item.getItemName().equalsIgnoreCase("Cần Câu Cá") && item.getQuantity() > 0) {
                return item.getDurability() != null ? item.getDurability() : 10; // Mặc định 10 nếu chưa có độ bền
            }
        }
        return -1; // Không có dụng cụ
    }

    // Helper: Cập nhật độ bền của Cần Câu Cá
    private void updateFishingToolDurability(String itemName, int newDurability) throws InterruptedException {
        for (Inventory item : inventory) {
            if (item.getItemName().equalsIgnoreCase(itemName)) {
                item.setDurability(newDurability);
                db.saveInventory(currentPlayer.getId(), item.getItemName(), item.getQuantity(), item.getDurability(), item.getWeight(), item.getRarity());
                break;
            }
        }
    }

    private void xuLyFeed(String[] parts) throws InterruptedException {
        String farmAnimals = farm.getAnimals();
        if (farmAnimals.equals("không có")) {
            typeEffect("\u001B[31mChuồng trống!\u001B[0m", 5);
            return;
        }
        typeEffect("\u001B[34m=== Danh sách vật nuôi trong chuồng ===\u001B[0m", 5);
        String[] animalEntries = farmAnimals.split("; ");
        for (int i = 0; i < animalEntries.length; i++) {
            typeEffect((i + 1) + ". " + animalEntries[i], 5);
        }
        typeEffect("\u001B[36m>> Chọn vật nuôi (1-" + animalEntries.length + " hoặc tên): \u001B[0m", 5);
        String input = scanner.nextLine().trim();
        String animalName;
        int animalIndex;
        try {
            animalIndex = Integer.parseInt(input) - 1;
            if (animalIndex < 0 || animalIndex >= animalEntries.length) {
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
                return;
            }
            animalName = animalEntries[animalIndex].split(":")[0].trim();
        } catch (NumberFormatException e) {
            animalName = input.toLowerCase();
            animalIndex = -1;
            for (int i = 0; i < animalEntries.length; i++) {
                if (animalEntries[i].split(":")[0].trim().equalsIgnoreCase(animalName)) {
                    animalIndex = i;
                    break;
                }
            }
            if (animalIndex == -1) {
                typeEffect("\u001B[31mVật nuôi không tồn tại!\u001B[0m", 5);
                return;
            }
        }
        String entry = animalEntries[animalIndex];
        String[] animalParts = entry.split(": ");
        String quantityStr = animalParts[1].split(" ")[0];
        int animalQuantity = Integer.parseInt(quantityStr);
        String currentStatus = animalParts[1].substring(quantityStr.length()).trim();
        if (!currentStatus.contains("đang đói")) {
            typeEffect("\u001B[31m" + animalName + " không đói!\u001B[0m", 5);
            return;
        }
        String foodItem = "Thức ăn cho " + animalName;
        System.out.print("\u001B[36mNhập số lượng " + animalName + " cần cho ăn (mặc định 1, tối đa " + animalQuantity + "): \u001B[0m");
        String qtyInput = scanner.nextLine().trim();
        int qtyToFeed;
        try {
            qtyToFeed = qtyInput.isEmpty() ? 1 : Integer.parseInt(qtyInput);
            if (qtyToFeed <= 0 || qtyToFeed > animalQuantity) {
                typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
            return;
        }
        if (!kiemTraKhoHang(foodItem, qtyToFeed)) {
            typeEffect("\u001B[31mKhông đủ " + foodItem + " trong kho!\u001B[0m", 5);
            return;
        }
        capNhatKhoHang(foodItem, -qtyToFeed);
        long completionTime = System.currentTimeMillis() / 1000 + animalHarvestTimes.get(animalName);
        StringBuilder newAnimals = new StringBuilder();
        for (int i = 0; i < animalEntries.length; i++) {
            if (i > 0) newAnimals.append("; ");
            if (i == animalIndex) {
                newAnimals.append(animalName).append(": ").append(animalQuantity).append(" (sẵn sàng sau ").append(animalHarvestTimes.get(animalName)).append(" giây)");
            } else {
                newAnimals.append(animalEntries[i]);
            }
        }
        farm.setAnimals(newAnimals.toString());
        animalTimers.put(animalName + ": " + animalQuantity, completionTime);
        db.saveFarm(currentPlayer.getId(), farm.getLand(), farm.getAnimals());
        db.saveCropTimers(currentPlayer.getId(), "animal", animalTimers);
        typeEffect("\u001B[32mĐã cho " + qtyToFeed + " " + animalName + " ăn! Sẵn sàng sau " + animalHarvestTimes.get(animalName) + " giây.\u001B[0m", 5);
        checkTaskCompletion("Cho " + qtyToFeed + " con " + animalName + " ăn");
    }

    private void choAn(String vatNuoi, int soLuong) throws InterruptedException {
        String currentAnimals = farm.getAnimals();
        String[] entries = currentAnimals.split("; ");
        StringBuilder newAnimals = new StringBuilder();
        boolean fed = false;

        for (String entry : entries) {
            if (entry.startsWith(vatNuoi + ":") && entry.contains("(đang đói)")) {
                String[] parts = entry.split(": ");
                int currentCount = Integer.parseInt(parts[1].split(" ")[0]);
                if (soLuong <= currentCount) {
                    String foodItem = "thức ăn cho " + vatNuoi;
                    Optional<Inventory> food = inventory.stream()
                            .filter(item -> item.getItemName().equalsIgnoreCase(foodItem) && item.getQuantity() >= soLuong)
                            .findFirst();

                    if (food.isPresent()) {
                        food.get().addQuantity(-soLuong);
                        long completionTime = System.currentTimeMillis() / 1000 + animalHarvestTimes.get(vatNuoi);
                        String newEntry = vatNuoi + ": " + soLuong; // Lưu đơn giản, trạng thái sẽ được xử lý trong hiển thị
                        animalTimers.put(newEntry, completionTime); // Key không chứa trạng thái, chỉ có tên và số lượng
                        if (currentCount > soLuong) {
                            newEntry += "; " + vatNuoi + ": " + (currentCount - soLuong) + " (đang đói)";
                        }
                        if (newAnimals.length() > 0) newAnimals.append("; ");
                        newAnimals.append(newEntry);
                        fed = true;
                    } else {
                        typeEffect("\u001B[31mKhông đủ thức ăn '" + foodItem + "' trong kho! Cần ít nhất " + soLuong + " đơn vị.\u001B[0m", 5);
                        return;
                    }
                }
            } else {
                if (newAnimals.length() > 0) newAnimals.append("; ");
                newAnimals.append(entry);
            }
        }

        if (fed) {
            farm.setAnimals(newAnimals.toString());
            db.saveFarm(currentPlayer.getId(), farm.getLand(), newAnimals.toString());
            typeEffect("\u001B[32mĐã cho " + soLuong + " " + vatNuoi + " ăn! Sẵn sàng sau " + animalHarvestTimes.get(vatNuoi) + " giây.\u001B[0m", 5);
        } else {
            typeEffect("\u001B[31mKhông có " + vatNuoi + " nào đang đói để cho ăn!\u001B[0m", 5);
        }
    }

    private Map<String, String> machineProducts = new HashMap<>(); // Lưu "machineType:product:quantity"
    private static final Map<String, String[][]> MACHINE_PRODUCTS = new HashMap<>();

    static {
        MACHINE_PRODUCTS.put("Lò Bánh Mì", new String[][]{{"Bánh mì", "1 bột mì", "240 giây"}});
        MACHINE_PRODUCTS.put("Nồi Bỏng Ngô", new String[][]{{"Bỏng ngô", "2 bắp", "1440 giây"}});
        MACHINE_PRODUCTS.put("Máy làm Thức ăn", new String[][]{
                {"Thức ăn cho gà", "1 lúa mì + 1 bắp", "180 giây"},
                {"Thức ăn cho bò", "2 đậu nành + 1 bắp", "240 giây"},
                {"Thức ăn cho lợn", "2 cà rốt + 1 bí ngô", "300 giây"},
                {"Thức ăn cho cừu", "2 lúa mì + 1 cải xanh", "210 giây"}
        });
        MACHINE_PRODUCTS.put("Nhà Máy Sữa", new String[][]{
                {"Kem", "1 sữa", "2880 giây"},
                {"Phô mai", "2 sữa", "3600 giây"}
        });
        MACHINE_PRODUCTS.put("Nhà Máy Đường", new String[][]{
                {"Đường", "1 mía", "3840 giây"},
                {"Sirô", "2 mía", "4800 giây"}
        });
        MACHINE_PRODUCTS.put("Máy May", new String[][]{
                {"Áo len", "2 len", "1200 giây"},
                {"Vải cotton", "3 bông", "1800 giây"}
        });
        MACHINE_PRODUCTS.put("Máy Làm Bánh Ngọt", new String[][]{
                {"Bánh ngọt", "2 bột mì + 1 trứng + 1 đường", "720 giây"},
                {"Bánh dâu", "2 dâu tây + 1 đường", "900 giây"}
        });
        MACHINE_PRODUCTS.put("Máy Làm Phô Mai", new String[][]{{"Phô mai dê", "2 sữa dê", "880 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Nước Ép", new String[][]{
                {"Nước ép táo", "3 táo", "1040 giây"},
                {"Nước ép dâu", "3 dâu tây", "1200 giây"}
        });
        MACHINE_PRODUCTS.put("Máy Làm Mứt", new String[][]{
                {"Mứt dâu", "2 dâu tây + 1 đường", "1120 giây"},
                {"Mứt táo", "2 táo + 1 đường", "1200 giây"}
        });
        MACHINE_PRODUCTS.put("Máy Làm Kem Tuyết", new String[][]{{"Kem tuyết dâu", "1 sữa + 1 dâu tây", "680 giây"}});
        MACHINE_PRODUCTS.put("Lò Nướng Bánh", new String[][]{{"Bánh nướng", "3 bột mì + 1 trứng", "760 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Sữa Chua", new String[][]{{"Sữa chua dâu", "1 sữa + 1 dâu tây", "960 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Bánh Quy", new String[][]{{"Bánh quy", "2 bột mì + 1 trứng + 1 bơ", "560 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Nước Sốt", new String[][]{{"Nước sốt cà chua", "2 cà chua", "800 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Kẹo", new String[][]{{"Kẹo caramel", "1 đường + 1 sữa", "1000 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Bánh Pizza", new String[][]{{"Bánh pizza", "3 bột mì + 1 phô mai + 1 cà chua", "1280 giây"}});
        MACHINE_PRODUCTS.put("Máy Dệt Vải", new String[][]{{"Vải dệt", "3 len", "1440 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Socola", new String[][]{{"Socola", "2 cacao + 1 đường", "1080 giây"}});
        MACHINE_PRODUCTS.put("Máy Làm Trà", new String[][]{{"Trà xanh", "2 lá trà", "920 giây"}});
        MACHINE_PRODUCTS.put("Máy Cối Xay Gió", new String[][]{{"bột mì", "2 lúa mì", "300 giây"}});
        MACHINE_PRODUCTS.put("Máy Phi Lê Cá", new String[][]{{"Cá phi lê", "1 cá (tùy chọn)", "600 giây"}});
    }

    private void collect() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;
        boolean hasCollected = false;
        Map<String, Integer> totalHarvestedCrops = new HashMap<>();
        Map<String, Integer> totalHarvestedAnimalProducts = new HashMap<>();

        // Thu hoạch từ cây trồng (giữ nguyên)
        typeEffect("\u001B[34m>> Đất trồng:\u001B[0m", 5);
        boolean hasCropsToCollect = false;
        for (int i = 1; i <= totalLandPlots; i++) {
            String crop = landPlots.getOrDefault(i, "trống");
            if (!crop.equals("trống")) {
                Long endTime = landTimers.get(i);
                if (endTime != null && currentTime >= endTime) {
                    if (isInventoryFull(2)) {
                        typeEffect("\u001B[31mKho đã đầy! Không thể thu hoạch " + crop + " từ đất " + i + "!\u001B[0m", 5);
                        continue;
                    }
                    int harvestQty = 2;
                    capNhatKhoHang(crop, harvestQty);
                    landPlots.put(i, "trống");
                    landTimers.remove(i);
                    cropTimers.remove(crop + ":" + i);
                    totalHarvestedCrops.merge(crop, harvestQty, Integer::sum);
                    hasCollected = true;
                    hasCropsToCollect = true;

                    if (random.nextDouble() < 0.0245) {
                        if (!isInventoryFull(1)) {
                            capNhatKhoHang("ván gỗ", 1);
                            typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Ván Gỗ!\u001B[0m", 5);
                        }
                    }
                    if (random.nextDouble() < 0.03535) {
                        if (!isInventoryFull(1)) {
                            capNhatKhoHang("đinh", 1);
                            typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Đinh!\u001B[0m", 5);
                        }
                    }
                }
            }
        }
        if (hasCropsToCollect) {
            for (Map.Entry<String, Integer> entry : totalHarvestedCrops.entrySet()) {
                typeEffect("\u001B[32mĐã thu hoạch " + entry.getValue() + " " + entry.getKey() + "\u001B[0m", 5);
            }
        } else {
            typeEffect("\u001B[33mCây trồng chưa có sản phẩm nào để thu thập\u001B[0m", 5);
        }

        // Thu hoạch từ vật nuôi (giữ nguyên)
        typeEffect("\u001B[34m>> Vật nuôi:\u001B[0m", 5);
        String farmAnimals = farm.getAnimals();
        boolean hasAnimalsToCollect = false;
        if (!farmAnimals.equals("không có")) {
            String[] animalEntries = farmAnimals.split("; ");
            StringBuilder newAnimals = new StringBuilder();
            for (String entry : animalEntries) {
                String[] parts = entry.split(": ");
                String animalName = parts[0].trim();
                String quantityStatus = parts[1].trim();
                String quantityStr = quantityStatus.split(" ")[0];
                int quantity = Integer.parseInt(quantityStr);
                String statusCleaned = quantityStatus.replaceAll("\\u001B\\[[;\\d]*m", "").trim();
                Long completionTime = animalTimers.get(animalName + ": " + quantityStr);

                boolean isReady = (completionTime != null && currentTime >= completionTime) ||
                        statusCleaned.contains("đã sẵn sàng");

                if (isReady) {
                    String sanPham = switch (animalName) {
                        case "gà" -> "trứng";
                        case "bò" -> "sữa";
                        case "heo" -> "thịt";
                        case "cừu" -> "len";
                        default -> "không xác định";
                    };
                    if (!sanPham.equals("không xác định")) {
                        for (int j = 0; j < quantity; j++) {
                            if (!isInventoryFull(1)) {
                                capNhatKhoHang(sanPham, 1);
                                typeEffect("\u001B[32mĐã thu 1 " + sanPham + " từ " + animalName + "!\u001B[0m", 5);
                                totalHarvestedAnimalProducts.merge(sanPham, 1, Integer::sum);
                                hasCollected = true;
                                hasAnimalsToCollect = true;

                                if (random.nextDouble() < 0.0845) {
                                    if (!isInventoryFull(1)) {
                                        capNhatKhoHang("ván gỗ", 1);
                                        typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Ván Gỗ!\u001B[0m", 5);
                                    }
                                }
                                if (random.nextDouble() < 0.01535) {
                                    if (!isInventoryFull(1)) {
                                        capNhatKhoHang("đinh", 1);
                                        typeEffect("\u001B[33m>\u001B[36m Nhặt được \u001B[33m1\u001B[36m Đinh!\u001B[0m", 5);
                                    }
                                }
                            } else {
                                typeEffect("\u001B[31mKho đã đầy! Không thể thu " + sanPham + " từ " + animalName + "!\u001B[0m", 5);
                                break;
                            }
                        }
                        if (newAnimals.length() > 0) newAnimals.append("; ");
                        newAnimals.append(animalName).append(": ").append(quantity).append(" (đang đói)");
                        animalTimers.remove(animalName + ": " + quantityStr);
                    }
                } else {
                    if (newAnimals.length() > 0) newAnimals.append("; ");
                    newAnimals.append(entry);
                    if (completionTime != null && currentTime < completionTime) {
                        long timeLeft = completionTime - currentTime;
                        typeEffect("\u001B[33m" + animalName + " chưa sẵn sàng! Còn " + timeLeft + " giây.\u001B[0m", 5);
                    }
                }
            }
            farm.setAnimals(newAnimals.length() > 0 ? newAnimals.toString() : "không có");
            db.saveFarm(currentPlayer.getId(), farm.getLand(), farm.getAnimals());
            db.saveCropTimers(currentPlayer.getId(), "animal", animalTimers);
        }
        if (!hasAnimalsToCollect) {
            typeEffect("\u001B[33mVật nuôi chưa có sản phẩm nào để thu thập\u001B[0m", 5);
        }

        // Thu hoạch từ máy móc
        typeEffect("\u001B[34m>> Máy móc:\u001B[0m", 5);
        boolean hasMachinesToCollect = false;
        for (Machine machine : machines) {
            String machineType = machine.getMachineType();
            String timerKey = machineTimers.keySet().stream()
                    .filter(key -> key.startsWith(machineType + ":"))
                    .findFirst().orElse(null);

            String statusCleaned = machine.getStatus().replaceAll("\\u001B\\[[;\\d]*m", "").trim();
            if (timerKey != null && (statusCleaned.equals("đã sẵn sàng") || currentTime >= machineTimers.get(timerKey))) {
                String productInfo = machineProducts.get(machineType);
                if (productInfo == null) {
                    typeEffect("\u001B[31mKhông tìm thấy thông tin sản phẩm cho " + machineType + "!\u001B[0m", 5);
                    machine.setStatus("rảnh");
                    db.saveMachine(currentPlayer.getId(), machineType, machine.getLevel(), machine.getProductionSlots(), machine.getStatus());
                    continue;
                }

                String[] parts = productInfo.split(":");
                String product = parts[0];
                int quantity = Integer.parseInt(parts[1]);
                int originalSlots = parts.length > 2 ? Integer.parseInt(parts[2]) : machine.getLevel() * 3;

                if (!isInventoryFull(quantity)) {
                    capNhatKhoHang(product, quantity);
                    typeEffect("\u001B[32mĐã thu " + quantity + " " + product + " từ " + machineType + "!\u001B[0m", 5);
                    checkTaskCompletion("Chế biến " + quantity + " " + product);

                    machine.setProductionSlots(originalSlots);
                    machine.setStatus("rảnh");

                    machineTimers.remove(timerKey);
                    machineProducts.remove(machineType);
                    db.saveMachine(currentPlayer.getId(), machineType, machine.getLevel(), machine.getProductionSlots(), machine.getStatus());
                    db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);
                    typeEffect("\u001B[32m> \u001B[0mÔ trống đã được khôi phục: \u001B[33m" + machine.getProductionSlots() + "\u001B[0m", 5);
                    hasCollected = true;
                    hasMachinesToCollect = true;
                } else {
                    typeEffect("\u001B[31mKho đã đầy! Không thể thu " + quantity + " " + product + " từ " + machineType + "!\u001B[0m", 5);
                }
            } else if (timerKey != null) {
                long timeLeft = machineTimers.get(timerKey) - currentTime;
                typeEffect("\u001B[33m" + machineType + " chưa sẵn sàng! Còn " + timeLeft + " giây.\u001B[0m", 5);
            } else if (statusCleaned.equals("đã sẵn sàng")) {
                typeEffect("\u001B[31mKhông tìm thấy thông tin sản phẩm cho " + machineType + "!\u001B[0m", 5);
                machine.setStatus("rảnh");
                db.saveMachine(currentPlayer.getId(), machineType, machine.getLevel(), machine.getProductionSlots(), machine.getStatus());
            }
        }
        if (!hasMachinesToCollect) {
            typeEffect("\u001B[33mMáy móc chưa có sản phẩm nào để thu thập\u001B[0m", 5);
        }

        // Kiểm tra nhiệm vụ thu hoạch (giữ nguyên)
        List<String> tasksToComplete = new ArrayList<>();
        for (Task task : tasks) {
            if (task.getStatus().equals("\u001B[33mChưa hoàn thành\u001B[0m") && task.getTaskName().startsWith("Thu hoạch ")) {
                String taskName = task.getTaskName().replaceAll("\\u001B\\[[;\\d]*m", "").trim();

                String[] taskParts = new String[3];
                if (taskName.startsWith("Thu hoạch ")) {
                    taskParts[0] = "Thu hoạch";
                    String remaining = taskName.substring("Thu hoạch".length()).trim();
                    String[] remainingParts = remaining.split("\\s+", 2);
                    if (remainingParts.length == 2) {
                        taskParts[1] = remainingParts[0];
                        taskParts[2] = remainingParts[1];
                    } else {
                        continue;
                    }
                } else {
                    continue;
                }

                try {
                    int requiredQuantity = Integer.parseInt(taskParts[1]);
                    String itemName = taskParts[2];
                    int harvestedCrops = totalHarvestedCrops.getOrDefault(itemName, 0);
                    int harvestedAnimalProducts = totalHarvestedAnimalProducts.getOrDefault(itemName, 0);
                    int totalHarvested = harvestedCrops + harvestedAnimalProducts;

                    if (totalHarvested >= requiredQuantity) {
                        tasksToComplete.add(taskName);
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu số lượng không hợp lệ
                }
            }
        }

        for (String taskName : tasksToComplete) {
            checkTaskCompletion(taskName);
        }

        if (!hasCollected) {
            typeEffect("\u001B[31mKhông có gì để thu hoạch!\u001B[0m", 5);
        }

        savePlayerData();
        saveLandData();
    }
    // Hàm tính số lượng thu hoạch (có thể tùy chỉnh)
    private int getHarvestQuantity(String crop) {
        return random.nextInt(5) + 1; // Ví dụ: 1-5 sản phẩm mỗi lần thu hoạch
    }


    private void xuLyMayPhileCa(Machine selectedMachine) throws InterruptedException {
        List<Inventory> fishItems = inventory.stream()
                .filter(item -> item.getWeight() != null && item.getQuantity() > 0)
                .toList();

        if (fishItems.isEmpty()) {
            typeEffect("\u001B[31mKhông có cá trong kho để phi lê!\u001B[0m", 5);
            return;
        }

        typeEffect("\u001B[34m\n=== Cá trong kho ===\u001B[0m", 3);
        for (int i = 0; i < fishItems.size(); i++) {
            Inventory fish = fishItems.get(i);
            typeEffect((i + 1) + ". " + fish.getItemName() + " (" + fish.getQuantity() + " con, " + fish.getWeight() + " kg/con)", 3);
        }

        typeEffect("\u001B[36m>> Chọn loại cá (1-" + fishItems.size() + " hoặc tên): \u001B[0m", 3);
        String fishInput = scanner.nextLine().trim().toLowerCase();

        Inventory selectedFish = null;
        try {
            int index = Integer.parseInt(fishInput) - 1;
            if (index >= 0 && index < fishItems.size()) {
                selectedFish = fishItems.get(index);
            }
        } catch (NumberFormatException e) {
            selectedFish = fishItems.stream()
                    .filter(f -> f.getItemName().toLowerCase().equals(fishInput))
                    .findFirst().orElse(null);
        }

        if (selectedFish == null) {
            typeEffect("\u001B[31mLoại cá không hợp lệ!\u001B[0m", 5);
            return;
        }

        typeEffect("\u001B[33mNhập số lượng cá để phi lê (1-" + selectedFish.getQuantity() + "): \u001B[0m", 5);
        String fishCountInput = scanner.nextLine().trim();
        int fishCount;
        try {
            fishCount = fishCountInput.isEmpty() ? 1 : Integer.parseInt(fishCountInput);
            if (fishCount <= 0 || fishCount > selectedFish.getQuantity()) {
                typeEffect("\u001B[31mSố lượng cá không hợp lệ!\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mSố lượng cá không hợp lệ!\u001B[0m", 5);
            return;
        }

        double weight = selectedFish.getWeight();
        int filletCount = (int) Math.floor(weight);
        if (weight % 1 >= 0.5) filletCount++;
        filletCount *= fishCount;

        typeEffect("\u001B[33mSẽ tạo ra " + filletCount + " Cá phi lê từ " + fishCount + " " + selectedFish.getItemName() + " (" + weight + " kg/con). Xác nhận? (Y/N): \u001B[0m", 5);
        String confirm = scanner.nextLine().trim().toUpperCase();
        if (confirm.equals("Y")) {
            capNhatKhoHang(selectedFish.getItemName(), -fishCount);
            selectedMachine.setStatus("\u001B[33mđang hoạt động\u001B[0m");
            long endTime = System.currentTimeMillis() / 1000 + 300;
            machineTimers.put("Máy Phi Lê Cá:Cá phi lê:" + filletCount, endTime);
            machineProducts.put("Máy Phi Lê Cá", "Cá phi lê:" + filletCount);
            db.saveMachine(currentPlayer.getId(), "Máy Phi Lê Cá", selectedMachine.getLevel(), selectedMachine.getProductionSlots(), selectedMachine.getStatus());
            db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);
            typeEffect("\u001B[32mĐã bắt đầu phi lê " + fishCount + " " + selectedFish.getItemName() + "! Hoàn thành sau 300 giây.\u001B[0m", 5);
        } else {
            typeEffect("\u001B[33mĐã hủy chế biến!\u001B[0m", 5);
        }
    }


    private void xuLyCraft(String[] parts) throws InterruptedException {
        if (parts.length > 1) {
            cheBien(parts[1], 1, null);
            return;
        }

        System.out.print("\u001B[34m┌────────────────────────────────┐\u001B[0m");
        System.out.println("\u001B[34m\n│  \u001B[0m Danh Sách Sản Phẩm Chế Biến \u001B[34m │\u001B[0m");
        System.out.println("\u001B[34m└────────────────────────────────┘\u001B[0m");
        if (machines.isEmpty()) {
            typeEffect("\u001B[31mBạn chưa sở hữu máy móc nào!\u001B[0m", 5);
            return;
        }

        typeEffect("\u001B[34m\n===\u001B[33m Danh sách máy móc hiện có\u001B[34m ===\u001B[0m", 3);
        for (int i = 0; i < machines.size(); i++) {
            Machine machine = machines.get(i);
            typeEffect((i + 1) + ". " + machine.getMachineType() + " (" + machine.getStatus() + ", Level " + machine.getLevel() + ", Ô: " + machine.getProductionSlots() + ")", 3);
        }

        typeEffect("\u001B[36m>> Chọn máy (1-" + machines.size() + " hoặc tên): \u001B[0m", 3);
        String inputMachine = scanner.nextLine().trim();

        Machine selectedMachine = null;
        try {
            int index = Integer.parseInt(inputMachine) - 1;
            if (index >= 0 && index < machines.size()) {
                selectedMachine = machines.get(index);
            }
        } catch (NumberFormatException e) {
            selectedMachine = machines.stream()
                    .filter(m -> m.getMachineType().toLowerCase().equals(inputMachine.toLowerCase()))
                    .findFirst().orElse(null);
        }

        if (selectedMachine == null) {
            typeEffect("\u001B[31mMáy không hợp lệ!\u001B[0m", 5);
            return;
        }

        String statusCleaned = selectedMachine.getStatus().replaceAll("\\u001B\\[[;\\d]*m", "").trim();
        if (!statusCleaned.equals("rảnh")) {
            typeEffect("\u001B[31m" + selectedMachine.getMachineType() + " đang hoạt động hoặc đã sẵn sàng! Thu hoạch trước khi chế biến thêm.\u001B[0m", 5);
            return;
        }

        String selectedMachineType = selectedMachine.getMachineType();
        String sanPham = null;
        int quantity = 0;

        if (selectedMachineType.equalsIgnoreCase("Máy Phi Lê Cá")) {
            List<Inventory> fishItems = inventory.stream()
                    .filter(item -> item.getWeight() != null && item.getQuantity() > 0)
                    .toList();

            if (fishItems.isEmpty()) {
                typeEffect("\u001B[31mKhông có cá trong kho để phi lê!\u001B[0m", 5);
                return;
            }

            typeEffect("\u001B[34m\n=== Cá trong kho ===\u001B[0m", 3);
            for (int i = 0; i < fishItems.size(); i++) {
                Inventory fish = fishItems.get(i);
                typeEffect((i + 1) + ". " + fish.getItemName() + " (" + fish.getQuantity() + " con, " + fish.getWeight() + " kg/con)", 3);
            }

            typeEffect("\u001B[36m>> Chọn loại cá (1-" + fishItems.size() + " hoặc tên): \u001B[0m", 3);
            String fishInput = scanner.nextLine().trim().toLowerCase();

            Inventory selectedFish = null;
            try {
                int index = Integer.parseInt(fishInput) - 1;
                if (index >= 0 && index < fishItems.size()) {
                    selectedFish = fishItems.get(index);
                }
            } catch (NumberFormatException e) {
                selectedFish = fishItems.stream()
                        .filter(f -> f.getItemName().toLowerCase().equals(fishInput))
                        .findFirst().orElse(null);
            }

            if (selectedFish == null) {
                typeEffect("\u001B[31mLoại cá không hợp lệ!\u001B[0m", 5);
                return;
            }

            int maxFishCraftable = Math.min(selectedFish.getQuantity(), selectedMachine.getProductionSlots());
            typeEffect("\u001B[33mNhập số lượng cá để phi lê (1-" + maxFishCraftable + "): \u001B[0m", 5);
            String fishCountInput = scanner.nextLine().trim();
            int fishCount;
            try {
                fishCount = fishCountInput.isEmpty() ? 1 : Integer.parseInt(fishCountInput);
                if (fishCount <= 0 || fishCount > maxFishCraftable) {
                    typeEffect("\u001B[31mSố lượng cá không hợp lệ! Tối đa " + maxFishCraftable + " dựa trên ô sản xuất.\u001B[0m", 5);
                    return;
                }
            } catch (NumberFormatException e) {
                typeEffect("\u001B[31mSố lượng cá không hợp lệ!\u001B[0m", 5);
                return;
            }

            double weight = selectedFish.getWeight();
            int filletCount = (int) Math.floor(weight);
            if (weight % 1 >= 0.5) filletCount++;
            filletCount *= fishCount;

            typeEffect("\u001B[33mSẽ tạo ra " + filletCount + " Cá phi lê từ " + fishCount + " " + selectedFish.getItemName() + " (" + weight + " kg/con). Xác nhận? (Y/N): \u001B[0m", 5);
            String confirm = scanner.nextLine().trim().toUpperCase();
            if (confirm.equals("Y")) {
                capNhatKhoHang(selectedFish.getItemName(), -fishCount);
                selectedMachine.setStatus("\u001B[33mđang hoạt động\u001B[0m");
                long endTime = System.currentTimeMillis() / 1000 + 300;
                machineTimers.put(selectedMachineType + ":Cá phi lê:" + filletCount, endTime);
                machineProducts.put(selectedMachineType, "Cá phi lê:" + filletCount);
                db.saveMachine(currentPlayer.getId(), selectedMachineType, selectedMachine.getLevel(), selectedMachine.getProductionSlots(), selectedMachine.getStatus());
                db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);
                typeEffect("\u001B[32mĐã bắt đầu phi lê " + fishCount + " " + selectedFish.getItemName() + "! Hoàn thành sau 300 giây.\u001B[0m", 5);
                sanPham = "Cá phi lê";
                quantity = filletCount;
            } else {
                typeEffect("\u001B[33mĐã hủy chế biến!\u001B[0m", 5);
                return;
            }
        } else {
            typeEffect("\u001B[34m\n├─────\u001B[36m Sản phẩm của \u001B[33m" + selectedMachine.getMachineType() + " \u001B[34m─────┤\u001B[0m", 3);

            Map<String, String[]> productRequirements = new HashMap<>() {{
                put("Bánh mì", new String[]{"1 bột mì", "Lò Bánh Mì", "240"});
                put("Bỏng ngô", new String[]{"2 bắp", "Nồi Bỏng Ngô", "1440"});
                put("Thức ăn cho gà", new String[]{"1 lúa mì + 1 bắp", "Máy làm Thức ăn", "180"});
                put("Thức ăn cho bò", new String[]{"2 đậu nành + 1 bắp", "Máy làm Thức ăn", "240"});
                put("Thức ăn cho lợn", new String[]{"2 cà rốt + 1 bí ngô", "Máy làm Thức ăn", "300"});
                put("Thức ăn cho cừu", new String[]{"2 lúa mì + 1 cải xanh", "Máy làm Thức ăn", "210"});
                put("Kem", new String[]{"1 sữa", "Nhà Máy Sữa", "2880"});
                put("Phô mai", new String[]{"2 sữa", "Nhà Máy Sữa", "3600"});
                put("Đường", new String[]{"1 mía", "Nhà Máy Đường", "3840"});
                put("Sirô", new String[]{"2 mía", "Nhà Máy Đường", "4800"});
                put("Áo len", new String[]{"2 len", "Máy May", "1200"});
                put("Vải cotton", new String[]{"3 bông", "Máy May", "1800"});
                put("Bánh ngọt", new String[]{"2 bột mì + 1 trứng + 1 đường", "Máy Làm Bánh Ngọt", "720"});
                put("Bánh dâu", new String[]{"2 dâu tây + 1 đường", "Máy Làm Bánh Ngọt", "900"});
                put("Phô mai dê", new String[]{"2 sữa dê", "Máy Làm Phô Mai", "880"});
                put("Nước ép táo", new String[]{"3 táo", "Máy Làm Nước Ép", "1040"});
                put("Nước ép dâu", new String[]{"3 dâu tây", "Máy Làm Nước Ép", "1200"});
                put("Mứt dâu", new String[]{"2 dâu tây + 1 đường", "Máy Làm Mứt", "1120"});
                put("Mứt táo", new String[]{"2 táo + 1 đường", "Máy Làm Mứt", "1200"});
                put("Kem tuyết dâu", new String[]{"1 sữa + 1 dâu tây", "Máy Làm Kem Tuyết", "680"});
                put("Bánh nướng", new String[]{"3 bột mì + 1 trứng", "Lò Nướng Bánh", "760"});
                put("Sữa chua dâu", new String[]{"1 sữa + 1 dâu tây", "Máy Làm Sữa Chua", "960"});
                put("Bánh quy", new String[]{"2 bột mì + 1 trứng + 1 bơ", "Máy Làm Bánh Quy", "560"});
                put("Nước sốt cà chua", new String[]{"2 cà chua", "Máy Làm Nước Sốt", "800"});
                put("Kẹo caramel", new String[]{"1 đường + 1 sữa", "Máy Làm Kẹo", "1000"});
                put("Bánh pizza", new String[]{"3 bột mì + 1 phô mai + 1 cà chua", "Máy Làm Bánh Pizza", "1280"});
                put("Vải dệt", new String[]{"3 len", "Máy Dệt Vải", "1440"});
                put("Socola", new String[]{"2 cacao + 1 đường", "Máy Làm Socola", "1080"});
                put("Trà xanh", new String[]{"2 lá trà", "Máy Làm Trà", "920"});
                put("bột mì", new String[]{"2 lúa mì", "Máy Cối Xay Gió", "300"}); //300
                put("cá phi lê", new String[]{"1 cá", "Máy phi lê cá", "600"}); //300
            }};

            List<String[]> products = productRequirements.entrySet().stream()
                    .filter(entry -> entry.getValue()[1].equalsIgnoreCase(selectedMachineType))
                    .map(entry -> new String[]{entry.getKey(), entry.getValue()[0], entry.getValue()[2] + " giây"})
                    .collect(Collectors.toList());

            if (products.isEmpty()) {
                typeEffect("\u001B[31mMáy này không có sản phẩm để chế biến!\u001B[0m", 5);
                return;
            }

            String topBorder = "┌─────┬──────────────────────┬────────────────────────────────────────┬────────────────┐";
            String headerRow = "│ STT │ Sản phẩm             │ Nguyên liệu                            │ Thời gian      │";
            String middleBorder = "├─────┼──────────────────────┼────────────────────────────────────────┼────────────────┤";
            String bottomBorder = "└─────┴──────────────────────┴────────────────────────────────────────┴────────────────┘";

            System.out.println("\u001B[34m" + topBorder + "\u001B[0m");
            System.out.println("\u001B[33m" + headerRow + "\u001B[0m");
            System.out.println("\u001B[34m" + middleBorder + "\u001B[0m");

            for (int i = 0; i < products.size(); i++) {
                String[] p = products.get(i);
                String row = String.format("│ %-3d │ %-20s │ %-38s │ %-14s │", (i + 1), p[0], p[1], p[2]);
                System.out.println(row);
            }

            System.out.println("\u001B[34m" + bottomBorder + "\u001B[0m");

            typeEffect("\u001B[36m>> Chọn sản phẩm (1-" + products.size() + " hoặc tên): \u001B[0m", 3);
            String inputProduct = scanner.nextLine().trim().toLowerCase();

            try {
                int index = Integer.parseInt(inputProduct) - 1;
                if (index >= 0 && index < products.size()) {
                    sanPham = products.get(index)[0];
                }
            } catch (NumberFormatException e) {
                sanPham = products.stream()
                        .filter(p -> p[0].toLowerCase().equals(inputProduct))
                        .map(p -> p[0])
                        .findFirst()
                        .orElse(null);
            }

            if (sanPham == null) {
                typeEffect("\u001B[31mSản phẩm không hợp lệ!\u001B[0m", 5);
                return;
            }

            String[] requirements = productRequirements.get(sanPham);
            String materials = requirements[0];
            Map<String, Integer> requiredItems = new HashMap<>();
            for (String item : materials.split(" \\+ ")) {
                String[] itemParts = item.split(" ", 2);
                int qty = Integer.parseInt(itemParts[0]);
                String name = itemParts[1];
                requiredItems.put(name, qty);
            }

            int maxCraftable = selectedMachine.getProductionSlots();
            StringBuilder materialInfo = new StringBuilder("> Bạn có ");
            boolean firstMaterial = true;

            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String itemName = entry.getKey();
                int requiredQtyPerUnit = entry.getValue();
                int available = inventory.stream()
                        .filter(item -> item.getItemName().equals(itemName))
                        .mapToInt(Inventory::getQuantity)
                        .sum();
                int craftable = available / requiredQtyPerUnit;
                maxCraftable = Math.min(maxCraftable, craftable);

                if (!firstMaterial) {
                    materialInfo.append(", ");
                }
                materialInfo.append(available).append(" ").append(itemName);
                firstMaterial = false;
            }

            materialInfo.append(" có thể chế tạo ").append(maxCraftable).append(" ").append(sanPham);
            typeEffect("\u001B[33m" + materialInfo.toString() + "\u001B[0m", 5);

            typeEffect("\u001B[33mNhập số lượng (tối đa " + maxCraftable + ", mặc định 1): \u001B[0m", 5);
            String soLuongInput = scanner.nextLine().trim();
            int soLuong;
            try {
                soLuong = soLuongInput.isEmpty() ? 1 : Integer.parseInt(soLuongInput);
                if (soLuong <= 0) {
                    typeEffect("\u001B[31mSố lượng phải lớn hơn 0!\u001B[0m", 5);
                    return;
                }
                if (soLuong > maxCraftable) {
                    typeEffect("\u001B[31mSố lượng vượt quá giới hạn! Tối đa là " + maxCraftable + " dựa trên ô sản xuất.\u001B[0m", 5);
                    return;
                }
            } catch (NumberFormatException e) {
                typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
                return;
            }

            // Chỉ gọi cheBien, không trừ ô ở đây nữa
            typeEffect("\u001B[36mBạn muốn chế biến " + soLuong + " " + sanPham + " không? (Y/N): \u001B[0m", 5);
            String confirm = scanner.nextLine().trim().toUpperCase();
            if (confirm.equals("Y")) {
                cheBien(sanPham, soLuong, selectedMachine); // Truyền đúng soLuong (3)
                if (requirements != null && selectedMachine.getStatus().equals("\u001B[33mđang hoạt động\u001B[0m")) {
                    machineProducts.put(selectedMachineType, sanPham + ":" + soLuong);
                    quantity = soLuong;
                }
            } else {
                typeEffect("\u001B[33mĐã hủy chế biến!\u001B[0m", 5);
            }

            if (sanPham != null && quantity > 0) {
                String taskName = "Chế biến " + quantity + " " + sanPham;
                tasks.stream()
                        .filter(t -> t.getTaskName().equals(taskName) && t.getStatus().equals("\u001B[33mChưa hoàn thành\u001B[0m"))
                        .findFirst()
                        .ifPresent(task -> {
                            task.setStatus("completed");
                            int reward = task.getReward();
                            currentPlayer.addCoins(reward);
                            try {
                                typeEffect("\u001B[36m>> \u001B[32mNhiệm vụ \u001B[0m'" + taskName + "' \u001B[32mHoàn Thành\u001B[0m! Nhận \u001B[33m" + reward + "\u001B[0m xu!\u001B[0m", 5);
                                db.saveTasks(currentPlayer.getId(), tasks);
                                db.savePlayer(currentPlayer.getId(), currentPlayer.getCoins(), 0, 0, 0);
                                updateTasksAndOrders();
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                System.err.println("Lỗi khi hoàn thành nhiệm vụ: " + e.getMessage());
                            }
                        });
            }
        }
    }

    private void cheBien(String sanPham, int soLuong, Machine selectedMachine) throws InterruptedException {
        Map<String, String[]> productRequirements = new HashMap<>() {{
            put("Bánh mì", new String[]{"1 bột mì", "Lò Bánh Mì", "240"});
            put("Bỏng ngô", new String[]{"2 bắp", "Nồi Bỏng Ngô", "1440"});
            put("Thức ăn cho gà", new String[]{"1 lúa mì + 1 bắp", "Máy làm Thức ăn", "180"});
            put("Thức ăn cho bò", new String[]{"2 đậu nành + 1 bắp", "Máy làm Thức ăn", "240"});
            put("Thức ăn cho lợn", new String[]{"2 cà rốt + 1 bí ngô", "Máy làm Thức ăn", "300"});
            put("Thức ăn cho cừu", new String[]{"2 lúa mì + 1 cải xanh", "Máy làm Thức ăn", "210"});
            put("Kem", new String[]{"1 sữa", "Nhà Máy Sữa", "2880"});
            put("Phô mai", new String[]{"2 sữa", "Nhà Máy Sữa", "3600"});
            put("Đường", new String[]{"1 mía", "Nhà Máy Đường", "3840"});
            put("Sirô", new String[]{"2 mía", "Nhà Máy Đường", "4800"});
            put("Áo len", new String[]{"2 len", "Máy May", "1200"});
            put("Vải cotton", new String[]{"3 bông", "Máy May", "1800"});
            put("Bánh ngọt", new String[]{"2 bột mì + 1 trứng + 1 đường", "Máy Làm Bánh Ngọt", "720"});
            put("Bánh dâu", new String[]{"2 dâu tây + 1 đường", "Máy Làm Bánh Ngọt", "900"});
            put("Phô mai dê", new String[]{"2 sữa dê", "Máy Làm Phô Mai", "880"});
            put("Nước ép táo", new String[]{"3 táo", "Máy Làm Nước Ép", "1040"});
            put("Nước ép dâu", new String[]{"3 dâu tây", "Máy Làm Nước Ép", "1200"});
            put("Mứt dâu", new String[]{"2 dâu tây + 1 đường", "Máy Làm Mứt", "1120"});
            put("Mứt táo", new String[]{"2 táo + 1 đường", "Máy Làm Mứt", "1200"});
            put("Kem tuyết dâu", new String[]{"1 sữa + 1 dâu tây", "Máy Làm Kem Tuyết", "680"});
            put("Bánh nướng", new String[]{"3 bột mì + 1 trứng", "Lò Nướng Bánh", "760"});
            put("Sữa chua dâu", new String[]{"1 sữa + 1 dâu tây", "Máy Làm Sữa Chua", "960"});
            put("Bánh quy", new String[]{"2 bột mì + 1 trứng + 1 bơ", "Máy Làm Bánh Quy", "560"});
            put("Nước sốt cà chua", new String[]{"2 cà chua", "Máy Làm Nước Sốt", "800"});
            put("Kẹo caramel", new String[]{"1 đường + 1 sữa", "Máy Làm Kẹo", "1000"});
            put("Bánh pizza", new String[]{"3 bột mì + 1 phô mai + 1 cà chua", "Máy Làm Bánh Pizza", "1280"});
            put("Vải dệt", new String[]{"3 len", "Máy Dệt Vải", "1440"});
            put("Socola", new String[]{"2 cacao + 1 đường", "Máy Làm Socola", "1080"});
            put("Trà xanh", new String[]{"2 lá trà", "Máy Làm Trà", "920"});
            put("bột mì", new String[]{"2 lúa mì", "Máy Cối Xay Gió", "300"}); //300
        }};

        String[] requirements = productRequirements.get(sanPham);
        if (requirements == null) {
            typeEffect("\u001B[31mSản phẩm không tồn tại!\u001B[0m", 5);
            return;
        }

        String materials = requirements[0];
        String requiredMachine = requirements[1];
        int time = Integer.parseInt(requirements[2]);

        if (selectedMachine == null || !selectedMachine.getMachineType().toLowerCase().equals(requiredMachine.toLowerCase())) {
            typeEffect("\u001B[31mChưa có " + requiredMachine + " hoặc đang bận!\u001B[0m", 5);
            return;
        }

        int currentSlots = selectedMachine.getProductionSlots();
        if (currentSlots < 0) {
            currentSlots = selectedMachine.getLevel() * 3; // Giả định mỗi level có 3 ô
            selectedMachine.setProductionSlots(currentSlots);
        }
        if (soLuong > currentSlots) {
            typeEffect("\u001B[31mKhông đủ ô trống! Cần " + soLuong + " ô, nhưng chỉ còn " + currentSlots + " ô.\u001B[0m", 5);
            return;
        }

        Map<String, Integer> requiredItems = new HashMap<>();
        for (String item : materials.split(" \\+ ")) {
            String[] itemParts = item.split(" ", 2);
            int qty = Integer.parseInt(itemParts[0]);
            String name = itemParts[1];
            requiredItems.put(name, qty * soLuong);
        }

        for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
            String itemName = entry.getKey();
            int requiredQty = entry.getValue();
            int available = inventory.stream()
                    .filter(item -> item.getItemName().equals(itemName))
                    .mapToInt(Inventory::getQuantity)
                    .sum();
            if (available < requiredQty) {
                typeEffect("\u001B[31mKhông đủ " + itemName + "! Cần: " + requiredQty + ", Có: " + available + "\u001B[0m", 5);
                return;
            }
        }

        typeEffect(">>\u001B[36m Xác Nhận chế biến \u001B[33m" + soLuong + " " + sanPham + "\u001B[36m ? (Y/N): \u001B[0m", 5);
        String confirm = scanner.nextLine().trim().toUpperCase();
        if (confirm.equals("Y")) {
            for (Map.Entry<String, Integer> entry : requiredItems.entrySet()) {
                String itemName = entry.getKey();
                int needed = entry.getValue();
                capNhatKhoHang(itemName, -needed);
            }

            int originalSlots = selectedMachine.getProductionSlots();
            selectedMachine.setProductionSlots(originalSlots - soLuong);
            selectedMachine.setStatus("\u001B[33mđang hoạt động\u001B[0m");

            long endTime = System.currentTimeMillis() / 1000 + time;
            String machineType = selectedMachine.getMachineType();
            String timerKey = machineType + ":" + sanPham + ":" + soLuong;
            String productInfo = sanPham + ":" + soLuong + ":" + originalSlots;

            machineTimers.put(timerKey, endTime);
            machineProducts.put(machineType, productInfo);

            // Lưu vào cơ sở dữ liệu
            db.saveMachine(currentPlayer.getId(), machineType, selectedMachine.getLevel(), selectedMachine.getProductionSlots(), selectedMachine.getStatus());
            db.saveCropTimers(currentPlayer.getId(), "machine", machineTimers);
            db.saveMachineProducts(currentPlayer.getId(), machineProducts);

            typeEffect("\u001B[32mĐã bắt đầu chế biến " + soLuong + " " + sanPham + " bằng " + machineType + "! Ô còn lại: " + selectedMachine.getProductionSlots() + "\u001B[0m", 5);
        } else {
            typeEffect("\u001B[33mĐã hủy chế biến!\u001B[0m", 5);
        }
    }

    private void xuLySell(String[] parts) throws InterruptedException {
        if (parts.length > 2) {
            banVatPham(parts[1], Integer.parseInt(parts[2]));
            return;
        }

        // Hiển thị kho hàng theo nhóm, chỉ hiển thị vật phẩm có số lượng > 0
        typeEffect("\u001B[34m\n=== Kho hàng ===\u001B[0m", 5);
        List<Inventory> nonZeroInventory = inventory.stream()
                .filter(item -> item.getQuantity() > 0)
                .toList();

        if (nonZeroInventory.isEmpty()) {
            typeEffect(" - Kho trống!", 5);
            return;
        }

        // Tạo danh sách tổng theo thứ tự hiển thị
        List<Inventory> displayedItems = new ArrayList<>();
        Map<Integer, List<Inventory>> categoryOptions = new HashMap<>(); // Lưu danh mục cho "Bán Tất Cả"
        int stt = 1; // Biến đếm STT liên tục

        // Sản phẩm của động vật
        List<Inventory> animalProducts = nonZeroInventory.stream()
                .filter(item -> animalHarvestTimes.containsKey(item.getItemName().toLowerCase()) ||
                        item.getItemName().equalsIgnoreCase("trứng") ||
                        item.getItemName().equalsIgnoreCase("sữa") ||
                        item.getItemName().equalsIgnoreCase("thịt") ||
                        item.getItemName().equalsIgnoreCase("len") ||
                        item.getItemName().toLowerCase().contains("thức ăn cho"))
                .toList();
        if (!animalProducts.isEmpty()) {
            typeEffect("\u001B[34m───────────── Sản phẩm của động vật ─────────────\u001B[0m", 5);
            for (Inventory item : animalProducts) {
                displayedItems.add(item);
                typeEffect(" " + stt + ". " + item.getItemName() + ": \u001B[33m" + item.getQuantity() + "\u001B[0m", 5);
                stt++;
            }
        }

        // Cây trồng
        List<Inventory> crops = nonZeroInventory.stream()
                .filter(item -> cropGrowthTimes.containsKey(item.getItemName().toLowerCase()))
                .toList();
        if (!crops.isEmpty()) {
            typeEffect("\u001B[32m───────────── Cây trồng ─────────────\u001B[0m", 5);
            for (Inventory item : crops) {
                displayedItems.add(item);
                typeEffect(" " + stt + ". " + item.getItemName() + ": \u001B[33m" + item.getQuantity() + " \u001B[0m", 5);
                stt++;
            }
        }

        // Sản phẩm nhà máy
        List<Inventory> factoryProducts = nonZeroInventory.stream()
                .filter(item -> machineProcessingTimes.keySet().stream()
                        .anyMatch(machine -> item.getItemName().toLowerCase().equals(machine.toLowerCase()) ||
                                item.getItemName().toLowerCase().equals(getProductFromMachine(machine).toLowerCase())) ||
                        item.getItemName().equalsIgnoreCase("phân bón"))
                .toList();
        if (!factoryProducts.isEmpty()) {
            typeEffect("\u001B[33m───────────── Sản phẩm nhà máy ─────────────\u001B[0m", 5);
            for (Inventory item : factoryProducts) {
                displayedItems.add(item);
                typeEffect(" " + stt + ". " + item.getItemName() + ": \u001B[33m" + item.getQuantity() + " \u001B[0m", 5);
                stt++;
            }
        }

        // Cá
        List<Inventory> fishItems = nonZeroInventory.stream()
                .filter(item -> item.getItemName().toLowerCase().contains("cá") &&
                        !item.getItemName().toLowerCase().contains("cần câu cá"))
                .toList();
        if (!fishItems.isEmpty()) {
            typeEffect("\u001B[36m───────────── Cá ─────────────\u001B[0m", 5);
            for (Inventory item : fishItems) {
                displayedItems.add(item);
                int price = getItemPrice(item.getItemName());
                String displayName = item.getItemName();
                if (item.getWeight() != null) {
                    displayName += " (" + item.getWeight() + " kg)";
                }
                typeEffect(" " + stt + ". " + displayName + ": \u001B[33m" + item.getQuantity() + " \u001B[0m(Giá: " + price + " xu)", 5);
                stt++;
            }
        }

        // Danh mục "Bán Tất Cả (Theo danh mục)"
        typeEffect("\u001B[31m───────────── Bán Tất Cả (Theo danh mục) ─────────────\u001B[0m", 5);
        int sellAllStt = stt; // Ghi nhớ STT bắt đầu của "Bán Tất Cả"
        if (!animalProducts.isEmpty()) {
            categoryOptions.put(stt, animalProducts);
            typeEffect(" " + stt + ". Sản phẩm của động vật", 5);
            stt++;
        }
        if (!crops.isEmpty()) {
            categoryOptions.put(stt, crops);
            typeEffect(" " + stt + ". Cây trồng", 5);
            stt++;
        }
        if (!factoryProducts.isEmpty()) {
            categoryOptions.put(stt, factoryProducts);
            typeEffect(" " + stt + ". Sản phẩm nhà máy", 5);
            stt++;
        }
        if (!fishItems.isEmpty()) {
            categoryOptions.put(stt, fishItems);
            typeEffect(" " + stt + ". Cá", 5);
            stt++;
        }
        categoryOptions.put(stt, displayedItems); // Tất cả sản phẩm
        typeEffect(" " + stt + ". Bán tất cả sản phẩm ở các danh mục", 5);
        stt++;

        // Nhập số thứ tự hoặc tên vật phẩm
        int maxOption = stt - 1; // Tổng số tùy chọn
        typeEffect("\u001B[36m>> Chọn vật phẩm (1-" + maxOption + " hoặc tên, cách nhau bằng dấu phẩy để chọn nhiều, nhấn Enter để bỏ qua): \u001B[0m", 5);
        String input = scanner.nextLine().trim().toLowerCase();

        if (input.isEmpty()) {
            return; // Nhấn Enter để bỏ qua
        }

        // Xử lý tùy chọn
        String[] selections = input.split("\\s*,\\s*"); // Tách các lựa chọn bằng dấu phẩy
        List<Inventory> itemsToSell = new ArrayList<>();
        Map<Inventory, Integer> quantitiesToSell = new HashMap<>(); // Lưu số lượng cho từng vật phẩm
        boolean isSellAll = false;

        for (String selection : selections) {
            try {
                int index = Integer.parseInt(selection);
                if (index > 0 && index <= displayedItems.size()) {
                    Inventory selectedItem = displayedItems.get(index - 1);
                    if (!itemsToSell.contains(selectedItem)) { // Tránh trùng lặp
                        itemsToSell.add(selectedItem);
                    }
                } else if (index >= sellAllStt && categoryOptions.containsKey(index)) {
                    banTatCaDanhMuc(categoryOptions.get(index), displayedItems, crops, factoryProducts, fishItems, animalProducts);
                    isSellAll = true;
                    break; // Nếu chọn "Bán tất cả", không xử lý các lựa chọn khác
                } else {
                    typeEffect("\u001B[31mTùy chọn " + selection + " không hợp lệ!\u001B[0m", 5);
                    return;
                }
            } catch (NumberFormatException e) {
                // Xử lý nhập tên vật phẩm
                for (Inventory item : displayedItems) {
                    if (item.getItemName().toLowerCase().equals(selection)) {
                        if (!itemsToSell.contains(item)) { // Tránh trùng lặp
                            itemsToSell.add(item);
                        }
                        break;
                    }
                }
            }
        }

        if (!isSellAll && !itemsToSell.isEmpty()) {
            // Hỏi số lượng cho từng vật phẩm
            for (Inventory item : itemsToSell) {
                String displayName = item.getItemName();
                if (item.getWeight() != null) {
                    displayName += " (" + item.getWeight() + " kg)";
                }
                System.out.print("\u001B[33mNhập số lượng " + displayName + " (tối đa " + item.getQuantity() + "): \u001B[0m");
                String quantityInput = scanner.nextLine().trim();
                int quantity;
                try {
                    quantity = quantityInput.isEmpty() ? item.getQuantity() : Integer.parseInt(quantityInput);
                    if (quantity <= 0 || quantity > item.getQuantity()) {
                        typeEffect("\u001B[31mSố lượng không hợp lệ cho " + displayName + "! Phải từ 1 đến " + item.getQuantity() + ".\u001B[0m", 5);
                        return;
                    }
                } catch (NumberFormatException e) {
                    typeEffect("\u001B[31mSố lượng không hợp lệ cho " + displayName + "!\u001B[0m", 5);
                    return;
                }
                quantitiesToSell.put(item, quantity);
            }

            // Tính tổng giá và hiển thị xác nhận
            int totalPrice = 0;
            StringBuilder summary = new StringBuilder();
            for (Inventory item : itemsToSell) {
                int quantity = quantitiesToSell.get(item);
                int pricePerUnit = getItemPrice(item.getItemName());
                int itemTotalPrice = pricePerUnit * quantity;
                totalPrice += itemTotalPrice;
                String displayName = item.getItemName();
                if (item.getWeight() != null) {
                    displayName += " (" + item.getWeight() + " kg)";
                }
                summary.append(" - \u001B[33m").append(quantity).append("\u001B[0m ").append(displayName).append(": \u001B[33m").append(itemTotalPrice).append("\u001B[0m xu\n");
            }

            typeEffect("\u001B[34m\n=== Xác nhận bán các vật phẩm ===\u001B[0m", 5);
            typeEffect(summary.toString(), 5);
            typeEffect("Tổng cộng: \u001B[33m" + totalPrice + " xu\u001B[0m", 5);
            typeEffect("\u001B[36m>> \u001B[0mBạn có chắc chắn muốn bán không? \u001B[33m(Y/N): \u001B[0m", 5);

            String confirm = scanner.nextLine().trim().toUpperCase();
            if (confirm.equals("Y")) {
                for (Inventory item : itemsToSell) {
                    banVatPham(item.getItemName(), quantitiesToSell.get(item));
                }
                typeEffect("\u001B[32m> \u001B[36mĐã bán các vật phẩm và nhận được \u001B[33m" + totalPrice + "\u001B[36m xu!\u001B[0m", 5);
            } else {
                typeEffect("\u001B[33mĐã hủy bán!\u001B[0m", 5);
            }
        } else if (!isSellAll && itemsToSell.isEmpty()) {
            typeEffect("\u001B[31mKhông tìm thấy vật phẩm nào phù hợp!\u001B[0m", 5);
        }
    }

    // Phương thức bán tất cả trong danh mục
    private void banTatCaDanhMuc(List<Inventory> items, List<Inventory> displayedItems, List<Inventory> crops,
                                 List<Inventory> factoryProducts, List<Inventory> fishItems, List<Inventory> animalProducts) throws InterruptedException {
        int totalPrice = 0;
        StringBuilder summary = new StringBuilder();
        for (Inventory item : items) {
            int pricePerUnit = getItemPrice(item.getItemName());
            int quantity = item.getQuantity();
            int itemTotalPrice = pricePerUnit * quantity;
            totalPrice += itemTotalPrice;
            String displayName = item.getItemName();
            if (item.getWeight() != null) {
                displayName += " (" + item.getWeight() + " kg)";
            }
            summary.append(" - \u001B[33m").append(quantity).append("\u001B[0m ").append(displayName).append(": \u001B[33m").append(itemTotalPrice).append("\u001B[0m xu\n");
        }

        String categoryName = items == displayedItems ? "tất cả sản phẩm ở các danh mục" :
                (items == crops ? "cây trồng" :
                        (items == factoryProducts ? "sản phẩm nhà máy" :
                                (items == fishItems ? "cá" : "sản phẩm của động vật")));
        typeEffect("\u001B[34m\n=== Bán tất cả " + categoryName + " ===\u001B[0m", 5);
        typeEffect("Tổng cộng: \u001B[33m" + totalPrice + " xu\u001B[0m", 5);
        typeEffect(summary.toString(), 5);
        typeEffect("\u001B[36m>> \u001B[0mBạn có chắc chắn muốn bán không? \u001B[33m(Y/N): \u001B[0m", 5);

        String confirm = scanner.nextLine().trim().toUpperCase();
        if (confirm.equals("Y")) {
            for (Inventory item : items) {
                banVatPham(item.getItemName(), item.getQuantity());
            }
            typeEffect("\u001B[32m> \u001B[36mĐã bán tất cả " + categoryName + " và nhận được \u001B[33m" + totalPrice + "\u001B[36m xu!\u001B[0m", 5);
        } else {
            typeEffect("\u001B[33mĐã hủy bán!\u001B[0m", 5);
        }
    }

    // Phương thức banMotVatPham giữ nguyên
    private void banMotVatPham(Inventory selectedItem) throws InterruptedException {
        typeEffect("\u001B[34m\n=== Vật phẩm: \u001B[0m" + selectedItem.getItemName() + "\u001B[34m ===\u001B[0m", 5);
        typeEffect("Số lượng: \u001B[33m" + selectedItem.getQuantity(), 5);
        typeEffect("\u001B[36m>> Chọn hành động:\u001B[0m", 5);
        typeEffect("1. Bán vật phẩm", 5);
        typeEffect("2. Quay lại", 5);
        typeEffect("\u001B[36mNhập lựa chọn (1-2): \u001B[0m", 5);

        String choice = scanner.nextLine().trim();
        switch (choice) {
            case "1":
                System.out.print("\u001B[33m- Nhập số lượng: \u001B[0m");
                int soLuong;
                try {
                    soLuong = Integer.parseInt(scanner.nextLine().trim());
                    if (soLuong <= 0 || soLuong > selectedItem.getQuantity()) {
                        typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
                        return;
                    }
                } catch (NumberFormatException e) {
                    typeEffect("\u001B[31mVui lòng nhập số hợp lệ!\u001B[0m", 5);
                    return;
                }

                int pricePerUnit = getItemPrice(selectedItem.getItemName());
                int totalPrice = pricePerUnit * soLuong;
                typeEffect("\u001B[33m> \u001B[36mGiá bán: \u001B[33m" + pricePerUnit + "\u001B[36m xu mỗi cái, tổng cộng \u001B[33m" + totalPrice + "\u001B[36m xu cho \u001B[33m" + soLuong + " " + selectedItem.getItemName() + "\u001B[0m", 5);

                typeEffect("\u001B[36m>> \u001B[0mBạn có chắc chắn muốn bán không? \u001B[33m(Y/N): \u001B[0m", 5);
                String confirm = scanner.nextLine().trim().toUpperCase();
                if (confirm.equals("Y")) {
                    banVatPham(selectedItem.getItemName(), soLuong);
                } else {
                    typeEffect("\u001B[33m> Đã hủy bán!\u001B[0m", 5);
                }
                break;
            case "2":
                typeEffect("\u001B[33m> Đã quay lại!\u001B[0m", 5);
                break;
            default:
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
        }
    }

    // Phương thức banVatPham giữ nguyên
    private void banVatPham(String tenVatPham, int soLuong) throws InterruptedException {
        if (soLuong <= 0) {
            typeEffect("\u001B[31mSố lượng phải lớn hơn 0!\u001B[0m", 5);
            return;
        }

        if (kiemTraKhoHang(tenVatPham, soLuong)) {
            int pricePerUnit = getItemPrice(tenVatPham);
            if (pricePerUnit <= 0) {
                typeEffect("\u001B[31mKhông thể bán " + tenVatPham + " với giá 0 xu!\u001B[0m", 5);
                return;
            }

            int tongXu = soLuong * pricePerUnit;
            currentPlayer.addCoins(tongXu);
            capNhatKhoHang(tenVatPham, -soLuong);
            typeEffect("\u001B[32mĐã bán \u001B[33m" + soLuong + " " + tenVatPham + "\u001B[32m được \u001B[33m" + tongXu + "\u001B[32m xu! (Đơn giá: \u001B[33m" + pricePerUnit + "\u001B[32m xu)\u001B[0m", 5);
            savePlayerData(); // Lưu xu và kho vào DB
        } else {
            int soLuongHienCo = inventory.stream()
                    .filter(i -> i.getItemName().equals(tenVatPham))
                    .mapToInt(Inventory::getQuantity)
                    .findFirst()
                    .orElse(0);
            typeEffect("\u001B[33mKhông đủ " + tenVatPham + " để bán! Hiện có: " + soLuongHienCo + "\u001B[0m", 5);
        }
    }


    // Khởi tạo shop ngẫu nhiên (có thể gọi trong hàm khởi tạo CommandHandler hoặc resetDailyContent)
    private void initializeRandomShop() {
        currentRandomShopItems = new ArrayList<>();
        Random rand = new Random();
        List<String> tempItems = new ArrayList<>(Arrays.asList(randomShopItems));
        int itemCount = rand.nextInt(6) + 2; // Chọn ngẫu nhiên từ 2 đến 7 vật phẩm
        shopItemQuantities = new HashMap<>();

        // Chọn ngẫu nhiên từ 2-7 vật phẩm
        for (int i = 0; i < Math.min(itemCount, randomShopItems.length); i++) {
            int randomIndex = rand.nextInt(tempItems.size());
            String item = tempItems.get(randomIndex);
            currentRandomShopItems.add(item);
            int maxQuantity = rand.nextInt(10) + 1; // Số lượng tối đa từ 1 đến 10
            shopItemQuantities.put(item.split(":")[0].toLowerCase(), maxQuantity);
            tempItems.remove(randomIndex); // Tránh trùng lặp
        }

        lastResetTime = System.currentTimeMillis() / 1000; // Thời gian reset hiện tại
    }

    public void xuLyBuy(String[] parts) throws InterruptedException {
        // Load dữ liệu từ DB nếu có
        Map<String, Object> shopData = db.loadRandomShop(currentPlayer.getId());
        if (!shopData.isEmpty()) {
            currentRandomShopItems = (List<String>) shopData.get("items");
            lastResetTime = (long) shopData.get("resetTime");
            shopItemQuantities = (Map<String, Integer>) shopData.get("quantities");
        }

        // Kiểm tra và reset shop nếu cần
        long currentTime = System.currentTimeMillis() / 1000;
        if (currentRandomShopItems == null || currentRandomShopItems.isEmpty() || currentTime - lastResetTime >= RESET_INTERVAL) {
            initializeRandomShop();
            db.saveRandomShop(currentPlayer.getId(), currentRandomShopItems, lastResetTime, shopItemQuantities);
        }

        // Tính thời gian còn lại
        long timeRemaining = RESET_INTERVAL - (currentTime - lastResetTime);
        if (timeRemaining < 0) timeRemaining = 0; // Đảm bảo không âm

        if (parts.length > 1) {
            String tenVatPham = parts[1].toLowerCase();
            int gia = 0;
            int maxQuantity = 0;
            for (String item : currentRandomShopItems) {
                String[] itemParts = item.split(":");
                if (itemParts[0].toLowerCase().equals(tenVatPham)) {
                    gia = Integer.parseInt(itemParts[1]);
                    maxQuantity = shopItemQuantities.getOrDefault(tenVatPham, 0);
                    muaVatPham(tenVatPham, Math.min(1, maxQuantity), gia);
                    return;
                }
            }
            typeEffect("\u001B[31mVật phẩm không có trong shop ngẫu nhiên!\u001B[0m", 5);
            return;
        }

        // Hiển thị danh sách shop ngẫu nhiên dưới dạng bảng
        System.out.println("\u001B[35m──────────────────────────────────────────────────────────────\u001B[0m");
        System.out.println("\u001B[35m \u001B[36m          Cửa Hàng Kỳ Bí (\u001B[33m" + timeRemaining + "\u001B[36m giây) \u001B[35m            \u001B[0m");
        System.out.println("\u001B[35m┌─────┬────────────────────────────┬────────────┬────────────┐\u001B[0m");

        if (currentRandomShopItems.isEmpty()) {
            typeEffect("\u001B[35m│ \u001B[0m - Không có vật phẩm nào trong shop!                     \u001B[35m│\u001B[0m", 5);
            System.out.println("\u001B[35m└────────────────────────────────────────────────────────┘\u001B[0m");
            return;
        }

        // Định nghĩa độ rộng cố định cho từng cột
        int sttWidth = 5;      // STT: 5 ký tự
        int itemWidth = 25;    // Vật phẩm: 25 ký tự (đủ cho tên dài)
        int qtyWidth = 10;     // Số lượng: 10 ký tự
        int priceWidth = 10;   // Giá: 10 ký tự (đủ cho "100 xu" + màu)

        // Tiêu đề cột
        String header = String.format(
                "\u001B[36m│ %-3s │ %-26s │ %-10s │ %-10s │\u001B[0m",
                "STT", "Vật phẩm", "Số lượng", "Giá"
        );
        typeEffect(header, 5);
        System.out.println("\u001B[35m├─────┼────────────────────────────┼────────────┼────────────┘\u001B[0m");

        // Nội dung bảng
        for (int i = 0; i < currentRandomShopItems.size(); i++) {
            String[] partsItem = currentRandomShopItems.get(i).split(":");
            String itemName = partsItem[0];
            String price = "\u001B[33m" + partsItem[1] + " xu\u001B[0m";
            int quantity = shopItemQuantities.get(itemName.toLowerCase());

            String row = String.format(
                    "\u001B[35m│ \u001B[0m%-3d \u001B[35m│\u001B[0m %-26s \u001B[35m│\u001B[0m %-10d \u001B[35m│\u001B[0m %-10s \u001B[0m",
                    (i + 1), itemName, quantity, price
            );
            typeEffect(row, 5);
        }
        // Chân bảng
        System.out.println("\u001B[35m└─────┴────────────────────────────┴────────────┴─────────────\u001B[0m");

        System.out.print(">>\u001B[33m Chọn vật phẩm (1-" + currentRandomShopItems.size() + " hoặc tên): \u001B[0m");
        String input = scanner.nextLine().trim().toLowerCase();

        String tenVatPham = null;
        int gia = 0;
        int maxQuantity = 0;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < currentRandomShopItems.size()) {
                String[] partsItem = currentRandomShopItems.get(index).split(":");
                tenVatPham = partsItem[0].toLowerCase();
                gia = Integer.parseInt(partsItem[1]);
                maxQuantity = shopItemQuantities.get(tenVatPham);
            }
        } catch (NumberFormatException e) {
            for (String item : currentRandomShopItems) {
                if (item.split(":")[0].toLowerCase().equals(input)) {
                    String[] partsItem = item.split(":");
                    tenVatPham = partsItem[0].toLowerCase();
                    gia = Integer.parseInt(partsItem[1]);
                    maxQuantity = shopItemQuantities.get(tenVatPham);
                    break;
                }
            }
        }

        if (tenVatPham == null) {
            typeEffect("\u001B[31mVật phẩm không hợp lệ!\u001B[0m", 5);
            return;
        }

        // Tính số lượng tối đa có thể mua dựa trên số xu
        int playerCoins = currentPlayer.getCoins(); // Lấy số xu của người chơi
        int maxAffordable = playerCoins / gia; // Số lượng tối đa có thể mua dựa trên tiền
        int maxAvailable = Math.min(maxQuantity, maxAffordable); // Số lượng tối đa có thể mua (giới hạn bởi shop và tiền)

        typeEffect(">\u001B[36m Bạn còn \u001B[33m" + playerCoins + "\u001B[36m xu bạn có thể mua được \u001B[33m" + maxAffordable + " " + tenVatPham + "\u001B[36m (tối đa trong shop: " + maxQuantity + ")\u001B[0m", 5);
        System.out.print("\u001B[33mNhập số lượng (mặc định 1, tối đa " + maxAvailable + "): \u001B[0m");
        String soLuongInput = scanner.nextLine().trim();
        int soLuong;
        try {
            soLuong = soLuongInput.isEmpty() ? 1 : Integer.parseInt(soLuongInput);
            if (soLuong > maxAvailable || soLuong <= 0) {
                typeEffect("\u001B[31m> Số lượng vượt quá giới hạn (\u001B[33m" + maxAvailable + "\u001B[31m) hoặc không hợp lệ!\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 5);
            return;
        }

        int tongGia = gia * soLuong;
        System.out.print("\u001B[33m>>\u001B[0m Xác nhận mua \u001B[33m" + soLuong + " " + tenVatPham + "\u001B[0m với giá \u001B[33m" + tongGia + "\u001B[0m xu? \u001B[33m(Y/N): \u001B[0m");
        String confirm = scanner.nextLine().trim().toUpperCase();
        if (confirm.equals("Y")) {
            muaVatPham(tenVatPham, soLuong, gia);
            int newQuantity = shopItemQuantities.get(tenVatPham) - soLuong;
            shopItemQuantities.put(tenVatPham, newQuantity);
            db.saveRandomShop(currentPlayer.getId(), currentRandomShopItems, lastResetTime, shopItemQuantities);
            if (newQuantity <= 0) {
                final String finalTenVatPham = tenVatPham;
                currentRandomShopItems.removeIf(item -> item.split(":")[0].toLowerCase().equals(finalTenVatPham));
                shopItemQuantities.remove(tenVatPham);
            }
        } else {
            typeEffect("\u001B[33m>>\u001B[31m Đã hủy giao dịch!\u001B[33m <<\u001B[0m", 5);
        }
    }

    private void muaVatPham(String tenVatPham, int soLuong, int gia) throws InterruptedException {
        int tongGia = gia * soLuong;
        // Kiểm tra kho đầy trước khi trừ tiền (trừ động vật)
        if (!animalHarvestTimes.containsKey(tenVatPham.toLowerCase()) && isInventoryFull(soLuong)) {
            typeEffect("\u001B[31mKho đã đầy! Không thể mua thêm " + tenVatPham + "!\u001B[0m", 5);
            return;
        }
        if (currentPlayer.getCoins() >= tongGia) {
            currentPlayer.addCoins(-tongGia);
            // Kiểm tra xem có phải là động vật không
            if (animalHarvestTimes.containsKey(tenVatPham.toLowerCase())) {
                String currentAnimals = farm.getAnimals();
                String newAnimalStatus;
                if (currentAnimals.equals("không có") || currentAnimals.equals("Chuồng trống!")) {
                    newAnimalStatus = tenVatPham + ": " + soLuong + " (đang đói)";
                } else {
                    newAnimalStatus = currentAnimals + "; " + tenVatPham + ": " + soLuong + " (đang đói)";
                }
                farm.setAnimals(newAnimalStatus);
                db.saveFarm(currentPlayer.getId(), farm.getLand(), newAnimalStatus);
                typeEffect("\u001B[32m>>\u001B[0m Đã mua \u001B[33m" + soLuong + " " + tenVatPham + "\u001B[0m với giá \u001B[33m" + tongGia + "\u001B[0m xu từ \u001B[35mCửa Hàng Kỳ Bí!\u001B[0m Động vật đã được thêm vào chuồng!", 5);
            } else if (machineBuildTimes.containsKey(tenVatPham)) {
                capNhatKhoHang(tenVatPham, soLuong);
                typeEffect("\u001B[32m>>\u001B[0m Đã mua \u001B[33m" + soLuong + " " + tenVatPham + "\u001B[0m với giá \u001B[33m" + tongGia + "\u001B[0m xu từ \u001B[35mCửa Hàng Kỳ Bí!\u001B[0m Dùng lệnh 'build' để xây máy.", 5);
            } else {
                capNhatKhoHang(tenVatPham, soLuong);
                typeEffect("\u001B[32m>>\u001B[0m Đã mua \u001B[33m" + soLuong + " " + tenVatPham + "\u001B[0m với giá \u001B[33m" + tongGia + "\u001B[0m xu từ \u001B[35mCửa Hàng Kỳ Bí!\u001B[0m Đã thêm vào kho.", 5);
            }
            savePlayerData();
        } else {
            typeEffect("\u001B[33m>> \u001B[31mKhông đủ xu để mua! \u001B[33m<<\u001B[0m", 5);
        }
    }

    private void muaVatPhamShopChinh(String tenVatPham, int soLuong, int gia) throws InterruptedException {
        int tongGia = gia * soLuong;
        if (isInventoryFull(soLuong)) {
            typeEffect("\u001B[31mKho đã đầy! Không thể mua thêm " + tenVatPham + "!\u001B[0m", 5);
            return;
        }
        if (currentPlayer.getCoins() >= tongGia) {
            String tenVatPhamChu = tenVatPham.substring(0, 1).toUpperCase() + tenVatPham.substring(1);

            // Kiểm tra nếu là máy móc và đã sở hữu
            if (machineBuildTimes.containsKey(tenVatPhamChu)) {
                boolean alreadyOwned = machines.stream().anyMatch(m -> m.getMachineType().equals(tenVatPhamChu)) ||
                        inventory.stream().anyMatch(i -> i.getItemName().equals(tenVatPhamChu) && i.getQuantity() > 0);
                if (alreadyOwned) {
                    typeEffect("\u001B[31mBạn đã sở hữu " + tenVatPhamChu + " rồi! Chỉ được mua 1 lần.\u001B[0m", 2);
                    return;
                }
                if (soLuong > 1) {
                    typeEffect("\u001B[31mChỉ được mua 1 " + tenVatPhamChu + "!\u001B[0m", 2);
                    return;
                }
            }

            currentPlayer.addCoins(-tongGia);

            // Kiểm tra xem có phải là vật nuôi không
            if (animalHarvestTimes.containsKey(tenVatPham.toLowerCase())) {
                String currentAnimals = farm.getAnimals();
                String newAnimalStatus;
                if (currentAnimals.equals("không có") || currentAnimals.equals("Chuồng trống!")) {
                    newAnimalStatus = tenVatPham + ": " + soLuong + " (đang đói)";
                } else {
                    newAnimalStatus = currentAnimals + "; " + tenVatPham + ": " + soLuong + " (đang đói)";
                }
                farm.setAnimals(newAnimalStatus);
                db.saveFarm(currentPlayer.getId(), farm.getLand(), newAnimalStatus);
                typeEffect("\u001B[32mĐã mua " + soLuong + " " + tenVatPhamChu + " với giá " + tongGia + " xu và thêm vào chuồng!\u001B[0m", 2);
            } else if (machineBuildTimes.containsKey(tenVatPhamChu)) {
                capNhatKhoHang(tenVatPhamChu, soLuong);
                typeEffect("\u001B[32mĐã mua " + soLuong + " " + tenVatPhamChu + " với giá " + tongGia + " xu! Dùng lệnh 'build' để xây.\u001B[0m", 2);
            } else {
                capNhatKhoHang(tenVatPhamChu, soLuong);
                typeEffect("\u001B[32mĐã mua " + soLuong + " " + tenVatPhamChu + " với giá " + tongGia + " xu!\u001B[0m", 2);
            }
            savePlayerData();
        } else {
            typeEffect("\u001B[31mKhông đủ xu để mua!\u001B[0m", 2);
        }
    }

    private void xemCuaHang() throws InterruptedException {
        while (true) {
            System.out.println("\u001B[33m\n───────┤\u001B[0m Cửa hàng chính \u001B[33m├───────\u001B[0m");
            System.out.println("\u001B[33m\n┌──────────────────────────┐\u001B[0m");
            System.out.println("\u001B[33m├─\u001B[0m 1. Cây Giống            \u001B[33m│");
            System.out.println("\u001B[33m├─\u001B[0m 2. Động Vật             \u001B[33m│");
            System.out.println("\u001B[33m├─\u001B[0m 3. Máy Móc              \u001B[33m│");
            System.out.println("\u001B[33m├─\u001B[0m 4. Nông Trại            \u001B[33m│");
            System.out.println("\u001B[33m├─\u001B[0m 5. Quay lại             \u001B[33m│");
            System.out.println("\u001B[33m└──────────────────────────┘\u001B[0m");
            typeEffect("\u001B[36m>> Chọn danh mục (1-5 hoặc tên): \u001B[0m", 5);
            String input = scanner.nextLine().trim().toLowerCase();

            int choice;
            try {
                choice = Integer.parseInt(input);
            } catch (NumberFormatException e) {
                if (input.equals("cây giống")) choice = 1;
                else if (input.equals("động vật")) choice = 2;
                else if (input.equals("máy móc")) choice = 3;
                else if (input.equals("nông trại")) choice = 4;
                else if (input.equals("quay lại")) choice = 5;
                else {
                    typeEffect("\u001B[31mDanh mục không hợp lệ!\u001B[0m", 2);
                    continue;
                }
            }

            if (choice == 5) {
                typeEffect("\u001B[33mQuay lại menu chính!\u001B[0m", 2);
                break;
            }

            String[] items;
            String categoryName;
            switch (choice) {
                case 1:
                    items = cropItems;
                    categoryName = "Cây Giống";
                    break;
                case 2:
                    items = animalItems;
                    categoryName = "Động Vật";
                    break;
                case 3:
                    items = Arrays.stream(machineItems)
                            .map(item -> {
                                String[] parts = item.split(":");
                                String machineName = parts[0];
                                boolean owned = machines.stream()
                                        .anyMatch(m -> m.getMachineType().toLowerCase().equals(machineName.toLowerCase())) ||
                                        inventory.stream()
                                                .anyMatch(i -> i.getItemName().toLowerCase().equals(machineName.toLowerCase()) && i.getQuantity() > 0);
                                return owned ? machineName + ":Đã có" : item;
                            })
                            .toArray(String[]::new);
                    categoryName = "Máy Móc";
                    break;
                case 4:
                    items = new String[]{"Mảnh đất:500"};
                    categoryName = "Nông Trại";
                    break;
                default:
                    typeEffect("\u001B[31mDanh mục không hợp lệ!\u001B[0m", 2);
                    continue;
            }

            typeEffect("\u001B[33m\n=== Danh sách " + categoryName + " ===\u001B[0m", 2);
            int padding = 4;
            int maxIndexLength = 3 + padding;
            int maxNameLength = 18 + padding;
            int maxPriceLength = 10 + padding;

            String horizontalLine = "\u001B[34m+" + "-".repeat(maxIndexLength) + "+" + "-".repeat(maxNameLength) + "+" + "-".repeat(maxPriceLength) + "+\u001B[0m";
            String headerFormat = "\u001B[33m| %-" + (maxIndexLength - 2) + "s | %-" + (maxNameLength - 2) + "s | %-" + (maxPriceLength - 2) + "s |\u001B[0m";
            String rowFormat = "| %-" + (maxIndexLength - 2) + "s | %-" + (maxNameLength - 2) + "s | %-" + (maxPriceLength - 2) + "s |";

            typeEffect(horizontalLine, 2);
            typeEffect(String.format(headerFormat, "STT", "Tên vật phẩm", "Giá"), 2);
            typeEffect(horizontalLine, 2);

            for (int i = 0; i < items.length; i++) {
                String[] parts = items[i].split(":");
                String priceDisplay = parts[1].equals("Đã có") ? parts[1] : parts[1] + " xu";
                typeEffect(String.format(rowFormat, (i + 1), parts[0], priceDisplay), 2);
            }
            typeEffect(horizontalLine, 2);

            typeEffect("\u001B[36m>> Chọn vật phẩm (1-" + items.length + " hoặc tên): \u001B[0m", 5);
            String itemInput = scanner.nextLine().trim().toLowerCase();

            String tenVatPham = null;
            String giaStr = null;
            int gia = 0;
            try {
                int index = Integer.parseInt(itemInput) - 1;
                if (index >= 0 && index < items.length) {
                    String[] parts = items[index].split(":");
                    tenVatPham = parts[0].toLowerCase();
                    giaStr = parts[1];
                    if (!giaStr.equals("Đã có")) {
                        gia = Integer.parseInt(giaStr);
                    }
                }
            } catch (NumberFormatException e) {
                for (String item : items) {
                    if (item.split(":")[0].toLowerCase().equals(itemInput)) {
                        String[] parts = item.split(":");
                        tenVatPham = parts[0].toLowerCase();
                        giaStr = parts[1];
                        if (!giaStr.equals("Đã có")) {
                            gia = Integer.parseInt(giaStr);
                        }
                        break;
                    }
                }
            }

            if (tenVatPham == null) {
                typeEffect("\u001B[31mVật phẩm không hợp lệ!\u001B[0m", 2);
                continue;
            }

            if (giaStr != null && giaStr.equals("Đã có")) {
                typeEffect("\u001B[31mBạn đã sở hữu " + tenVatPham + " rồi!\u001B[0m", 2);
                continue;
            }

            if (choice == 3) {
                System.out.print("\u001B[33mBạn muốn mua " + tenVatPham + " với giá " + gia + " xu? (Y/N): \u001B[0m");
                String confirm = scanner.nextLine().trim().toUpperCase();
                if (confirm.equals("Y")) {
                    muaVatPhamShopChinh(tenVatPham, 1, gia);
                } else {
                    typeEffect("\u001B[33m>>\u001B[31m Đã hủy giao dịch! \u001B[33m<<\u001B[0m", 2);
                }
            } else {
                System.out.print("\u001B[33mNhập số lượng (mặc định 1): \u001B[0m");
                String soLuongInput = scanner.nextLine().trim();
                int soLuong;
                try {
                    soLuong = soLuongInput.isEmpty() ? 1 : Integer.parseInt(soLuongInput);
                } catch (NumberFormatException e) {
                    typeEffect("\u001B[31mSố lượng không hợp lệ!\u001B[0m", 2);
                    continue;
                }

                int tongGia = gia * soLuong;
                System.out.print("\u001B[33mXác nhận mua " + soLuong + " " + tenVatPham + " với giá " + tongGia + " xu? (Y/N): \u001B[0m");
                String confirm = scanner.nextLine().trim().toUpperCase();
                if (confirm.equals("Y")) {
                    if (tenVatPham.equals("mảnh đất")) {
                        if (currentPlayer.getCoins() >= tongGia) {
                            currentPlayer.addCoins(-tongGia);
                            totalLandPlots += soLuong;
                            for (int i = totalLandPlots - soLuong + 1; i <= totalLandPlots; i++) {
                                landPlots.put(i, "trống");
                            }
                            saveLandData();
                            typeEffect("\u001B[32mĐã mua " + soLuong + " mảnh đất! Tổng số đất: " + totalLandPlots + "\u001B[0m", 2);
                        } else {
                            typeEffect("\u001B[31mKhông đủ xu để mua!\u001B[0m", 2);
                        }
                    } else {
                        muaVatPhamShopChinh(tenVatPham, soLuong, gia);
                    }
                } else {
                    typeEffect("\u001B[33m>>\u001B[31m Đã hủy giao dịch! \u001B[33m<<\u001B[0m", 2);
                }
            }
        }
    }

    private void xuLyBuild(String[] parts) throws InterruptedException {
        if (parts.length > 1) {
            xayMayMoc(parts[1].toLowerCase());
            return;
        }
        System.out.println("\u001B[34m\n=== Danh sách Máy móc trong kho ===\u001B[0m");
        List<Inventory> machinesInInventory = inventory.stream()
                .filter(item -> machineBuildTimes.containsKey(item.getItemName().toLowerCase()) && item.getQuantity() > 0)
                .toList();
        if (machinesInInventory.isEmpty()) {
            System.out.println("Không có Máy móc nào để xây!");
            return;
        }
        for (int i = 0; i < machinesInInventory.size(); i++) {
            Inventory item = machinesInInventory.get(i);
            System.out.println((i + 1) + ". " + item.getItemName() + " (Thời gian xây: " + machineBuildTimes.get(item.getItemName().toLowerCase()) + " giây)");
        }
        System.out.print("\u001B[36m>> Chọn Máy để xây (1-" + machinesInInventory.size() + " hoặc tên): \u001B[0m");
        String input = scanner.nextLine().trim().toLowerCase();

        String tenMay = null;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < machinesInInventory.size()) {
                tenMay = machinesInInventory.get(index).getItemName();
            }
        } catch (NumberFormatException e) {
            for (Inventory item : machinesInInventory) {
                if (item.getItemName().toLowerCase().equals(input)) {
                    tenMay = item.getItemName();
                    break;
                }
            }
        }

        if (tenMay == null) {
            System.out.println("\u001B[31mMáy móc không hợp lệ!\u001B[0m");
            return;
        }

        xayMayMoc(tenMay.toLowerCase());
    }

    private void xayMayMoc(String tenMay) throws InterruptedException {
        tenMay = tenMay.toLowerCase(); // Chuẩn hóa tên máy
        if (!kiemTraKhoHang(tenMay, 1)) {
            System.out.println("\u001B[33m>> Không có " + tenMay + " trong kho!\u001B[0m");
            return;
        }
        if (machineTimers.containsKey(tenMay + ":build")) {
            System.out.println("\u001B[36m>> " + tenMay + " đang được xây!\u001B[0m");
            return;
        }
        capNhatKhoHang(tenMay, -1);
        long completionTime = System.currentTimeMillis() / 1000 + machineBuildTimes.get(tenMay);
        machineTimers.put(tenMay + ":build", completionTime);
        machines.add(new Machine(machines.size() + 1, currentPlayer.getId(), tenMay, "đang xây"));
        System.out.println("\u001B[32mĐã bắt đầu xây " + tenMay + "! Hoàn thành sau " + machineBuildTimes.get(tenMay) + " giây.\u001B[0m");
    }

    private void nhanDonHang() throws InterruptedException {
        xemNhiemVu();
        typeEffect("\u001B[36m>> Chọn nhiệm vụ để nhận (1-" + tasks.size() + "): \u001B[0m", 5);
        String input = scanner.nextLine().trim();
        int index;
        try {
            index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < tasks.size() && tasks.get(index).getStatus().equals("incomplete")) {
                tasks.get(index).setStatus("in progress");
                db.saveTasks(currentPlayer.getId(), tasks); // Lưu trạng thái vào DB
                typeEffect("\u001B[32mĐã nhận nhiệm vụ: " + tasks.get(index).getTaskName() + "!\u001B[0m", 5);
            } else {
                typeEffect("\u001B[36m>> Nhiệm vụ không hợp lệ hoặc đã được nhận/hoàn thành!\u001B[0m", 5);
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mVui lòng nhập số hợp lệ!\u001B[0m", 5);
        }
    }

    private void giaoHangXeTai() throws InterruptedException {
        updateTasksAndOrders(); // Cập nhật trước để đảm bảo danh sách đúng

        // Tiêu đề
        typeEffect("\u001B[33m┌────────────────────────────┐\u001B[0m", 5);
        typeEffect("\u001B[33m│\u001B[36m     Đơn Hàng Xe Tải        \u001B[33m│\u001B[0m", 5);
        typeEffect("\u001B[33m└────────────────────────────┘\u001B[0m", 5);

        if (currentTruckOrders.isEmpty()) {
            typeEffect(" - Chưa có đơn hàng xe tải nào!", 5);
            return;
        }

        // Định nghĩa độ rộng cố định cho từng cột
        int sttWidth = 5;         // STT: 5 ký tự
        int itemsWidth = 80;      // Vật phẩm: tăng chiều dài để chứa đủ
        int rewardWidth = 12;     // Thưởng: tăng nhẹ để cân đối

        // Tiêu đề bảng
        String header = String.format(
                "┌─────┬%s┬%s┐\n" +
                        "│ \u001B[33m%-3s\u001B[0m │ \u001B[33m%-" + itemsWidth + "s\u001B[0m │ \u001B[33m%-" + (rewardWidth - 2) + "s\u001B[0m   │\n" +
                        "├─────┼%s┼%s┤",
                "─".repeat(itemsWidth + 2),
                "─".repeat(rewardWidth + 2),
                "STT", "Vật phẩm", "Thưởng",
                "─".repeat(itemsWidth + 2),
                "─".repeat(rewardWidth + 2)
        );
        typeEffect(header, 2);

        // Nội dung bảng
        for (int i = 0; i < currentTruckOrders.size(); i++) {
            String order = currentTruckOrders.get(i);
            String[] items = order.split(", ");
            StringBuilder displayItems = new StringBuilder();
            int totalReward = 0;

            for (int j = 0; j < items.length; j++) {
                String[] parts = items[j].split(":");
                if (parts.length == 2) {
                    String itemQtyName = parts[0].trim();
                    int reward = Integer.parseInt(parts[1]);
                    if (j > 0) displayItems.append(", ");
                    displayItems.append(itemQtyName);
                    totalReward += reward;
                }
            }

            String itemsText = displayItems.toString(); // Không cắt nữa!

            String row = String.format(
                    "│ %-3d │ %-"+itemsWidth+"s │ \u001B[33m%-" + (rewardWidth - 2) + "s\u001B[0m   │",
                    (i + 1), itemsText, totalReward + " xu"
            );
            typeEffect(row, 2);
        }

        // Chân bảng
        String footer = String.format("└─────┴%s┴%s┘",
                "─".repeat(itemsWidth + 2),
                "─".repeat(rewardWidth + 2)
        );
        typeEffect(footer, 2);

        // Chọn đơn hàng
        typeEffect("\u001B[36m>> Chọn đơn hàng (1-" + currentTruckOrders.size() + "): \u001B[0m", 2);
        String input = scanner.nextLine().trim();
        int index;

        try {
            index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < currentTruckOrders.size()) {
                String order = currentTruckOrders.get(index);
                String[] items = order.split(", ");
                int totalReward = 0;
                boolean canDeliver = true;

                for (String item : items) {
                    String[] parts = item.split(":");
                    if (parts.length != 2) {
                        typeEffect("\u001B[31mĐịnh dạng đơn hàng không hợp lệ!\u001B[0m", 2);
                        return;
                    }
                    String[] itemParts = parts[0].trim().split(" ", 2);
                    int qty = Integer.parseInt(itemParts[0]);
                    String itemName = itemParts[1];
                    int reward = Integer.parseInt(parts[1]);

                    if (!kiemTraKhoHang(itemName, qty)) {
                        typeEffect("\u001B[33m>> Không đủ " + itemName + " để giao!\u001B[0m", 2);
                        canDeliver = false;
                        break;
                    }
                    totalReward += reward;
                }

                if (canDeliver) {
                    for (String item : items) {
                        String[] parts = item.split(":");
                        String[] itemParts = parts[0].trim().split(" ", 2);
                        int qty = Integer.parseInt(itemParts[0]);
                        String itemName = itemParts[1];
                        capNhatKhoHang(itemName, -qty);
                    }
                    currentPlayer.addCoins(totalReward);
                    typeEffect("\u001B[32mĐã giao " + order + "! Kiếm được " + totalReward + " xu.\u001B[0m", 2);
                    checkTaskCompletion("Giao " + order);
                    truckOrderStatus.put(order, "completed");
                    currentTruckOrders.remove(index);
                    updateTasksAndOrders();
                    savePlayerData();
                }
            } else {
                typeEffect("\u001B[31mĐơn hàng không hợp lệ!\u001B[0m", 2);
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mVui lòng nhập số hợp lệ!\u001B[0m", 2);
        } catch (Exception e) {
            typeEffect("\u001B[31mLỗi không xác định: " + e.getMessage() + "\u001B[0m", 2);
        }
    }


    private void xemNhiemVu() throws InterruptedException {
        // Tiêu đề
        typeEffect("\u001B[36m┌────────────────────────────┐\u001B[0m", 5);
        typeEffect("\u001B[36m│\u001B[0m     Nhiệm Vụ Hàng Ngày    \u001B[36m │\u001B[0m", 5);
        typeEffect("\u001B[36m└────────────────────────────┘\u001B[0m", 5);

        if (tasks.isEmpty()) {
            typeEffect(" - Chưa có nhiệm vụ nào!", 5);
            return;
        }

        // Định nghĩa độ rộng cố định cho từng cột
        int sttWidth = 5;      // STT: 5 ký tự (bao gồm khoảng trắng)
        int taskWidth = 25;    // Nhiệm Vụ: 25 ký tự (đủ cho tên dài như "Chế biến 6 nước sốt")
        int statusWidth = 20;  // Trạng Thái: 20 ký tự (đủ cho "Chưa hoàn thành" + màu)
        int rewardWidth = 10;  // Thưởng: 10 ký tự (đủ cho "100 xu" + màu)

        // Tiêu đề bảng
        String header = String.format(
                "┌─────┬─────────────────────────┬────────────────────┬──────────┐\n" +
                        "│ \u001B[36m%-3s\u001B[0m │ \u001B[36m%-23s\u001B[0m │ \u001B[36m%-18s\u001B[0m │ \u001B[36m%-8s\u001B[0m │\n" +
                        "├─────┼─────────────────────────┼────────────────────┼──────────┤",
                "STT", "Nhiệm Vụ", "Trạng Thái", "Thưởng"
        );
        typeEffect(header, 5);

        // Nội dung bảng
        for (int i = 0; i < tasks.size(); i++) {
            Task task = tasks.get(i);
            String taskName = task.getTaskName();
            String status = task.getStatus(); // Giữ mã màu
            String reward = task.getReward() + " xu";

            // Cắt ngắn nếu quá dài (tùy chọn, để tránh tràn cột)
            taskName = taskName.length() > taskWidth - 2 ? taskName.substring(0, taskWidth - 5) + "..." : taskName;
            status = status.replaceAll("\u001B\\[[0-9;]*m", "").length() > statusWidth - 2 ? status.substring(0, statusWidth - 5) + "..." : status;

            String row = String.format(
                    "│ %-3d │ %-23s │ %-18s    │ \u001B[33m%-8s\u001B[0m │",
                    (i + 1), taskName, status, reward
            );
            typeEffect(row, 5);
        }

        // Chân bảng
        typeEffect("└─────┴─────────────────────────┴────────────────────┴──────────┘", 5);
    }

    private void hienDanhSachLenh() {
        System.out.println("\u001B[36m");
        System.out.println("─────────────────────────────────────────────────\u001B[0m");
        System.out.println("                   MENU TRANG TRẠI  ");
        System.out.println("\u001B[36m─────────────────────────────────────────────────\u001B[0m");
        System.out.println("┌──────────────┬────────────────────────────────────────────┐");
        System.out.println("│ \u001B[33mLệnh\u001B[0m         │ \u001B[33mChức năng\u001B[0m                                  │");
        System.out.println("├──────────────┼────────────────────────────────────────────┤");
        System.out.println("│ \u001B[33mfarm\u001B[0m         │ Xem thông tin trang trại của bạn           │");
        System.out.println("│ \u001B[33mplant\u001B[0m        │ Trồng cây (lúa, bắp, cà rốt,...)           │");
        System.out.println("│ \u001B[33mfer\u001B[0m          │ Bón phân giúp cây phát triển nhanh hơn 10% │");
        System.out.println("│ \u001B[33mfeed\u001B[0m         │ Cho vật nuôi ăn (gà, bò,...)               │");
        System.out.println("│ \u001B[33mcollect\u001B[0m      │ Thu sản phẩm từ vật nuôi (trứng, sữa,...)  │");
        System.out.println("│ \u001B[33mcraft\u001B[0m        │ Chế biến sản phẩm (bánh mì, bỏng ngô,...)  │");
        System.out.println("│ \u001B[33msell\u001B[0m         │ Bán sản phẩm để kiếm xu                    │");
        System.out.println("│ \u001B[33mbuy\u001B[0m          │ Mua từ \u001B[35mCửa Hàng Thần Bí\u001B[0m (giá rẻ hơn 10%)   │");
        System.out.println("│ \u001B[33mshop\u001B[0m         │ Xem shop chính (cây, động vật, Máy móc)    │");
        System.out.println("│ \u001B[33mprice\u001B[0m        │ Xem giá cả (cây, động vật, Máy móc)        │");
        System.out.println("│ \u001B[33mbuild\u001B[0m        │ Xây Máy móc từ kho                         │");
        System.out.println("│ \u001B[33mtool\u001B[0m         │ Nâng cấp kho và nhà máy của bạn            │");
        System.out.println("│ \u001B[33morder\u001B[0m        │ Nhận nhiệm vụ từ danh sách                 │");
        System.out.println("│ \u001B[33mtruck\u001B[0m        │ Giao hàng bằng xe tải                      │");
        System.out.println("│ \u001B[33mtasks\u001B[0m        │ Xem nhiệm vụ hàng tuần                     │");
        System.out.println("│ \u001B[33mcheck\u001B[0m        │ Kiểm tra trạng thái hoạt động              │");
        System.out.println("│ \u001B[33mlogout\u001B[0m       │ Đăng xuất khỏi trò chơi                    │");
        System.out.println("│ \u001B[31mexit\u001B[0m         │ Thoát khỏi trò chơi                        │");
        System.out.println("└──────────────┴────────────────────────────────────────────┘");
    }

    private boolean kiemTraKhoHang(String tenVatPham, int soLuong) {
        return inventory.stream()
                .filter(i -> i.getItemName().equalsIgnoreCase(tenVatPham))
                .mapToInt(Inventory::getQuantity)
                .sum() >= soLuong;
    }

    private void capNhatKhoHang(String itemName, int quantityChange, Double weight, String rarity) throws InterruptedException {
        boolean itemExists = false;
        for (Inventory item : inventory) {
            if (item.getItemName().equalsIgnoreCase(itemName)) {
                item.addQuantity(quantityChange);
                if (weight != null) item.setWeight(weight);
                if (rarity != null) item.setRarity(rarity);
                itemExists = true;
                break;
            }
        }
        if (!itemExists && quantityChange > 0) {
            Inventory newItem = new Inventory(inventory.size() + 1, currentPlayer.getId(), itemName, quantityChange, null, weight, rarity);
            inventory.add(newItem);
        }
        db.saveInventory(currentPlayer.getId(), itemName, quantityChange, null, weight, rarity);
    }

    private void capNhatKhoHang(String itemName, int quantityChange) throws InterruptedException {
        capNhatKhoHang(itemName, quantityChange, null, null);
    }

    private boolean kiemTraMayMoc(String machineType) {
        boolean result = machines.stream()
                .anyMatch(m -> {
                    String actualMachineType = m.getMachineType().trim();
                    String actualStatus = m.getStatus().replaceAll("\u001B\\[[0-9;]*m", "").trim(); // Loại bỏ mã màu
                    boolean typeMatch = actualMachineType.equalsIgnoreCase(machineType.trim());
                    boolean statusMatch = actualStatus.equalsIgnoreCase("rảnh");
                    return typeMatch && statusMatch;
                });
        return result;
    }

    private void xuLyPrice(String[] parts) throws InterruptedException {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"));
        String dateStr = today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        typeEffect("\u001B[34m\n=== Chọn Danh Mục Giá ===\u001B[0m", 5);
        typeEffect("1. Cây trồng", 5);
        typeEffect("2. Sản phẩm từ động vật", 5);
        typeEffect("3. Sản phẩm chế biến", 5);
        typeEffect("4. Cá", 5);
        typeEffect("\u001B[36m>> Nhập số (1-4): \u001B[0m", 5);

        String input = scanner.nextLine().trim();
        int choice;
        try {
            choice = Integer.parseInt(input);
            if (choice < 1 || choice > 4) {
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
                return;
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
            return;
        }

        Map<String, Integer> basePrices = new HashMap<>() {{
            // Cây trồng
            put("lúa mì", 16); put("bắp", 28); put("cà rốt", 40); put("đậu nành", 55); put("mía cây", 70);
            put("bí ngô", 170); put("ớt", 70); put("dâu tây", 100); put("cà chua", 70); put("khoai tây", 55);
            put("dưa hấu", 165); put("hành tây", 40); put("tỏi", 35); put("cải xanh", 45); put("táo", 120);
            put("bông", 200); put("cacao", 240); put("lá trà", 160);

            // Sản phẩm từ vật nuôi
            put("trứng", 21); put("sữa", 80); put("thịt", 260); put("len", 385);

            // Sản phẩm từ máy móc
            put("bánh mì", 22); put("bỏng ngô", 86); put("thức ăn cho gà", 20); put("kem", 140); put("đường", 150);
            put("thức ăn cho bò", 37); put("thức ăn cho lợn", 45); put("thức ăn cho cừu", 52);
            put("vải cotton", 230); put("bánh dâu", 130); put("phô mai", 120); put("nước ép dâu", 140); put("mứt táo", 150);
            put("kem tuyết", 100); put("bánh nướng", 110); put("sữa chua", 130); put("bánh quy", 90); put("nước sốt", 95);
            put("kẹo", 120); put("bánh pizza", 160); put("vải", 260); put("socola", 300); put("trà xanh", 200); put("cá phi lê", 85);

            // Cá
            // Common (24 loài, giá từ 10-34 xu)
            put("Cá rô phi", 10); put("Cá tra", 12); put("Cá basa", 13); put("Cá mè trắng", 14);
            put("Cá trắm cỏ", 15); put("Cá chép", 16); put("Cá lóc", 17); put("Cá trê", 18);
            put("Cá diêu hồng", 19); put("Cá nục", 20); put("Cá thu", 21); put("Cá ngân", 22);
            put("Cá đối", 23); put("Cá mòi", 24); put("Cá bạc má", 25); put("Cá sòng", 26);
            put("Cá bống", 27); put("Cá kèo", 28); put("Cá linh", 29); put("Cá cơm", 30);
            put("Cá sơn", 31); put("Cá ét", 32); put("Cá hanh", 33); put("Cá ngát", 34);

            // Uncommon (16 loài, giá từ 40-85 xu)
            put("Cá bống tượng", 40); put("Cá lăng", 43); put("Cá mú", 46); put("Cá chim trắng", 49);
            put("Cá tai tượng", 52); put("Cá bớp", 55); put("Cá sặc rằn", 58); put("Cá hồng", 61);
            put("Cá trèn", 64); put("Cá he", 67); put("Cá thác lác", 70); put("Cá úc", 73);
            put("Cá chạch", 76); put("Cá mối", 79); put("Cá nhồng", 82); put("Cá dìa", 85);

            // Super Rare (12 loài, giá từ 150-300 xu)
            put("Cá chình", 170); put("Cá ngựa", 180); put("Cá rồng", 195); put("Cá sấu", 205);
            put("Cá mập bò", 225); put("Cá lưỡi trâu", 235); put("Cá hải tượng", 250); put("Cá chép Koi", 270);
            put("Cá hề", 305); put("Cá mó", 375); put("Cá thòi lòi", 420); put("Cá bống sao", 610);

            // Legendary (12 loài, giá từ 400-1000 xu)
            put("Cá tra dầu", 1200); put("Cá sủ vàng", 1350); put("Cá đuối sông", 1500); put("Cá hô", 1650);
            put("Cá anh vũ", 1800); put("Cá chiên", 1950); put("Cá heo nước ngọt", 2100); put("Cá leo", 2250);
            put("Cá bông lau", 2400); put("Cá mặt trăng", 2550); put("Cá tầm", 2700); put("Cá vồ cờ", 3000);
        }};

        String categoryName;
        switch (choice) {
            case 1: // Cây trồng
                categoryName = "Cây trồng";
                typeEffect("\u001B[33m─────────┤\u001B[36mGiá " + categoryName + " Ngày " + dateStr + "\u001B[33m├─────────\u001B[0m", 2);
                typeEffect("────────────────────────────────────────────────", 2);
                typeEffect("\u001B[33mSTT | Vật phẩm       | Giá       | Biến động\u001B[0m", 2);
                typeEffect("────|────────────────|───────────|──────────────", 2);
                int stt = 1;
                for (String crop : cropGrowthTimes.keySet()) {
                    String itemName = crop.equals("mía") ? "mía cây" : crop;
                    int price = getItemPrice(crop);
                    Integer basePriceObj = basePrices.get(itemName);
                    int basePrice = (basePriceObj != null) ? basePriceObj : 1;
                    String fluctuation = getFluctuationString(price, basePrice);
                    typeEffect(String.format("%-3d | %-14s | %-6d xu | %s", stt++, crop, price, fluctuation), 2);
                }
                typeEffect("───────────────────────────────────────────────", 2);
                break;

            case 2: // Sản phẩm từ động vật
                categoryName = "Sản phẩm từ động vật";
                typeEffect("\u001B[33m─────────┤\u001B[36mGiá " + categoryName + " Ngày " + dateStr + "\u001B[33m├─────────\u001B[0m", 2);
                typeEffect("────────────────────────────────────────────────", 2);
                typeEffect("\u001B[33mSTT | Vật phẩm       | Giá       | Biến động\u001B[0m", 2);
                typeEffect("────|────────────────|───────────|──────────────", 2);
                stt = 1;
                Map<String, String> animalProducts = new HashMap<>() {{
                    put("gà", "trứng"); put("bò", "sữa"); put("heo", "thịt"); put("cừu", "len");
                }};
                for (String animal : animalHarvestTimes.keySet()) {
                    String product = animalProducts.get(animal);
                    int price = getItemPrice(product);
                    Integer basePriceObj = basePrices.get(product);
                    int basePrice = (basePriceObj != null) ? basePriceObj : 1;
                    String fluctuation = getFluctuationString(price, basePrice);
                    typeEffect(String.format("%-3d | %-14s | %-6d xu | %s", stt++, product, price, fluctuation), 2);
                }
                typeEffect("────────────────────────────────────────────────", 5);
                break;

            case 3: // Sản phẩm chế biến
                categoryName = "Sản phẩm chế biến";
                typeEffect("\u001B[33m─────────┤\u001B[36mGiá " + categoryName + " Ngày " + dateStr + "\u001B[33m├─────────\u001B[0m", 2);
                typeEffect("──────────────────────────────────────────────────", 2);
                typeEffect("\u001B[33mSTT  | Vật phẩm        | Giá       | Biến động\u001B[0m", 5);
                typeEffect("────|──────────────────|───────────|──────────────", 2);
                stt = 1;
                Map<String, List<String>> machineProducts = new HashMap<>() {{
                    put("Lò Bánh Mì", List.of("bánh mì"));
                    put("Nồi Bỏng Ngô", List.of("bỏng ngô"));
                    put("Máy Làm Thức Ăn", List.of("thức ăn cho gà", "thức ăn cho bò", "thức ăn cho lợn", "thức ăn cho cừu")); // Một máy, 4 sản phẩm
                    put("Nhà Máy Sữa", List.of("kem"));
                    put("Nhà Máy Đường", List.of("đường"));
                    put("Máy May", List.of("vải cotton"));
                    put("Máy Làm Bánh Ngọt", List.of("bánh dâu"));
                    put("Máy Làm Phô Mai", List.of("phô mai"));
                    put("Máy Làm Nước Ép", List.of("nước ép dâu"));
                    put("Máy Làm Mứt", List.of("mứt táo"));
                    put("Máy Làm Kem Tuyết", List.of("kem tuyết"));
                    put("Máy Làm Bánh Nướng", List.of("bánh nướng"));
                    put("Máy Làm Sữa Chua", List.of("sữa chua"));
                    put("Máy Làm Bánh Quy", List.of("bánh quy"));
                    put("Máy Làm Nước Sốt", List.of("nước sốt"));
                    put("Máy Làm Kẹo", List.of("kẹo"));
                    put("Máy Làm Bánh Pizza", List.of("bánh pizza"));
                    put("Máy Dệt Vải", List.of("vải"));
                    put("Máy Làm Socola", List.of("socola"));
                    put("Máy Làm Trà", List.of("trà"));
                }};
                for (Map.Entry<String, List<String>> entry : machineProducts.entrySet()) {
                    List<String> products = entry.getValue();
                    for (String product : products) {
                        int price = getItemPrice(product);
                        Integer basePriceObj = basePrices.get(product);
                        int basePrice = (basePriceObj != null) ? basePriceObj : 1;
                        String fluctuation = getFluctuationString(price, basePrice);
                        typeEffect(String.format("%-3d | %-16s | %-6d xu | %s", stt++, product, price, fluctuation), 2);
                    }
                }
                typeEffect("────────────────────────────────────────────────", 2);
                break;

            case 4: // Cá
                categoryName = "Cá";
                typeEffect("\u001B[33m─────────┤\u001B[36mGiá " + categoryName + " Ngày " + dateStr + "\u001B[33m├─────────\u001B[0m", 2);
                typeEffect("──────────────────────────────────────────────────────", 2);
                typeEffect("\u001B[33mSTT | Vật phẩm             | Giá       | Biến động\u001B[0m", 2);
                typeEffect("────|──────────────────────|───────────|──────────────", 2);
                stt = 1;

                String[][] fishTypes = {
                        // Common (24 loài) - Trắng
                        {"Cá rô phi", "Cá tra", "Cá basa", "Cá mè trắng", "Cá trắm cỏ", "Cá chép",
                                "Cá lóc", "Cá trê", "Cá diêu hồng", "Cá nục", "Cá thu", "Cá ngân",
                                "Cá đối", "Cá mòi", "Cá bạc má", "Cá sòng", "Cá bống", "Cá kèo",
                                "Cá linh", "Cá cơm", "Cá sơn", "Cá ét", "Cá hanh", "Cá ngát"},

                        // Uncommon (16 loài) - Xanh lá
                        {"Cá bống tượng", "Cá lăng", "Cá mú", "Cá chim trắng", "Cá tai tượng", "Cá bớp",
                                "Cá sặc rằn", "Cá hồng", "Cá trèn", "Cá he", "Cá thác lác", "Cá úc",
                                "Cá chạch", "Cá mối", "Cá nhồng", "Cá dìa"},

                        // Super Rare (12 loài) - Tím
                        {"Cá chình", "Cá ngựa", "Cá rồng", "Cá sấu", "Cá mập bò", "Cá lưỡi trâu",
                                "Cá hải tượng", "Cá chép Koi", "Cá hề", "Cá mó", "Cá thòi lòi", "Cá bống sao"},

                        // Legendary (12 loài) - Cầu vồng đổi màu từng chữ
                        {"Cá tra dầu", "Cá sủ vàng", "Cá đuối sông", "Cá hô", "Cá anh vũ", "Cá chiên",
                                "Cá heo nước ngọt", "Cá leo", "Cá bông lau", "Cá mặt trăng", "Cá tầm", "Cá vồ cờ"}
                };

                for (int group = 0; group < fishTypes.length; group++) {
                    for (String fish : fishTypes[group]) {
                        String fishNameDisplay;
                        switch (group) {
                            case 0: // Common - Trắng
                                fishNameDisplay = "\u001B[37m" + fish + "\u001B[0m";
                                break;
                            case 1: // Uncommon - Xanh lá
                                fishNameDisplay = "\u001B[32m" + fish + "\u001B[0m";
                                break;
                            case 2: // Super Rare - Tím
                                fishNameDisplay = "\u001B[35m" + fish + "\u001B[0m";
                                break;
                            case 3: // Legendary - cầu vồng
                                fishNameDisplay = toRainbow(fish);
                                break;
                            default:
                                fishNameDisplay = fish;
                        }

                        int price = getItemPrice(fish);
                        Integer basePriceObj = basePrices.get(fish);
                        int basePrice = (basePriceObj != null) ? basePriceObj : 1;
                        String fluctuation = getFluctuationString(price, basePrice);

                        String paddedFish = padRight(fishNameDisplay, 20);
                        String line = String.format("%-3d | %s | %-9s | %s", stt++, paddedFish, price + " xu", fluctuation);
                        typeEffect(line, 2);
                    }

                    if (group < fishTypes.length - 1) {
                        typeEffect("────|──────────────────────|───────────|──────────────", 2);
                    }
                }

                typeEffect("────────────────────────────────────────────────────", 2);
                break;
        }
    }

    // Hàm phụ để tính và định dạng mức dao động
    private String getFluctuationString(int currentPrice, int basePrice) {
        double fluctuation = (double) (currentPrice - basePrice) / basePrice * 100;
        if (fluctuation > 0) {
            return "\u001B[32m ↗ Tăng " + String.format("%.0f%%", fluctuation) + "\u001B[0m";
        } else if (fluctuation < 0) {
            return "\u001B[33m ↘ Giảm " + String.format("%.0f%%", Math.abs(fluctuation)) + "\u001B[0m";
        } else {
            return " - Không đổi";
        }
    }

    // Kiểm tra kho đầy
    private boolean isInventoryFull(int additionalItems) {
        int totalItems = inventory.stream().mapToInt(Inventory::getQuantity).sum();
        return (totalItems + additionalItems) > maxInventorySlots;
    }


    // Phương thức mới để sửa cần câu
    private void repairFishingRod() throws InterruptedException {
        String fishingTool = "Cần Câu Cá";
        int durability = getFishingToolDurability();

        if (durability == -1) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mBạn chưa có Cần câu cá trong kho!\u001B[0m", 5);
            return;
        }

        if (durability > 0) {
            typeEffect(">>\u001B[36m Cần câu của bạn vẫn còn độ bền (\u001B[33m" + durability + "\u001B[36m), không cần sửa!\u001B[0m", 5);
            return;
        }

        typeEffect("\u001B[31m<\u001B[33m!\u001B[31m>> \u001B[0mCần câu của bạn đã hỏng, bạn có muốn sửa với giá \u001B[33m185\u001B[0m xu không? \u001B[33m(Y/N)\u001B[0m", 5);
        String choice = scanner.nextLine().trim().toUpperCase();
        if (choice.equals("Y")) {
            if (currentPlayer.getCoins() >= 185) {
                currentPlayer.addCoins(-185); // Trừ 185 xu
                updateFishingToolDurability(fishingTool, 10); // Sửa cần câu, đặt độ bền về 10
                typeEffect("\u001B[32m>>\u001B[0m Cần câu đã được sửa, độ bền trở lại \u001B[33m10\u001B[0m!", 5);
                savePlayerData(); // Lưu thay đổi xu và kho
            } else {
                typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mBạn không đủ xu để sửa cần câu (cần 185 xu)!\u001B[0m", 5);
            }
        } else {
            typeEffect("\u001B[33m>> Đã hủy sửa cần câu.\u001B[0m", 5);
        }
    }

    // Thêm lệnh tool
    private void handleToolCommand() throws InterruptedException {
        typeEffect("\u001B[34m\n=== Menu Công Cụ ===\u001B[0m", 5);
        typeEffect("1. Nâng cấp kho đồ (Level hiện tại: " + inventoryLevel + ", Giới hạn: " + maxInventorySlots + ")", 5);
        typeEffect("2. Sửa cần câu", 5);
        typeEffect("3. Nâng cấp máy móc", 5);
        typeEffect("\u001B[36m>> Chọn hành động (1-3): \u001B[0m", 5);

        String input = scanner.nextLine().trim();
        switch (input) {
            case "1":
                upgradeInventory();
                break;
            case "2":
                repairFishingRod();
                break;
            case "3":
                upgradeMachine();
                break;
            default:
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
        }
    }

    // Xử lý nâng cấp kho
    private void upgradeInventory() throws InterruptedException {
        int woodRequired = 2 + (inventoryLevel - 1); // 2 ván gỗ ở level 1, tăng 1 mỗi level
        int nailsRequired = 1 + (inventoryLevel - 1); // 1 đinh ở level 1, tăng 1 mỗi level

        typeEffect("\u001B[33mNâng cấp kho lên level " + (inventoryLevel + 1) + ":\u001B[0m", 5);
        typeEffect("- Cần: " + woodRequired + " ván gỗ, " + nailsRequired + " đinh", 5);
        typeEffect("- Hiện có: " + getInventoryQuantity("ván gỗ") + " ván gỗ, " + getInventoryQuantity("đinh") + " đinh", 5);

        if (kiemTraKhoHang("ván gỗ", woodRequired) && kiemTraKhoHang("đinh", nailsRequired)) {
            System.out.print("\u001B[33mXác nhận nâng cấp? (Y/N): \u001B[0m");
            String confirm = scanner.nextLine().trim().toUpperCase();
            if (confirm.equals("Y")) {
                capNhatKhoHang("ván gỗ", -woodRequired);
                capNhatKhoHang("đinh", -nailsRequired);
                inventoryLevel++;
                maxInventorySlots = 100 + (inventoryLevel - 1) * 25;
                savePlayerData();
                typeEffect("\u001B[32mĐã nâng cấp kho lên level \u001B[33m" + inventoryLevel + "! Giới hạn mới: \u001B[36m" + maxInventorySlots + "\u001B[0m", 5);
            } else {
                typeEffect("\u001B[31mĐã hủy nâng cấp!\u001B[0m", 5);
            }
        } else {
            typeEffect("\u001B[31mKhông đủ nguyên liệu để nâng cấp!\u001B[0m", 5);
        }
    }

    private void upgradeMachine() throws InterruptedException {
        // Lọc máy móc rảnh (không đang sản xuất)
        List<Machine> idleMachines = machines.stream()
                .filter(m -> m.getStatus().replaceAll("\\u001B\\[[;\\d]*m", "").trim().equals("rảnh"))
                .toList();

        if (idleMachines.isEmpty()) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mBạn không có máy móc nào rảnh để nâng cấp!\u001B[0m", 5);
            return;
        }

        // Hiển thị bảng máy móc rảnh
        typeEffect("\u001B[34m\n=== Máy móc rảnh ===\u001B[0m", 5);
        String topLine = "┌─────┬───────────────────────────┬──────────┬────────────┐";
        String header = "│ STT │ Tên máy                   │ Cấp độ   │ Ô sản xuất │";
        String midLine = "├─────┼───────────────────────────┼──────────┼────────────┤";
        String bottomLine = "└─────┴───────────────────────────┴──────────┴────────────┘";

        typeEffect("\u001B[36m" + topLine + "\u001B[0m", 2);
        typeEffect("\u001B[33m" + header + "\u001B[0m", 2);
        typeEffect("\u001B[36m" + midLine + "\u001B[0m", 2);

        int stt = 1;
        for (Machine machine : idleMachines) {
            String line = String.format("│ %3d │ %-25s │ %8d │ %11d │", stt++, machine.getMachineType(), machine.getLevel(), machine.getProductionSlots());
            typeEffect(line, 2);
        }
        typeEffect("\u001B[36m" + bottomLine + "\u001B[0m", 2);

        // Chọn máy để nâng cấp
        typeEffect("\u001B[36m>> Chọn máy để nâng cấp (1-" + idleMachines.size() + " hoặc tên): \u001B[0m", 5);
        String input = scanner.nextLine().trim();

        Machine selectedMachine = null;
        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < idleMachines.size()) {
                selectedMachine = idleMachines.get(index);
            }
        } catch (NumberFormatException e) {
            selectedMachine = idleMachines.stream()
                    .filter(m -> m.getMachineType().toLowerCase().equals(input.toLowerCase()))
                    .findFirst().orElse(null);
        }

        if (selectedMachine == null) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mMáy không hợp lệ!\u001B[0m", 5);
            return;
        }

        int currentLevel = selectedMachine.getLevel();
        int nextLevel = currentLevel + 1;

        // Kiểm tra cấp tối đa
        if (nextLevel > 5) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33m" + selectedMachine.getMachineType() + " đã đạt cấp tối đa (Level 5)!\u001B[0m", 5);
            return;
        }

        // Kiểm tra máy đang nâng cấp
        String upgradeKey = selectedMachine.getMachineType() + ":upgrade";
        if (machineUpgradeTimers.containsKey(upgradeKey)) {
            long endTime = machineUpgradeTimers.get(upgradeKey);
            long currentTime = System.currentTimeMillis() / 1000;
            if (currentTime < endTime) {
                long remainingTime = endTime - currentTime;
                typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33m" + selectedMachine.getMachineType() + " đang nâng cấp! Còn " + remainingTime + " giây.\u001B[0m", 5);
                return;
            } else {
                // Hoàn thành nâng cấp nếu hết thời gian
                selectedMachine.setLevel(nextLevel);
                machineUpgradeTimers.remove(upgradeKey);
                db.saveMachine(currentPlayer.getId(), selectedMachine.getMachineType(), selectedMachine.getLevel(), selectedMachine.getProductionSlots(), selectedMachine.getStatus());
                typeEffect("\u001B[32m>> " + selectedMachine.getMachineType() + " đã hoàn thành nâng cấp lên Level " + nextLevel + "! Ô sản xuất: " + selectedMachine.getProductionSlots() + "\u001B[0m", 5);
                savePlayerData();
                return;
            }
        }

        // Hiển thị yêu cầu nâng cấp
        int woodRequired = machineUpgradeWoodCosts.get(nextLevel);
        int nailsRequired = machineUpgradeNailCosts.get(nextLevel);
        int timeRequired = machineUpgradeTimes.get(nextLevel);

        typeEffect("\u001B[33mNâng cấp " + selectedMachine.getMachineType() + " lên Level " + nextLevel + ":\u001B[0m", 5);
        typeEffect("- Cần: " + woodRequired + " ván gỗ, " + nailsRequired + " đinh", 5);
        typeEffect("- Thời gian: " + (timeRequired / 60) + " phút (" + timeRequired + " giây)", 5);
        typeEffect("- Hiện có: " + getInventoryQuantity("ván gỗ") + " ván gỗ, " + getInventoryQuantity("đinh") + " đinh", 5);

        // Kiểm tra nguyên liệu
        if (!kiemTraKhoHang("ván gỗ", woodRequired) || !kiemTraKhoHang("đinh", nailsRequired)) {
            typeEffect("\u001B[31m<\u001B[33m!\u001B[31m> \u001B[33mKhông đủ nguyên liệu để nâng cấp!\u001B[0m", 5);
            return;
        }

        // Xác nhận nâng cấp
        typeEffect("\u001B[36m>> Xác nhận nâng cấp? (Y/N): \u001B[0m", 5);
        String confirm = scanner.nextLine().trim().toUpperCase();
        if (!confirm.equals("Y")) {
            typeEffect("\u001B[33m>> Đã hủy nâng cấp!\u001B[0m", 5);
            return;
        }

        // Trừ nguyên liệu và bắt đầu nâng cấp
        capNhatKhoHang("ván gỗ", -woodRequired);
        capNhatKhoHang("đinh", -nailsRequired);
        selectedMachine.setStatus("\u001B[33mđang nâng cấp\u001B[0m");
        long endTime = System.currentTimeMillis() / 1000 + timeRequired;
        machineUpgradeTimers.put(upgradeKey, endTime);
        db.saveMachine(currentPlayer.getId(), selectedMachine.getMachineType(), selectedMachine.getLevel(), selectedMachine.getProductionSlots(), selectedMachine.getStatus());
        db.saveCropTimers(currentPlayer.getId(), "machineUpgrade", machineUpgradeTimers); // Lưu timer nâng cấp
        typeEffect("\u001B[32m>> Đã bắt đầu nâng cấp " + selectedMachine.getMachineType() + " lên Level " + nextLevel + "! Hoàn thành sau " + (timeRequired / 60) + " phút.\u001B[0m", 5);
        savePlayerData();
    }


    // Lấy số lượng vật phẩm trong kho
    private int getInventoryQuantity(String itemName) {
        return inventory.stream()
                .filter(i -> i.getItemName().equalsIgnoreCase(itemName))
                .mapToInt(Inventory::getQuantity)
                .sum();
    }

    // Đếm độ dài hiển thị (loại bỏ mã màu ANSI)
    private static int visibleLength(String str) {
        return str.replaceAll("\u001B\\[[;\\d]*m", "").length();
    }

    // Căng lề bên phải theo độ dài thực
    private static String padRight(String str, int length) {
        int visibleLen = visibleLength(str);
        int padding = Math.max(0, length - visibleLen);
        return str + " ".repeat(padding);
    }

    // Tạo hiệu ứng cầu vồng từng chữ (cho cá huyền thoại)
    private static String toRainbow(String text) {
        int[] colorCodes = {196, 202, 208, 214, 220, 118, 45, 51, 93, 129, 201};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int color = colorCodes[i % colorCodes.length];
            result.append("\u001B[38;5;").append(color).append("m").append(text.charAt(i));
        }
        result.append("\u001B[0m");
        return result.toString();
    }

    private void xulyOrder() throws InterruptedException {
        long currentTime = System.currentTimeMillis() / 1000;

        // Reset NPC orders mỗi giờ
        if (currentTime - lastNPCReset >= NPC_RESET_INTERVAL || currentNPCOrders.isEmpty()) {
            generateNPCOrders();
            lastNPCReset = currentTime;
            db.saveNPCOrders(currentPlayer.getId(), currentNPCOrders, lastNPCReset); // Lưu vào DB
        } else {
            // Load từ DB nếu không reset
            Map<String, Object> npcData = db.loadNPCOrders(currentPlayer.getId());
            currentNPCOrders = (List<String>) npcData.getOrDefault("orders", currentNPCOrders);
            lastNPCReset = (Long) npcData.getOrDefault("lastReset", lastNPCReset);
        }

        // Hiển thị danh sách NPC orders
        if (currentNPCOrders.isEmpty()) {
            typeEffect("\u001B[33mHiện tại không có NPC nào muốn mua hàng!\u001B[0m", 5);
            return;
        }

        typeEffect("\u001B[34m\n=== Danh sách đơn hàng từ NPC ===\u001B[0m", 5);
        String header = "┌─────┬─────────────────────────────────────────────────────────────┐\n" +
                "│ \u001B[33mSTT\u001B[0m │ \u001B[33mĐơn hàng từ NPC                                            \u001B[0m │\n" +
                "├─────┼─────────────────────────────────────────────────────────────┤";
        typeEffect(header, 2);

        for (int i = 0; i < currentNPCOrders.size(); i++) {
            String order = currentNPCOrders.get(i);
            // Tách chuỗi để thêm màu cho tên sản phẩm và giá
            String[] parts = order.split(" ", 2); // Tách tên NPC và phần còn lại
            String npcName = parts[0]; // "Dì Sáu:"
            String rest = parts[1]; // Phần còn lại của câu

            // Tìm vị trí tên sản phẩm và giá
            String[] words = rest.split(" ");
            int itemIndex = -1;
            int priceIndex = -1;
            for (int j = 0; j < words.length - 1; j++) {
                if (words[j].matches("\\d+") && !words[j + 1].matches("\\d+")) {
                    itemIndex = j + 1; // Tên sản phẩm ngay sau số lượng
                    break;
                }
            }
            for (int j = words.length - 1; j >= 0; j--) {
                if (words[j].matches("\\d+")) {
                    priceIndex = j; // Giá là số cuối cùng
                    break;
                }
            }

            // Thêm màu vàng cho tên sản phẩm và giá
            StringBuilder coloredOrder = new StringBuilder(npcName + " ");
            for (int j = 0; j < words.length; j++) {
                if (j == itemIndex) {
                    coloredOrder.append("\u001B[33m").append(words[j]).append("\u001B[0m");
                } else if (j == priceIndex) {
                    coloredOrder.append("\u001B[33m").append(words[j]).append("\u001B[0m");
                } else {
                    coloredOrder.append(words[j]);
                }
                if (j < words.length - 1) coloredOrder.append(" ");
            }

            // Tính độ dài thực tế (không tính mã màu) và căn chỉnh
            String plainText = coloredOrder.toString().replaceAll("\u001B\\[[0-9]+m", "");
            int plainLength = plainText.length();
            int maxWidth = 59; // Độ rộng tối đa của cột
            StringBuilder row = new StringBuilder("│ " + String.format("%-3d", (i + 1)) + " │ " + coloredOrder.toString());
            int padding = maxWidth - plainLength;
            if (padding > 0) {
                row.append(" ".repeat(padding)); // Thêm khoảng trắng để căn chỉnh
            }
            row.append(" │");
            typeEffect(row.toString(), 2);
        }

        typeEffect("└─────┴─────────────────────────────────────────────────────────────┘", 2);

        // Chọn NPC order
        typeEffect("\u001B[36m>> Chọn đơn hàng để bán (1-" + currentNPCOrders.size() + " hoặc Enter để thoát): \u001B[0m", 5);
        String input = scanner.nextLine().trim();

        if (input.isEmpty()) {
            typeEffect("\u001B[33mĐã thoát khỏi menu NPC!\u001B[0m", 5);
            return;
        }

        try {
            int index = Integer.parseInt(input) - 1;
            if (index >= 0 && index < currentNPCOrders.size()) {
                String order = currentNPCOrders.get(index);
                String[] parts = order.split(":")[1].split(" với giá "); // Tách câu thoại và giá
                String npcMessage = parts[0].trim(); // "Tui muốn mua 5 lúa mì"
                int price = Integer.parseInt(parts[1].replaceAll("[^0-9]", "")); // Lấy số xu

                // Trích xuất tên vật phẩm và số lượng từ npcMessage
                String[] messageParts = npcMessage.split(" ");
                int qty = Integer.parseInt(messageParts[messageParts.length - 2]); // Số lượng
                String itemName = messageParts[messageParts.length - 1]; // Tên vật phẩm

                // Xác nhận bán
                typeEffect("\u001B[36m>> Bạn có muốn bán " + qty + " " + itemName + " với giá " + price + " xu không? (Y/N): \u001B[0m", 5);
                String confirm = scanner.nextLine().trim().toUpperCase();

                if (confirm.equals("Y")) {
                    if (kiemTraKhoHang(itemName, qty)) {
                        capNhatKhoHang(itemName, -qty);
                        currentPlayer.addCoins(price);
                        typeEffect("\u001B[32mĐã bán " + qty + " " + itemName + " cho NPC với giá " + price + " xu!\u001B[0m", 5);
                        currentNPCOrders.remove(index); // Xóa đơn hàng sau khi bán
                        db.saveNPCOrders(currentPlayer.getId(), currentNPCOrders, lastNPCReset);
                        savePlayerData();
                    } else {
                        typeEffect("\u001B[31mKhông đủ " + itemName + " trong kho để bán!\u001B[0m", 5);
                    }
                } else {
                    typeEffect("\u001B[33mĐã hủy giao dịch!\u001B[0m", 5);
                }
            } else {
                typeEffect("\u001B[31mLựa chọn không hợp lệ!\u001B[0m", 5);
            }
        } catch (NumberFormatException e) {
            typeEffect("\u001B[31mVui lòng nhập số hợp lệ!\u001B[0m", 5);
        }
    }

    private void generateNPCOrders() throws InterruptedException {
        currentNPCOrders.clear();

        // Danh sách tên NPC đậm chất miền Tây
        String[] npcNames = {
                "Chú Ba", "Cô Tư", "Chú Năm", "Dì Sáu", "Bác Bảy",
                "Anh Tám", "Chị Hai", "Thím Chín", "Ông Mười", "Bà Ngoại"
        };

        // Danh sách câu thoại tự nhiên, có chủ ngữ/vị ngữ, không thêm tên người chơi
        String[] npcDialogues = {
                "Lấy cho %s %d \u001B[33m%s\u001B[0m coi bây, %s đưa \u001B[33m%d\u001B[0m xu nè",
                "Bán cho %s %d \u001B[33m%s\u001B[0m đi mấy đứa, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Cho %s xin %d \u001B[33m%s\u001B[0m nghen, %s đưa \u001B[33m%d\u001B[0m xu nha",
                "Dzậy chớ, lấy cho %s %d \u001B[33m%s\u001B[0m đi, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Coi bộ ngon, bán cho %s %d \u001B[33m%s\u001B[0m đi bây, %s đưa \u001B[33m%d\u001B[0m xu nè",
                "Thèm quá, lấy cho %s %d \u001B[33m%s\u001B[0m nghen, %s đưa \u001B[33m%d\u001B[0m xu thôi",
                "Bán cho %s %d \u001B[33m%s\u001B[0m đi mấy đứa, có đứa nào ở nhà không, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Mấy cái này, cho %s %d \u001B[33m%s\u001B[0m coi, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Hợp gu quá, lấy cho %s %d \u001B[33m%s\u001B[0m đi bây, %s đưa \u001B[33m%d\u001B[0m xu nha",
                "Hổng chịu nổi, bán cho %s %d \u001B[33m%s\u001B[0m đi, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Ngon dữ ha, lấy cho %s %d \u001B[33m%s\u001B[0m coi bây, %s đưa \u001B[33m%d\u001B[0m xu thôi",
                "Má ơi mê quá, cho %s %d \u001B[33m%s\u001B[0m nghen, %s đưa \u001B[33m%d\u001B[0m xu nè",
                "Dzui quá, bán cho %s %d \u001B[33m%s\u001B[0m đi mấy đứa, %s đưa \u001B[33m%d\u001B[0m xu nghen",
                "Cái này ngon thiệt, lấy cho %s %d \u001B[33m%s\u001B[0m coi, %s đưa \u001B[33m%d\u001B[0m xu nha",
                "Hổng có là %s buồn, cho %s %d \u001B[33m%s\u001B[0m nghen, %s đưa \u001B[33m%d\u001B[0m xu thôi"
        };

        // Lấy danh sách vật phẩm trong kho, trừ công cụ
        List<Inventory> availableItems = inventory.stream()
                .filter(item -> !item.getItemName().equalsIgnoreCase("Cần Câu Cá") &&
                        !item.getItemName().equalsIgnoreCase("ván gỗ") &&
                        !item.getItemName().equalsIgnoreCase("đinh") &&
                        item.getQuantity() > 0)
                .sorted((a, b) -> b.getQuantity() - a.getQuantity()) // Sắp xếp theo số lượng giảm dần
                .collect(Collectors.toList());

        if (availableItems.isEmpty()) {
            return; // Không có vật phẩm để NPC mua
        }

        // Giới hạn tối đa 3 NPC
        int npcCount = Math.min(MAX_NPC_ORDERS, availableItems.size());
        for (int i = 0; i < npcCount; i++) {
            String npcName = npcNames[random.nextInt(npcNames.length)]; // Chọn ngẫu nhiên tên NPC
            String dialogueTemplate = npcDialogues[random.nextInt(npcDialogues.length)]; // Chọn ngẫu nhiên câu thoại
            Inventory item = availableItems.get(i);
            String itemName = item.getItemName();
            int maxQty = item.getQuantity() / 2; // Không mua quá 1/2 số lượng
            if (maxQty <= 0) continue;

            int qtyToBuy = random.nextInt(maxQty) + 1; // Random từ 1 đến maxQty
            int marketPrice = getItemPrice(itemName); // Giá thị trường hiện tại
            int discountedPrice = (int) (marketPrice * qtyToBuy * 0.85); // Giảm 15% từ giá thị trường

            // Ghép câu thoại hoàn chỉnh
            String order = String.format(dialogueTemplate, npcName, qtyToBuy, itemName, npcName, discountedPrice);
            currentNPCOrders.add(order);
        }
    }

    private static String rainbowText(String text) {
        String[] colors = {"\u001B[31m", "\u001B[33m", "\u001B[32m", "\u001B[36m", "\u001B[34m", "\u001B[35m"};
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            result.append(colors[i % colors.length]).append(text.charAt(i));
        }
        result.append("\u001B[0m");
        return result.toString();
    }

} // end