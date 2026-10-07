package cn.aqcraft.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置文件增量合并工具。
 *
 * <p>把「jar 内置默认 config.yml」中、玩家磁盘 config.yml 里缺失的配置项
 * <b>以纯文本方式插入</b>到玩家文件中：</p>
 * <ul>
 *   <li>缺失的顶层键 / 段落 → 追加到文件末尾（连同其上方注释）；</li>
 *   <li>缺失的嵌套键（其父段落已存在）→ 插入到该段落末尾（连同其上方注释）；</li>
 *   <li><b>绝不修改或删除玩家已有的任何行</b>（保留注释、顺序、自定义值）。</li>
 * </ul>
 *
 * <p>仅做基于缩进的行级解析，适用于本插件使用的标准 2 空格缩进 YAML。</p>
 */
public final class ConfigMerger {

    private ConfigMerger() {
    }

    public static final class Result {
        public final String text;
        public final boolean changed;
        public final List<String> added;

        Result(String text, boolean changed, List<String> added) {
            this.text = text;
            this.changed = changed;
            this.added = added;
        }
    }

    /** 合并：返回应在玩家文件中使用的新文本（未变化时返回原文）。 */
    public static Result merge(String defText, String curText) {
        List<String> defLines = split(defText);
        List<String> curLines = split(curText);
        List<Node> defTop = parse(defLines, 0, 0, defLines.size()).nodes;
        ParseResult curParsed = parse(curLines, 0, 0, curLines.size());
        List<Node> curTop = curParsed.nodes;

        Map<String, Node> curByKey = new LinkedHashMap<>();
        for (Node n : curTop) curByKey.put(n.key, n);

        List<String> added = new ArrayList<>();
        // 收集插入任务：idx（插入到该行号之前）+ 要插入的行块
        List<int[]> idxs = new ArrayList<>();
        List<List<String>> blocks = new ArrayList<>();

        // 1) 顶层新增（整段或单键）→ 追加到末尾
        for (Node dn : defTop) {
            if (curByKey.containsKey(dn.key)) continue;
            List<String> block = new ArrayList<>(dn.pre);
            block.add(dn.line);
            block.addAll(dn.childLines);
            idxs.add(new int[]{curLines.size()});
            blocks.add(block);
            added.add(dn.key);
        }

        // 2) 嵌套新增（父段落已存在）→ 插入到该段落末尾
        for (Node dn : defTop) {
            Node cn = curByKey.get(dn.key);
            if (cn == null || dn.children.isEmpty()) continue;
            java.util.Set<String> existing = new java.util.HashSet<>();
            for (Node cc : cn.children) existing.add(cc.key);
            List<String> block = new ArrayList<>();
            for (Node dchild : dn.children) {
                if (existing.contains(dchild.key)) continue;
                if (!block.isEmpty()) block.add("");
                block.addAll(dchild.pre);
                block.add(dchild.line);
                block.addAll(dchild.childLines);
                added.add(dn.key + "." + dchild.key);
            }
            if (!block.isEmpty()) {
                idxs.add(new int[]{cn.endLine});
                blocks.add(block);
            }
        }

        if (added.isEmpty()) {
            return new Result(curText, false, added);
        }

        // 合并同一插入点的块（保持先后顺序）
        Map<Integer, List<String>> byIdx = new LinkedHashMap<>();
        for (int k = 0; k < idxs.size(); k++) {
            int idx = idxs.get(k)[0];
            byIdx.computeIfAbsent(idx, x -> new ArrayList<>()).addAll(blocks.get(k));
        }

        // 从后往前插入，避免行号偏移
        List<Integer> positions = new ArrayList<>(byIdx.keySet());
        positions.sort((a, b) -> b - a);
        List<String> out = new ArrayList<>(curLines);
        for (int idx : positions) {
            List<String> toAdd = byIdx.get(idx);
            // 追加到末尾时若前面不是空行则先补一个空行分隔
            if (idx >= out.size() && !toAdd.isEmpty() && !out.isEmpty() && !out.get(out.size() - 1).trim().isEmpty()) {
                List<String> withSep = new ArrayList<>();
                withSep.add("");
                withSep.addAll(toAdd);
                toAdd = withSep;
            }
            out.addAll(Math.min(idx, out.size()), toAdd);
        }

        // 确保以换行结尾
        String text = String.join("\n", out);
        if (!text.endsWith("\n")) text = text + "\n";
        return new Result(text, true, added);
    }

    // ============================================================
    // 解析
    // ============================================================

    private static final class Node {
        int indent;
        String key;
        List<String> pre = new ArrayList<>();
        String line = "";
        List<String> childLines = new ArrayList<>();
        List<Node> children = new ArrayList<>();
        int endLine; // 该节点（含子行）结束后的行号（插入点）
    }

    private static final class ParseResult {
        final List<Node> nodes;
        final int nextIndex;

        ParseResult(List<Node> nodes, int nextIndex) {
            this.nodes = nodes;
            this.nextIndex = nextIndex;
        }
    }

    private static ParseResult parse(List<String> lines, int baseIndent, int start, int end) {
        List<Node> nodes = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        int i = start;
        while (i < end) {
            String line = lines.get(i);
            String s = line.trim();
            int ind = indentOf(line);
            if (s.isEmpty() || s.startsWith("#")) {
                pending.add(line);
                i++;
                continue;
            }
            if (ind < baseIndent) {
                break;
            }
            if (ind > baseIndent) { // 缩进异常，跳过
                pending.add(line);
                i++;
                continue;
            }
            String[] pk = parseKey(line, ind);
            if (pk == null) {
                pending.add(line);
                i++;
                continue;
            }
            Node n = new Node();
            n.pre = new ArrayList<>(pending);
            pending.clear();
            n.indent = ind;
            n.key = pk[1];
            n.line = line;
            i++;
            // 收集子行：缩进大于 baseIndent 的行；空行仅当后面还有更深内容时才并入
            List<String> childLines = new ArrayList<>();
            while (i < end) {
                String l2 = lines.get(i);
                String s2 = l2.trim();
                if (s2.isEmpty()) {
                    int j = i + 1;
                    while (j < end && lines.get(j).trim().isEmpty()) j++;
                    if (j < end && indentOf(lines.get(j)) > baseIndent) {
                        childLines.add(l2);
                        i++;
                        continue;
                    }
                    break;
                }
                if (indentOf(l2) > baseIndent) {
                    childLines.add(l2);
                    i++;
                    continue;
                }
                break;
            }
            n.childLines = childLines;
            n.endLine = i;
            if (!childLines.isEmpty()) {
                int childIndent = indentOf(firstNonBlank(childLines, baseIndent + 2));
                n.children = parse(childLines, childIndent, 0, childLines.size()).nodes;
            }
            nodes.add(n);
        }
        return new ParseResult(nodes, i);
    }

    private static String firstNonBlank(List<String> lines, int fallback) {
        for (String l : lines) {
            if (!l.trim().isEmpty()) return l;
        }
        return "";
    }

    private static String[] parseKey(String line, int indent) {
        String rest = line.substring(indent);
        if (rest.isEmpty()) return null;
        char c0 = rest.charAt(0);
        if (c0 == '#' || c0 == '-') return null;
        int colon = rest.indexOf(':');
        if (colon < 0) return null;
        String key = rest.substring(0, colon).trim();
        if (key.isEmpty() || key.contains(" ")) return null;
        return new String[]{String.valueOf(indent), key};
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }

    private static List<String> split(String text) {
        // 统一 LF 后按行拆分，末尾不保留多余空行
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        List<String> lines = new ArrayList<>();
        for (String l : t.split("\n", -1)) lines.add(l);
        // 去掉结尾因换行产生的最后一个空元素
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
