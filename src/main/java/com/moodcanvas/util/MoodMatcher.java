package com.moodcanvas.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Turns a free-form check-in into an understandable mood and museum search phrase. */
public final class MoodMatcher {
    public record Match(String tag, String term) { }

    private record Mood(List<String> words, String searchTerm) { }

    private static final Map<String, Mood> MOODS = new LinkedHashMap<>();

    static {
        MOODS.put("melancholic", new Mood(List.of("sad", "lonely", "empty", "lost", "blue", "grief", "grieving", "hurt", "upset", "down", "heartbroken", "disappointed"), "rainy street night portrait solitude"));
        MOODS.put("dreamy", new Mood(List.of("dreamy", "dream", "wonder", "imagine", "imagination", "float", "soft", "curious", "inspired", "hopeful", "wondering"), "moonlight water clouds garden"));
        MOODS.put("joyful", new Mood(List.of("happy", "happier", "excited", "joyful", "joy", "bright", "energetic", "cheerful", "grateful", "thrilled", "celebrating", "celebration", "fun"), "sunlit garden flowers celebration"));
        MOODS.put("calm", new Mood(List.of("calm", "peaceful", "still", "quiet", "serene", "relaxed", "relax", "stress", "stressed", "anxious", "anxiety", "nervous", "overwhelmed", "worried", "rest", "tired"), "quiet lake mountain landscape"));
        MOODS.put("romantic", new Mood(List.of("love", "romantic", "warm", "tender", "longing", "affection", "miss", "beloved", "heart", "intimate"), "lovers embrace garden flowers"));
        MOODS.put("reflective", new Mood(List.of("think", "thinking", "reflect", "reflection", "ponder", "deep", "thoughtful", "nostalgic", "memory", "memories", "remember", "confused", "change"), "woman reading interior dusk portrait"));
    }

    private MoodMatcher() { }

    public static Match match(String text) {
        String normalized = text.toLowerCase(Locale.ROOT);
        String selectedTag = "dreamy";
        int bestScore = 0;
        for (Map.Entry<String, Mood> entry : MOODS.entrySet()) {
            int score = 0;
            for (String word : entry.getValue().words()) {
                if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(normalized).find()) score++;
            }
            if (score > bestScore) {
                bestScore = score;
                selectedTag = entry.getKey();
            }
        }
        return new Match(selectedTag, MOODS.get(selectedTag).searchTerm());
    }
}
