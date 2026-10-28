package net.lunacore.luna_npc.client.util;

// Forgiving matching for every in-game search bar. It is insensitive to case, to punctuation
// (so "iron_golem", "iron golem" and " golem" all behave), and to word order, and it tolerates a
// few typos per word so "creper" still finds the creeper. score() also ranks matches, letting the
// closest result float to the top; matches() is the plain yes/no on top of it.
public final class FuzzySearch {

    private FuzzySearch() {
    }

    public static boolean matches(String query, String... haystacks) {
        return score(query, haystacks) >= 0;
    }

    // Higher is a closer match; -1 means the query does not match at all. Every whitespace-separated
    // word the user typed must land somewhere in the combined haystack, in any order.
    public static int score(String query, String... haystacks) {
        String needle = normalize(query);
        if (needle.isEmpty()) {
            return 0;
        }
        String hay = joinNormalized(haystacks);
        if (hay.isEmpty()) {
            return -1;
        }
        String[] words = hay.split(" ");
        int total = 0;
        for (String token : needle.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            int best = tokenScore(token, hay, words);
            if (best < 0) {
                return -1;
            }
            total += best;
        }
        return total;
    }

    private static int tokenScore(String token, String hay, String[] words) {
        int best = -1;
        for (String word : words) {
            if (word.equals(token)) {
                return 100;
            }
            if (word.startsWith(token)) {
                best = Math.max(best, 80);
            }
        }
        if (best < 0 && hay.contains(token)) {
            best = 60;
        }
        if (best >= 0) {
            return best;
        }
        int allowed = token.length() <= 3 ? 0 : token.length() <= 6 ? 1 : 2;
        if (allowed == 0) {
            return -1;
        }
        int nearest = allowed + 1;
        for (String word : words) {
            nearest = Math.min(nearest, levenshtein(token, word, allowed));
        }
        return nearest <= allowed ? 40 - (nearest - 1) * 15 : -1;
    }

    // Bounded edit distance: bails out as soon as the whole row can only exceed the tolerance, so it
    // stays cheap even against thousands of registry entries.
    private static int levenshtein(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) {
            return max + 1;
        }
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            int rowMin = curr[0];
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= b.length(); j++) {
                int cost = ca == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(prev[j] + 1, curr[j - 1] + 1), prev[j - 1] + cost);
                rowMin = Math.min(rowMin, curr[j]);
            }
            if (rowMin > max) {
                return max + 1;
            }
            int[] swap = prev;
            prev = curr;
            curr = swap;
        }
        return prev[b.length()];
    }

    // Lowercase, and turn every run of non-alphanumeric characters into a single space, so ids,
    // display names and tags all reduce to the same word-separated form.
    private static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean pendingSpace = false;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                if (pendingSpace && sb.length() > 0) {
                    sb.append(' ');
                }
                pendingSpace = false;
                sb.append(c);
            } else {
                pendingSpace = true;
            }
        }
        return sb.toString();
    }

    private static String joinNormalized(String[] haystacks) {
        StringBuilder sb = new StringBuilder();
        for (String haystack : haystacks) {
            if (haystack == null || haystack.isEmpty()) {
                continue;
            }
            String normalized = normalize(haystack);
            if (normalized.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(normalized);
        }
        return sb.toString();
    }
}
