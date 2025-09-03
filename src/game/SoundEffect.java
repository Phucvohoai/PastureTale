package game;

import javax.sound.sampled.*;
import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class SoundEffect {
    private static final Map<String, Clip> soundMap = new HashMap<>();

    // Gọi một lần lúc khởi động game
    public static void preloadSounds(String folderPath) {
        File folder = new File(folderPath);
        if (!folder.exists() || !folder.isDirectory()) {
            System.out.println("⚠️ Thư mục âm thanh không tồn tại: " + folderPath);
            return;
        }

        File[] files = folder.listFiles((dir, name) -> name.toLowerCase().endsWith(".wav"));
        if (files == null) return;

        for (File file : files) {
            try {
                AudioInputStream inputStream = AudioSystem.getAudioInputStream(file);
                Clip clip = AudioSystem.getClip();
                clip.open(inputStream);
                soundMap.put(file.getName(), clip);
//                System.out.println("✅ Preloaded: " + file.getName());
            } catch (Exception e) {
                System.out.println("❌ Lỗi load " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    // Phát âm thanh theo tên file
    public static void playSound(String fileName) {
        Clip clip = soundMap.get(fileName);
        if (clip != null) {
            if (clip.isRunning()) clip.stop();
            clip.setFramePosition(0);
            clip.start();
        } else {
            System.out.println("⚠️ Không tìm thấy âm thanh: " + fileName);
        }
    }
}
