import game.FarmGame;
import game.SoundEffect;
import game.BackgroundSound;


public class Main {
    public static void main(String[] args) {
        try {
            SoundEffect.preloadSounds("sound");
            new Thread(() -> BackgroundSound.playLoop("sound/background_sound.wav")).start();
            typeEffect("\u001B[33mGame Running \u001B[32m...\u001B[0m", 5); // Giảm từ 50 xuống 20
            System.out.println();
            typeEffect("\u001B[36mBắt đầu thôi!", 2);
            System.out.println();
            typeEffect("\u001B[35mVersion \u001B[33m1\u001B[35m.\u001B[33m0\u001B[35m.\u001B[33m3\u001B[35m", 2);
            System.out.println();

            FarmGame game = new FarmGame();
            game.start();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void typeEffect(String text, int delay) throws InterruptedException {
        for (char c : text.toCharArray()) {
            System.out.print(c);
            System.out.flush();
            Thread.sleep(delay);
        }
    }
}