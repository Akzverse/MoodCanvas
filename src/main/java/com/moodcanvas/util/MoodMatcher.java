package com.moodcanvas.util;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Turns a free-form check-in or imported Spotify music list into an understandable mood and museum search phrase. */
public final class MoodMatcher {
    public record Match(String tag, String term) { }

    private record Mood(List<String> words, String searchTerm) { }

    private static final Map<String, Mood> MOODS = new LinkedHashMap<>();

    static {
        MOODS.put("melancholic", new Mood(List.of(
                "sad", "lonely", "empty", "lost", "blue", "grief", "grieving", "hurt", "upset", "down",
                "heartbroken", "disappointed", "cry", "crying", "dark", "tears", "sorrow", "pain",
                "cold", "alone", "goodbye", "death", "die", "broken", "shadow", "rain", "rainy",
                "melancholy", "melancholic", "blues", "slow", "gray", "grey", "gloom", "bleak"
        ), "solitude rain"));

        MOODS.put("dreamy", new Mood(List.of(
                "dreamy", "dream", "wonder", "imagine", "imagination", "float", "soft", "curious",
                "inspired", "hopeful", "wondering", "stars", "star", "space", "sky", "cloud",
                "clouds", "moon", "moonlight", "lucid", "magic", "universe", "ethereal", "sleep",
                "fly", "fantasy", "cosmic", "night", "astronomy", "mystic", "illusion", "psychedelic"
        ), "moonlight"));

        MOODS.put("joyful", new Mood(List.of(
                "happy", "happier", "excited", "joyful", "joy", "bright", "energetic", "cheerful",
                "grateful", "thrilled", "celebrating", "celebration", "fun", "party", "dance",
                "disco", "sun", "sunshine", "summer", "shine", "smile", "gold", "golden",
                "fire", "light", "energy", "groove", "jump", "sweet", "paradise", "vibrant",
                "pop", "upbeat", "alive", "cheer"
        ), "sunlit flowers"));

        MOODS.put("calm", new Mood(List.of(
                "calm", "peaceful", "still", "quiet", "serene", "relaxed", "relax", "stress",
                "stressed", "anxious", "anxiety", "nervous", "overwhelmed", "worried", "rest",
                "tired", "acoustic", "piano", "chill", "ambient", "river", "water", "forest",
                "green", "lake", "morning", "whisper", "meditation", "lofi", "lo-fi", "nature",
                "zen", "ease", "breathe", "peace", "harmony"
        ), "peaceful landscape"));

        MOODS.put("romantic", new Mood(List.of(
                "love", "romantic", "warm", "tender", "longing", "affection", "miss", "beloved",
                "heart", "intimate", "lovers", "lover", "kiss", "kissing", "darling", "baby",
                "romance", "passion", "desire", "forever", "sweetheart", "belong", "eyes", "embrace",
                "crush", "sensual", "warmth"
        ), "lovers"));

        MOODS.put("reflective", new Mood(List.of(
                "think", "thinking", "reflect", "reflection", "ponder", "deep", "thoughtful",
                "nostalgic", "memory", "memories", "remember", "confused", "change", "time",
                "years", "story", "soul", "mind", "road", "home", "journey", "mirror", "echo",
                "classical", "folk", "acoustic", "searching", "vintage", "retro", "past", "dusk",
                "solitary", "reading"
        ), "reading portrait dusk"));
    }

    private MoodMatcher() { }

    public static Match match(String text) {
        if (text == null || text.isBlank()) {
            return new Match("dreamy", MOODS.get("dreamy").searchTerm());
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        String selectedTag = null;
        int bestScore = 0;

        for (Map.Entry<String, Mood> entry : MOODS.entrySet()) {
            int score = 0;
            for (String word : entry.getValue().words()) {
                if (Pattern.compile("\\b" + Pattern.quote(word) + "\\b").matcher(normalized).find()) {
                    score++;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                selectedTag = entry.getKey();
            }
        }

        // If no dictionary keyword matched, check if any mood tag itself is directly mentioned
        if (selectedTag == null) {
            for (String tag : MOODS.keySet()) {
                if (normalized.contains(tag)) {
                    selectedTag = tag;
                    break;
                }
            }
        }

        // If still no direct match, provide a varied deterministic default based on hash
        if (selectedTag == null) {
            String[] tags = MOODS.keySet().toArray(new String[0]);
            int idx = Math.abs(normalized.hashCode()) % tags.length;
            selectedTag = tags[idx];
        }

        return new Match(selectedTag, MOODS.get(selectedTag).searchTerm());
    }
}
