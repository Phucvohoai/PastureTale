package game;

import model.Player;
import model.Farm;
import model.Inventory;
import db.DatabaseHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class FarmGame {
    private DatabaseHelper db;
    private Player currentPlayer;
    private Farm farm;
    private List<Inventory> inventory;
    private CommandHandler commandHandler;
    private Scanner scanner;

    public FarmGame() {
        db = new DatabaseHelper();
        scanner = new Scanner(System.in);
        inventory = new ArrayList<>();
//        commandHandler = new CommandHandler(db);
    }

    public void start() {
        while (true) {
            System.out.println("\u001B[36m");
            System.out.println("=========================================\u001B[0m");
            System.out.println("\u001B[33m>>>\u001B[0m   🐓 SHEEPVALLEY - ĐĂNG NHẬP 🌾   \u001B[33m<<<\u001B[0m");
            System.out.println("\u001B[36m=========================================\u001B[0m");
            System.out.println("1. r - Đăng ký");
            System.out.println("2. l - Đăng nhập");
            System.out.println("3. e - Thoát");
            System.out.print("\u001B[33mNhập lệnh: \u001B[0m");
            String command = scanner.nextLine();

            switch (command) {
                case "r":
                    register();
                    break;
                case "l":
                    if (login()) {
                        try {
                            commandHandler = new CommandHandler(currentPlayer, db); // Bọc trong try-catch
                            gameLoop();
                        } catch (InterruptedException e) {
                            System.err.println("\u001B[31mLỗi khi khởi tạo game: " + e.getMessage() + "\u001B[0m");
                            e.printStackTrace();
                        }
                    }
                    break;
                case "e":
                    System.out.println("\u001B[31mTạm biệt!\u001B[0m");
                    return;
                default:
                    System.out.println("\u001B[31mLệnh không hợp lệ!\u001B[0m");
            }
        }
    }

    private void register() {
        System.out.print("Nhập tên đăng nhập: ");
        String username = scanner.nextLine();
        System.out.print("Nhập mật khẩu: ");
        String password = scanner.nextLine();
        if (db.registerPlayer(username, password)) {
            currentPlayer = db.loginPlayer(username, password); // Đăng nhập ngay để lấy thông tin người chơi
            try {
                commandHandler = new CommandHandler(currentPlayer, db); // Bọc trong try-catch
                commandHandler.resetDailyContent(); // Khởi tạo nhiệm vụ và đơn hàng ngay sau khi đăng ký
                welcomeNewUser(username); // Gọi lời chào và hướng dẫn
                gameLoop(); // Chuyển thẳng vào game loop sau khi đăng ký
            } catch (InterruptedException e) {
                System.err.println("\u001B[31mLỗi khi khởi tạo game cho người chơi mới: " + e.getMessage() + "\u001B[0m");
                e.printStackTrace();
            }
        }
    }

    private boolean login() {
        System.out.print("Nhập tên đăng nhập: ");
        String username = scanner.nextLine();
        System.out.print("Nhập mật khẩu: ");
        String password = scanner.nextLine();
        currentPlayer = db.loginPlayer(username, password);
        if (currentPlayer != null) {
            System.out.println();
            System.out.println("\u001B[36m──────────────────────────────────────────────\u001B[0m");
            System.out.println("\u001B[33m>>>\u001B[0m  Đăng nhập thành công! Chào mừng\u001B[33m " + currentPlayer.getUsername() + "!\u001B[0m");
            System.out.println("\u001B[36m──────────────────────────────────────────────\u001B[0m");
            return true;
        } else {
            System.out.println("\u001B[31mTên đăng nhập hoặc mật khẩu không đúng!\u001B[0m");
            return false;
        }
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

    private void welcomeNewUser(String username) {
        try {
        typeEffect("\u001B[34m==========================================\u001B[0m", 2);
        typeEffect("\u001B[32m🌾 Chào mừng " + username + " đến với \u001B[33m SheepValley!\u001B[0m 🌾\u001B[0m", 2);
        typeEffect("\u001B[34m==========================================\u001B[0m", 2);
        typeEffect("\u001B[33m✨ NPC Nông Dân Vui Vẻ: \u001B[0mChào bạn \u001B[36m" + username + "\u001B[0m! Tớ là Nông Dân Vui Vẻ đây!", 2);
        typeEffect("🌞 Ồ, cậu vừa đặt chân đến một vùng đất tuyệt vời, nơi cây cối xanh mướt, vật nuôi dễ thương và những cuộc phiêu lưu nông trại đang chờ đón!", 2);
        typeEffect("🐓 Tớ sẽ là người bạn đồng hành, dẫn cậu qua những ngày đầu tiên ở\u001B[33m SheepValley\u001B[0m. Đừng lo, mọi thứ siêu đơn giản và vui lắm luôn!", 2);
        typeEffect("\u001B[34m------------------------------------------\u001B[0m", 2);

        // Hướng dẫn chi tiết
        typeEffect("\u001B[36m🎯 Bắt đầu cuộc sống nông trại thế nào đây?\u001B[0m", 2);
        typeEffect("🌟 Tớ sẽ chỉ cậu vài bước cơ bản để biến cậu thành một nông dân siêu đỉnh, sẵn sàng chưa?", 2);
        typeEffect("  \u001B[32m1. Khám phá trang trại của cậu:\u001B[0m", 2);
        typeEffect("     - Gõ \u001B[32m'farm'\u001B[0m để xem mảnh đất của cậu trông ra sao! Có đất trồng, chuồng trại và cả kho hàng nữa!", 2);
        typeEffect("     - Lúc đầu hơi trống trải, nhưng đừng lo, cậu sẽ sớm phủ đầy nó bằng cây cối và vật nuôi thôi!", 2);
        typeEffect("  \u001B[32m2. Trồng cây đầu tiên:\u001B[0m", 2);
        typeEffect("     - Cậu đã được tặng \u001B[33m10 hạt lúa mì\u001B[0m để khởi đầu! Gõ \u001B[32m'plant'\u001B[0m rồi chọn 'hạt lúa mì' để gieo chúng xuống đất.", 2);
        typeEffect("     - Chờ một chút, chúng sẽ lớn lên thành lúa mì thơm ngon để cậu thu hoạch đó!", 2);
        typeEffect("  \u001B[32m3. Đi mua sắm hạt giống:\u001B[0m", 2);
        typeEffect("     - Hết hạt giống? Gõ \u001B[32m'shop'\u001B[0m để vào cửa hàng! Ở đó có đủ loại hạt: lúa mì, bắp, ớt... tha hồ chọn!", 2);
        typeEffect("     - Dùng \u001B[33mxu\u001B[0m cậu có để mua, tớ tin cậu sẽ sớm giàu lên thôi!", 2);
        typeEffect("  \u001B[32m4. Thu hoạch và kiếm xu:\u001B[0m", 2);
        typeEffect("     - Khi cây chín (kiểm tra bằng \u001B[32m'check'\u001B[0m), gõ \u001B[32m'collect'\u001B[0m để thu hoạch! Lúa mì sẽ vào kho, bán đi là có xu ngay!", 2);
        typeEffect("     - Xu dùng để mua hạt, vật nuôi, máy móc... cứ tích lũy dần nhé!", 2);
        typeEffect("  \u001B[32m5. Khám phá thêm:\u001B[0m", 2);
        typeEffect("     - Muốn biết thêm lệnh? Gõ \u001B[32m'help'\u001B[0m, tớ sẽ liệt kê hết cho cậu! Từ nuôi gà, làm bánh mì, đến giao hàng xe tải, chơi hoài không chán đâu!", 2);

        // Kết thúc sinh động
        typeEffect("\u001B[34m------------------------------------------\u001B[0m", 2);
        typeEffect("\u001B[33m🌈 Nông Dân Vui Vẻ: \u001B[0mThế nào, hào hứng chưa? Tớ cá là cậu sẽ mê mẩn cái trang trại này ngay thôi!", 2);
        typeEffect("🐄 Cứ từ từ khám phá, trồng trọt, chăn nuôi, chế biến... mọi thứ đều trong tay cậu! Có gì thắc mắc, cứ gọi tớ bằng \u001B[32m'help'\u001B[0m nhé!", 2);
        typeEffect("\u001B[32mChúc cậu có những ngày thật vui ở \u001B[33m SheepValley!\u001B[0m Bắt đầu nào, bạn của tớ!\u001B[0m", 2);
        typeEffect("\u001B[34m==========================================\u001B[0m", 2);
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Khôi phục trạng thái ngắt
        System.err.println("Lỗi: Hiệu ứng chữ bị gián đoạn!");
    }
}

    private void gameLoop() {
        while (true) {
//            System.out.println("\u001B[36m");
//            System.out.println("========================================\u001B[0m");
//            System.out.println("            MENU TRANG TRẠI  ");
//            System.out.println("\u001B[36m========================================\u001B[0m");
//            System.out.println("\u001B[32mDanh sách lệnh khả dụng:\u001B[0m");
//            System.out.println("┌──────────────┬────────────────────────────────────────────┐");
//            System.out.println("│ Lệnh         │ Chức năng                                  │");
//            System.out.println("├──────────────┼────────────────────────────────────────────┤");
//            System.out.println("│ farm         │ Xem thông tin trang trại của bạn           │");
//            System.out.println("│ plant        │ Trồng cây (rice, corn, tomato,...)         │");
//            System.out.println("│ harvest      │ Thu hoạch cây trồng đã trưởng thành        │");
//            System.out.println("│ feed         │ Cho vật nuôi ăn (chicken, cow,...)         │");
//            System.out.println("│ collect      │ Thu sản phẩm từ vật nuôi (egg, milk,...)   │");
//            System.out.println("│ craft        │ Chế biến sản phẩm (bread, bbq,...)         │");
//            System.out.println("│ sell         │ Bán sản phẩm để kiếm xu                    │");
//            System.out.println("│ buy          │ Mua nguyên liệu từ cửa hàng                │");
//            System.out.println("│ shop         │ Xem cửa hàng hôm nay                       │");
//            System.out.println("│ order        │ Nhận đơn hàng từ NPC                       │");
//            System.out.println("│ truck        │ Giao hàng bằng xe tải                      │");
//            System.out.println("│ tasks        │ Xem nhiệm vụ hàng tuần                     │");
//            System.out.println("│ logout       │ Đăng xuất khỏi trò chơi                    │");
//            System.out.println("└──────────────┴────────────────────────────────────────────┘");
            System.out.print("\n>> \u001B[33mNhập lệnh: \u001B[0m");
            String command = scanner.nextLine();

            if (command.equals("logout")) {
                System.out.println("\u001B[33m>> \u001B[31mĐăng Xuất Thành Công!\u001B[33m <<\u001B[0m");
                break;
            }

            try {
                commandHandler.xuLyLenh(command);
            } catch (InterruptedException e) {
                System.out.println("\u001B[31mLỗi khi xử lý lệnh! Hãy thử lại.\u001B[0m");
                e.printStackTrace();
            }
        }
    }
}