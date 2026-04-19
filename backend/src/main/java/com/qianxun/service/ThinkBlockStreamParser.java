package com.qianxun.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 流式 token 中 {@code <think>...</think>} 块解析器。
 *
 * <p>DeepSeek-R1、QwQ 等推理模型会在回复中插入 {@code <think>} 标签标记内部推理过程，
 * 本类在 token 逐个到达的过程中实时识别这些标签，将 token 分流为「推理内容」与「正式回复」两路。
 *
 * <p>使用方式：
 * <pre>{@code
 * ThinkBlockStreamParser parser = new ThinkBlockStreamParser();
 * for (String token : tokenStream) {
 *     for (ThinkBlockStreamParser.Chunk chunk : parser.feed(token)) {
 *         if (chunk.type() == ChunkType.THINK) { ... }   // 推理 token
 *         else                                { ... }   // 正式回复 token
 *     }
 * }
 * parser.flush(); // 处理末尾剩余缓冲
 * }</pre>
 */
public class ThinkBlockStreamParser {

    public enum ChunkType { THINK, TEXT }

    public record Chunk(ChunkType type, String text) {}

    private static final String THINK_OPEN  = "<think>";
    private static final String THINK_CLOSE = "</think>";

    private final StringBuilder buffer = new StringBuilder();
    private boolean inThink = false;

    /**
     * 喂入一个 token，返回零个或多个已确定的 Chunk。
     * 每次调用可能因为缓冲中间状态而不立即返回 Chunk。
     */
    public List<Chunk> feed(String token) {
        buffer.append(token);
        return drain();
    }

    /**
     * 流结束时调用，将缓冲中剩余内容全部输出。
     */
    public List<Chunk> flush() {
        List<Chunk> result = new ArrayList<>();
        if (!buffer.isEmpty()) {
            result.add(new Chunk(inThink ? ChunkType.THINK : ChunkType.TEXT, buffer.toString()));
            buffer.setLength(0);
        }
        return result;
    }

    private List<Chunk> drain() {
        List<Chunk> result = new ArrayList<>();
        String tag = inThink ? THINK_CLOSE : THINK_OPEN;

        while (true) {
            String buf = buffer.toString();
            int tagIdx = buf.indexOf(tag);

            if (tagIdx == -1) {
                // 标签未完整出现，但可能是前缀——保留最多 tag.length()-1 个尾字符防截断
                int safe = Math.max(0, buf.length() - (tag.length() - 1));
                if (safe > 0) {
                    result.add(new Chunk(inThink ? ChunkType.THINK : ChunkType.TEXT, buf.substring(0, safe)));
                    buffer.delete(0, safe);
                }
                break;
            }

            // 标签前的内容直接输出
            if (tagIdx > 0) {
                result.add(new Chunk(inThink ? ChunkType.THINK : ChunkType.TEXT, buf.substring(0, tagIdx)));
            }

            // 跳过标签本身，切换状态
            buffer.delete(0, tagIdx + tag.length());
            inThink = !inThink;
            tag = inThink ? THINK_CLOSE : THINK_OPEN;
        }
        return result;
    }

    public boolean isInThink() {
        return inThink;
    }
}
