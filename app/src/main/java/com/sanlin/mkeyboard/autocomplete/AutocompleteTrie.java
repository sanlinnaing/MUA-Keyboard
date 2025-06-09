package com.sanlin.mkeyboard.autocomplete;

import java.util.*;

public class AutocompleteTrie {

    static class Node {
        Map<Character, Node> children = new HashMap<>();
        boolean isWord = false;
        int score = 0; // Higher = more relevant
    }

    private final Node root = new Node();

    public void insert(String word, int score) {
        Node curr = root;
        for (char c : word.toCharArray()) {
            curr = curr.children.computeIfAbsent(c, k -> new Node());
        }
        curr.isWord = true;
        curr.score = score;
    }

    public List<String> getSuggestions(String prefix, int maxResults) {
        List<Suggestion> results = new ArrayList<>();
        Node node = root;

        for (char c : prefix.toCharArray()) {
            if (!node.children.containsKey(c)) return Collections.emptyList();
            node = node.children.get(c);
        }

        collectSuggestions(node, new StringBuilder(prefix), results);

        // Sort by score
        results.sort((a, b) -> Integer.compare(b.score, a.score));

        List<String> finalResults = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, results.size()); i++) {
            finalResults.add(results.get(i).word);
        }

        return finalResults;
    }

    private void collectSuggestions(Node node, StringBuilder path, List<Suggestion> results) {
        if (node.isWord) {
            results.add(new Suggestion(path.toString(), node.score));
        }
        for (Map.Entry<Character, Node> entry : node.children.entrySet()) {
            path.append(entry.getKey());
            collectSuggestions(entry.getValue(), path, results);
            path.setLength(path.length() - 1);
        }
    }

    static class Suggestion {
        String word;
        int score;

        Suggestion(String word, int score) {
            this.word = word;
            this.score = score;
        }
    }
}

