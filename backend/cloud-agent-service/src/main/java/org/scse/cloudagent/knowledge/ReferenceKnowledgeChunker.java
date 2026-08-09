package org.scse.cloudagent.knowledge;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ReferenceKnowledgeChunker {
    private static final int MAX_CHARS = 5_000;
    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^#{1,6}\\s+(.+?)\\s*$");
    private static final Pattern ASCIIDOC_HEADING = Pattern.compile("^={1,6}\\s+(.+?)\\s*$");

    public List<ReferenceKnowledgeChunk> chunk(String title, String rawContent) {
        String content = normalize(rawContent);
        if (content.isBlank()) return List.of();

        List<Integer> starts = lineStarts(content);
        List<Section> sections = sections(title, content, starts);
        List<ReferenceKnowledgeChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            split(section, content, starts, chunks);
        }
        return List.copyOf(chunks);
    }

    static String normalize(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n').stripTrailing();
    }

    private List<Section> sections(String title, String content, List<Integer> starts) {
        List<Section> sections = new ArrayList<>();
        int currentStart = 0;
        String currentHeading = title;
        for (int line = 0; line < starts.size(); line++) {
            int start = starts.get(line);
            int end = lineEnd(content, starts, line);
            String lineText = content.substring(start, end).strip();
            String heading = heading(lineText, content, starts, line);
            if (heading == null || start == 0) {
                if (heading != null) currentHeading = heading;
                continue;
            }
            if (start > currentStart) sections.add(new Section(currentStart, start, currentHeading));
            currentStart = start;
            currentHeading = heading;
        }
        if (currentStart < content.length()) sections.add(new Section(currentStart, content.length(), currentHeading));
        return sections.isEmpty() ? List.of(new Section(0, content.length(), title)) : sections;
    }

    private String heading(String line, String content, List<Integer> starts, int lineIndex) {
        Matcher markdown = MARKDOWN_HEADING.matcher(line);
        if (markdown.matches()) return markdown.group(1).trim();
        Matcher asciidoc = ASCIIDOC_HEADING.matcher(line);
        if (asciidoc.matches()) return asciidoc.group(1).trim();
        if (lineIndex + 1 < starts.size() && !line.isBlank()) {
            String underline = content.substring(starts.get(lineIndex + 1),
                    lineEnd(content, starts, lineIndex + 1)).strip();
            if (underline.matches("[=\\-~^\"`:+*#<>]{3,}")) return line;
        }
        return null;
    }

    private void split(Section section, String content, List<Integer> starts,
                       List<ReferenceKnowledgeChunk> chunks) {
        int cursor = section.start();
        while (cursor < section.end()) {
            int target = Math.min(section.end(), cursor + MAX_CHARS);
            int end = target;
            if (target < section.end()) {
                int newline = content.lastIndexOf('\n', target);
                if (newline > cursor) end = newline;
            }
            if (end <= cursor) end = target;
            String piece = content.substring(cursor, end).strip();
            if (!piece.isEmpty()) {
                int actualStart = cursor + leadingWhitespace(content, cursor, end);
                int actualEnd = actualStart + piece.length();
                chunks.add(new ReferenceKnowledgeChunk(chunks.size(), section.heading(),
                        lineNumber(starts, actualStart), lineNumber(starts, Math.max(actualStart, actualEnd - 1)),
                        actualStart, actualEnd, piece, KnowledgeHashing.sha256(piece)));
            }
            cursor = end;
            while (cursor < section.end() && Character.isWhitespace(content.charAt(cursor))) cursor++;
        }
    }

    private int leadingWhitespace(String content, int start, int end) {
        int cursor = start;
        while (cursor < end && Character.isWhitespace(content.charAt(cursor))) cursor++;
        return cursor - start;
    }

    private List<Integer> lineStarts(String content) {
        List<Integer> starts = new ArrayList<>();
        starts.add(0);
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n' && i + 1 < content.length()) starts.add(i + 1);
        }
        return starts;
    }

    private int lineNumber(List<Integer> starts, int offset) {
        int low = 0;
        int high = starts.size() - 1;
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (starts.get(mid) <= offset) low = mid + 1;
            else high = mid - 1;
        }
        return high + 1;
    }

    private int lineEnd(String content, List<Integer> starts, int line) {
        int next = line + 1 < starts.size() ? starts.get(line + 1) - 1 : content.length();
        return Math.max(starts.get(line), next);
    }

    private record Section(int start, int end, String heading) {
    }
}
