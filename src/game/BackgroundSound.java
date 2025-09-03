package game;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

public class BackgroundSound {
    private static Clip backgroundClip;

    public static void playLoop(String filePath) {
        try {
            File audioFile = new File(filePath);
            if (!audioFile.exists()) {
                System.err.println(">> Không tìm thấy file âm thanh: " + filePath);
                return;
            }

            AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
            backgroundClip = AudioSystem.getClip();
            backgroundClip.open(audioStream);

            // Giảm âm lượng (0.0f = nhỏ nhất, 1.0f = bình thường)
            FloatControl volume = (FloatControl) backgroundClip.getControl(FloatControl.Type.MASTER_GAIN);
            volume.setValue(-15.0f); // ~ nhỏ hơn 70% bình thường (có thể chỉnh -10, -20...)

            backgroundClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (UnsupportedAudioFileException | IOException | LineUnavailableException e) {
            System.err.println(">> Lỗi khi phát background sound: " + e.getMessage());
        }
    }

    public static void stop() {
        if (backgroundClip != null && backgroundClip.isRunning()) {
            backgroundClip.stop();
            backgroundClip.close();
        }
    }
}
