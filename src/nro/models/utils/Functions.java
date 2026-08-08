package nro.models.utils;

import java.util.Random;
import java.util.regex.Pattern;
import nro.models.player.Player;

/**
 *
 * @author By Mr Blue
 * 
 */

public class Functions {

    private static final String REGEX = "(?i)\\b(dkm|đkm|đbrr|địt|đĩ|đỹ|cm|cmm|lồn|buồi|cc|ôm cl|mẹ mày|cặc|đụ|fuck|damn|clmm|dcmm|cl|tml|đ*t|c*c|dit|d*t|c.a.c|l.o.n|c.ặ.c|l.ồ.n|b.u.ồ.i|bu*i|đặc cầu|đồn lầu|bú cu|buscu|đm|cc|đb|db|lol|nhu lon|nhu cac|vc|vl|vãi|đéo|đờ mờ|đờ cờ mờ|clgt|dell|mẹ|cứt|shit|idiot|khốn|xiên chết|cụ|giao phối|thiểu năng|ngáo|chó|dog|đcmm|vcl|vkl|đ!t|d!t|đỵt|dyt|ngu|óc|.com|.net|.online|.vn|.pw|.pro|.org|.info|.ml|.ga|.gq|.cf|.fun|.xyz|.io|.club)\\b";
    private static final Pattern pattern = Pattern.compile(REGEX);

    public static boolean isSpam(Player player, String text) {
        return pattern.matcher(text).find() && !"spamcc".equals(player.name);
    }

    public static int maxint(long n) {
        return (int) Math.min(n, Integer.MAX_VALUE);
    }
    
    public static String generateRandomCharacters(int quantity) {
        StringBuilder sb = new StringBuilder(quantity);
        Random random = new Random();

        for (int i = 0; i < quantity; i++) {
            char generatedChar = (char) (random.nextInt(36) + (random.nextInt(2) == 0 ? '0' : 'A'));
            sb.append(generatedChar);
        }

        return sb.toString();
    }

    public static void sleep(long l) {
        try {
            Thread.sleep(l);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
