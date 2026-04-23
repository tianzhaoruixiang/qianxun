package com.qianxun.nlu;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.IntentScenario.SlotDefinition;
import com.qianxun.llm.OpenAiCompatibleStreamClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QianXunServiceIntentSlotUnderstanding {

    private static final Logger log = LoggerFactory.getLogger(QianXunServiceIntentSlotUnderstanding.class);

    private static final String FALLBACK_SYSTEM_PROMPT = """
            你是「千寻」系统的 NLU 模块，擅长数据分析场景下的意图识别与槽位抽取。
            请阅读用户问题，输出**仅一段 JSON**（不要 Markdown，不要额外解释），字段如下：
            {"scenario_code":"general","slots":{...},"confidence":0.0,"reasoning":""}
            slots 用键值对承载关键信息（如 question 等），不知道时给 {}。
            若输入含多轮对话摘录：【当前用户消息】可能是对上文主题的补充，须与上文合并后再抽取槽位，不得丢失上文已出现的人物、事件或专有名词。
            """;

    private final ObjectMapper objectMapper;
    private final OpenAiCompatibleStreamClient openAiClient;

    public QianXunServiceIntentSlotUnderstanding(ObjectMapper objectMapper, OpenAiCompatibleStreamClient openAiClient) {
        this.objectMapper = objectMapper;
        this.openAiClient = openAiClient;
    }

    /**
     * 远程 LLM 路径：基于已注册的意图场景对用户输入进行场景分类与槽位抽取。
     *
     * @param multiTurnContext 当前输入之前的对话摘录（可为空）；非空时 NLU 须做指代消解并与历史合并槽位。
     */
    public IntentSlotUnderstanding understand(
            String userText,
            String multiTurnContext,
            List<IntentScenario> scenarios,
            String baseUrl,
            String apiKey,
            String model,
            double temperature,
            String customSystemPrompt
    ) throws Exception {
        String system = (customSystemPrompt != null && !customSystemPrompt.isBlank())
                ? customSystemPrompt.trim()
                : (scenarios == null || scenarios.isEmpty()
                ? FALLBACK_SYSTEM_PROMPT
                : buildScenarioSystemPrompt(scenarios));

        String userPayload = buildNluUserPayload(userText, multiTurnContext);

        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", userPayload));

        String raw = openAiClient.completeChat(baseUrl, apiKey, model, messages, temperature);
        return parseFromModelText(userText, scenarios, raw);
    }

    static String buildNluUserPayload(String userText, String multiTurnContext) {
        String u = userText == null ? "" : userText;
        if (multiTurnContext == null || multiTurnContext.isBlank()) {
            return u;
        }
        return multiTurnContext.strip()
                + "\n---\n【当前用户消息】（请结合上文完成指代消解；若为对助手追问的回答，请与上文主题合并为完整槽位）\n"
                + u;
    }

    /**
     * 本地 mock 路径：在没有远程模型时基于关键字 / 示例做粗匹配，便于联调 UI。
     *
     * @param multiTurnContext 与 {@link #understand} 相同；非空时会拼入启发式匹配的输入文本。
     */
    public IntentSlotUnderstanding mockUnderstand(
            String userText, String multiTurnContext, List<IntentScenario> scenarios
    ) {
        String t = userText == null ? "" : userText.trim();
        if (multiTurnContext != null && !multiTurnContext.isBlank()) {
            t = (multiTurnContext.strip() + " " + t).trim();
        }
        if (scenarios != null && !scenarios.isEmpty()) {
            IntentScenario best = pickByHeuristic(t, scenarios);
            if (best != null && !best.isGeneral()) {
                Map<String, Object> slots = heuristicSlots(t, best);
                List<String> missing = computeMissingRequiredSlots(best, slots);
                return new IntentSlotUnderstanding(
                        best.code(),
                        best.code(),
                        best.name(),
                        nullSafe(best.agentSkill()),
                        slots,
                        missing,
                        0.4,
                        "本地 mock 启发式匹配命中：" + best.code(),
                        "",
                        best
                );
            }
        }
        Map<String, Object> slots = new LinkedHashMap<>();
        slots.put("question", t);
        if (t.length() > 120) {
            slots.put("question_preview", t.substring(0, 120) + "…");
        }
        IntentScenario general = findGeneral(scenarios);
        return new IntentSlotUnderstanding(
                IntentScenario.GENERAL_CODE,
                IntentScenario.GENERAL_CODE,
                general == null ? "通用问答" : general.name(),
                general == null ? "" : nullSafe(general.agentSkill()),
                slots,
                List.of(),
                0.3,
                "本地 mock 未命中专项场景，回退通用问答",
                "",
                general
        );
    }

    private IntentSlotUnderstanding parseFromModelText(
            String userText,
            List<IntentScenario> scenarios,
            String raw
    ) {
        if (raw == null || raw.isBlank()) {
            return IntentSlotUnderstanding.fallback(userText, raw);
        }
        String jsonText = JsonPayloadExtractor.extractJsonObject(raw);
        if (jsonText == null || jsonText.isBlank()) {
            log.warn("NLU 输出无法提取 JSON，已回退。raw={}", abbrev(raw));
            return IntentSlotUnderstanding.fallback(userText, raw);
        }
        try {
            JsonNode root = objectMapper.readTree(jsonText);
            String scenarioCode = firstNonBlank(
                    root.path("scenario_code").asText(""),
                    root.path("intent").asText("")
            );
            if (scenarioCode == null || scenarioCode.isBlank()) {
                scenarioCode = IntentScenario.GENERAL_CODE;
            }

            JsonNode slotsNode = root.path("slots");
            Map<String, Object> slots = slotsNode.isObject()
                    ? objectMapper.convertValue(slotsNode, new TypeReference<Map<String, Object>>() {
            })
                    : Map.of();

            double confidence = root.path("confidence").isNumber() ? root.path("confidence").asDouble() : 0.0;
            String reasoning = root.path("reasoning").asText("");

            IntentScenario matched = matchScenarioByCode(scenarios, scenarioCode);
            String displayName = matched != null
                    ? matched.name()
                    : (IntentScenario.GENERAL_CODE.equals(scenarioCode) ? "通用问答" : scenarioCode);
            String agentSkill = matched == null ? "" : nullSafe(matched.agentSkill());
            List<String> missing = matched == null ? List.of() : computeMissingRequiredSlots(matched, slots);

            return new IntentSlotUnderstanding(
                    scenarioCode,
                    scenarioCode,
                    displayName,
                    agentSkill,
                    slots,
                    missing,
                    confidence,
                    reasoning,
                    raw,
                    matched
            );
        } catch (Exception ex) {
            log.warn("NLU JSON 解析失败，已回退: {}", ex.toString());
            return IntentSlotUnderstanding.fallback(userText, raw);
        }
    }

    static String buildScenarioSystemPrompt(List<IntentScenario> scenarios) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是「千寻」系统的 NLU 模块，请将用户问题映射到下列「调研场景」之一，并抽取槽位。\n\n");
        sb.append("## 可用场景\n");
        for (IntentScenario s : scenarios) {
            sb.append("[CODE: ").append(s.code()).append("] ").append(s.name()).append('\n');
            if (s.description() != null && !s.description().isBlank()) {
                sb.append("  描述：").append(s.description()).append('\n');
            }
            List<SlotDefinition> required = s.safeSlots().stream().filter(SlotDefinition::required).toList();
            List<SlotDefinition> optional = s.safeSlots().stream().filter(d -> !d.required()).toList();
            if (!required.isEmpty()) {
                sb.append("  必填槽位：\n");
                for (SlotDefinition d : required) {
                    appendSlot(sb, d);
                }
            }
            if (!optional.isEmpty()) {
                sb.append("  可选槽位：\n");
                for (SlotDefinition d : optional) {
                    appendSlot(sb, d);
                }
            }
            if (!s.safeExamples().isEmpty()) {
                sb.append("  示例：\n");
                for (String e : s.safeExamples()) {
                    sb.append("    - ").append(e).append('\n');
                }
            }
            sb.append('\n');
        }
        sb.append("## 输出要求\n");
        sb.append("仅输出一段紧凑 JSON（不要 Markdown，不要解释），字段如下：\n");
        sb.append("{\n");
        sb.append("  \"scenario_code\": \"<上述某个 CODE，未匹配则填 general>\",\n");
        sb.append("  \"slots\": { \"<key>\": <value>, ... },\n");
        sb.append("  \"confidence\": 0.0~1.0,\n");
        sb.append("  \"reasoning\": \"<不超过 50 字的中文说明>\"\n");
        sb.append("}\n");
        sb.append("规则：\n");
        sb.append("- slots 仅包含本场景声明过的槽位（缺失字段不要瞎猜，留空即可）\n");
        sb.append("- 如果同时可能匹配多个场景，选择置信度最高的一个；找不到任何专项场景则使用 general\n");
        sb.append("- 若用户消息中包含「对话摘录」或上文：最新一句可能是对助手追问的简短补充，必须把上文中的实体/事件名/主题与当前句合并后再填槽位，不得丢弃首轮已出现的人物或事件称谓\n");
        return sb.toString();
    }

    private static void appendSlot(StringBuilder sb, SlotDefinition d) {
        sb.append("    - ").append(d.name()).append(" (").append(d.type());
        if ("enum".equalsIgnoreCase(d.type()) && d.values() != null && !d.values().isEmpty()) {
            sb.append(": ").append(String.join("|", d.values()));
        }
        sb.append(")");
        if (d.description() != null && !d.description().isBlank()) {
            sb.append(" - ").append(d.description());
        }
        sb.append('\n');
    }

    private static IntentScenario matchScenarioByCode(List<IntentScenario> scenarios, String code) {
        if (scenarios == null || scenarios.isEmpty() || code == null) {
            return null;
        }
        for (IntentScenario s : scenarios) {
            if (s.code() != null && s.code().equalsIgnoreCase(code.trim())) {
                return s;
            }
        }
        return null;
    }

    static List<String> computeMissingRequiredSlots(IntentScenario scenario, Map<String, Object> slots) {
        if (scenario == null) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (SlotDefinition d : scenario.safeSlots()) {
            if (!d.required()) {
                continue;
            }
            Object v = slots == null ? null : slots.get(d.name());
            if (v == null) {
                missing.add(d.name());
                continue;
            }
            if (v instanceof CharSequence cs && cs.toString().trim().isEmpty()) {
                missing.add(d.name());
            }
        }
        return missing;
    }

    /**
     * 常见中文动词/连接 bigram，用于在示例匹配时剔除噪声，
     * 突出实体性 bigram 对场景的区分作用。
     */
    private static final Set<String> STOP_BIGRAMS = Set.of(
            "帮我", "请帮", "麻烦", "我想", "想要", "看下", "看一", "一下", "看看",
            "了解", "整理", "分析", "介绍", "查下", "查一", "请问", "可以", "能否"
    );

    static IntentScenario pickByHeuristic(String userText, List<IntentScenario> scenarios) {
        if (userText == null || userText.isBlank() || scenarios == null) {
            return null;
        }
        Set<String> userBigrams = bigrams(userText);
        IntentScenario best = null;
        int bestScore = 0;
        for (IntentScenario s : scenarios) {
            if (s.isGeneral()) {
                continue;
            }
            int score = 0;
            if (s.name() != null && !s.name().isBlank()) {
                for (String bg : bigrams(s.name())) {
                    if (userBigrams.contains(bg)) {
                        score += 2;
                    }
                }
            }
            for (String e : s.safeExamples()) {
                if (e == null || e.isBlank()) {
                    continue;
                }
                for (String bg : bigrams(e)) {
                    if (STOP_BIGRAMS.contains(bg)) {
                        continue;
                    }
                    if (userBigrams.contains(bg)) {
                        score += 1;
                    }
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = s;
            }
        }
        return bestScore >= 4 ? best : null;
    }

    private static Set<String> bigrams(String text) {
        if (text == null) {
            return Set.of();
        }
        String t = text.replaceAll("\\s+", "");
        if (t.length() < 2) {
            return Set.of();
        }
        Set<String> out = new LinkedHashSet<>();
        for (int i = 0; i + 1 < t.length(); i++) {
            out.add(t.substring(i, i + 2));
        }
        return out;
    }

    private static Map<String, Object> heuristicSlots(String userText, IntentScenario scenario) {
        Map<String, Object> slots = new LinkedHashMap<>();
        for (SlotDefinition d : scenario.safeSlots()) {
            if (!d.required() || !"string".equalsIgnoreCase(d.type())) {
                continue;
            }
            String stripped = userText
                    .replaceAll("[?？!！。.]+$", "")
                    .replaceAll("(帮我|请帮|麻烦|帮忙|我想|想要|看一下|看下|看看|了解|调研|分析|整理|介绍|查一下|查下|一下)", "")
                    .replaceAll("(的最新动态|的最新|的财报|的风险|最近|动态|情况|履历)", "")
                    .trim();
            if (!stripped.isEmpty()) {
                slots.put(d.name(), stripped);
                break;
            }
        }
        return slots;
    }

    private static IntentScenario findGeneral(List<IntentScenario> scenarios) {
        if (scenarios == null) {
            return null;
        }
        for (IntentScenario s : scenarios) {
            if (s.isGeneral()) {
                return s;
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private static String abbrev(String s) {
        if (s == null) {
            return "";
        }
        String t = s.replace("\n", " ").trim();
        return t.length() > 240 ? t.substring(0, 240) + "…" : t;
    }
}
