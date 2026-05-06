package com.qianxun.service;

import com.qianxun.domain.IntentScenario;
import com.qianxun.domain.IntentScenario.SlotDefinition;
import com.qianxun.repo.IntentScenarioRepository;
import com.qianxun.web.dto.UpsertIntentScenarioRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class QianXunServiceIntentScenario {

    private static final Logger log = LoggerFactory.getLogger(QianXunServiceIntentScenario.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(15);
    private static final Set<String> ALLOWED_SLOT_TYPES =
            Set.of("string", "number", "boolean", "enum", "date", "datetime");

    private final IntentScenarioRepository repository;
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public QianXunServiceIntentScenario(IntentScenarioRepository repository) {
        this.repository = repository;
    }

    public List<IntentScenario> listEnabled() {
        Cached c = cache.get();
        if (c != null && Instant.now().isBefore(c.expiresAt())) {
            return c.scenarios();
        }
        List<IntentScenario> fresh;
        try {
            fresh = repository.listEnabledOrderByPriorityDesc();
        } catch (Exception ex) {
            log.warn("加载意图场景失败，将临时返回空集合: {}", ex.toString());
            return List.of();
        }
        cache.set(new Cached(fresh, Instant.now().plus(CACHE_TTL)));
        return fresh;
    }

    public List<IntentScenario> listAll() {
        return repository.listAll();
    }

    public Optional<IntentScenario> findByCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.empty();
        }
        return repository.findByCode(code.trim());
    }

    public IntentScenario get(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "意图场景不存在"));
    }

    public IntentScenario create(UpsertIntentScenarioRequest req) {
        validate(req, true);
        if (repository.findByCode(req.code().trim()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "意图场景 code 已存在");
        }
        Instant now = Instant.now();
        IntentScenario s = new IntentScenario(
                newId(),
                req.code().trim(),
                req.name().trim(),
                trimOrEmpty(req.description()),
                normalizeExamples(req.examples()),
                normalizeSlots(req.slots()),
                trimOrEmpty(req.agentSkill()),
                trimOrEmpty(req.promptTemplate()),
                req.extraParams() == null ? Map.of() : req.extraParams(),
                req.priority() == null ? 100 : req.priority(),
                req.enabled() == null || req.enabled(),
                now,
                now
        );
        repository.insert(s);
        invalidateCache();
        return s;
    }

    public IntentScenario update(String id, UpsertIntentScenarioRequest req) {
        IntentScenario existing = get(id);
        validate(req, false);

        if (req.code() != null && !req.code().trim().equals(existing.code())) {
            repository.findByCode(req.code().trim())
                    .filter(other -> !other.id().equals(id))
                    .ifPresent(other -> {
                        throw new ResponseStatusException(HttpStatus.CONFLICT, "意图场景 code 已存在");
                    });
        }

        Instant now = Instant.now();
        IntentScenario merged = new IntentScenario(
                existing.id(),
                req.code() == null ? existing.code() : req.code().trim(),
                req.name() == null ? existing.name() : req.name().trim(),
                req.description() == null ? existing.description() : req.description(),
                req.examples() == null ? existing.examples() : normalizeExamples(req.examples()),
                req.slots() == null ? existing.slots() : normalizeSlots(req.slots()),
                req.agentSkill() == null ? existing.agentSkill() : req.agentSkill().trim(),
                req.promptTemplate() == null ? existing.promptTemplate() : req.promptTemplate(),
                req.extraParams() == null ? existing.extraParams() : req.extraParams(),
                req.priority() == null ? existing.priority() : req.priority(),
                req.enabled() == null ? existing.enabled() : req.enabled(),
                existing.createdAt(),
                now
        );
        repository.updateAll(merged);
        invalidateCache();
        return merged;
    }

    public void delete(String id) {
        IntentScenario existing = get(id);
        if (existing.isGeneral()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "通用兜底场景不可删除");
        }
        repository.deleteById(id);
        invalidateCache();
    }

    public void invalidateCache() {
        cache.set(null);
    }

    private void validate(UpsertIntentScenarioRequest req, boolean creating) {
        if (req == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求体不能为空");
        }
        if (creating) {
            requireNonBlank(req.code(), "code 必填", true);
            requireNonBlank(req.name(), "name 必填", true);
        } else {
            requireNonBlank(req.code(), "code 不能为空字符串", false);
            requireNonBlank(req.name(), "name 不能为空字符串", false);
        }
        if (req.code() != null && !req.code().matches("[a-zA-Z][a-zA-Z0-9_\\-]{0,127}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "code 必须以字母开头，仅含字母数字下划线和短横，长度 1~128");
        }
        if (req.slots() != null) {
            validateSlots(req.slots());
        }
    }

    private void requireNonBlank(String value, String message, boolean required) {
        if (required && (value == null || value.isBlank())
                || !required && value != null && value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }

    private void validateSlots(List<SlotDefinition> slots) {
        Set<String> seen = new LinkedHashSet<>();
        for (SlotDefinition slot : slots) {
            validateSlot(slot, seen);
        }
    }

    private void validateSlot(SlotDefinition slot, Set<String> seen) {
        if (slot == null || slot.name() == null || slot.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "槽位名不能为空");
        }
        if (!seen.add(slot.name().trim())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "槽位名重复: " + slot.name());
        }
        String type = slot.type() == null ? "string" : slot.type().trim().toLowerCase();
        if (!ALLOWED_SLOT_TYPES.contains(type)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "槽位类型非法: " + slot.type() + "，可选 " + ALLOWED_SLOT_TYPES);
        }
        if ("enum".equals(type) && (slot.values() == null || slot.values().isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "枚举槽位必须提供 values: " + slot.name());
        }
    }

    private static List<String> normalizeExamples(List<String> examples) {
        if (examples == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>(examples.size());
        for (String e : examples) {
            if (e == null) {
                continue;
            }
            String t = e.trim();
            if (!t.isEmpty()) {
                out.add(t);
            }
        }
        return out;
    }

    private static List<SlotDefinition> normalizeSlots(List<SlotDefinition> slots) {
        if (slots == null) {
            return List.of();
        }
        List<SlotDefinition> out = new ArrayList<>(slots.size());
        for (SlotDefinition s : slots) {
            if (s == null || s.name() == null || s.name().isBlank()) {
                continue;
            }
            String type = s.type() == null || s.type().isBlank() ? "string" : s.type().trim().toLowerCase();
            out.add(new SlotDefinition(
                    s.name().trim(),
                    type,
                    s.required(),
                    s.description() == null ? "" : s.description(),
                    s.values() == null ? List.of() : List.copyOf(s.values())
            ));
        }
        return out;
    }

    private static String trimOrEmpty(String v) {
        return v == null ? "" : v.trim();
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private record Cached(List<IntentScenario> scenarios, Instant expiresAt) {
    }
}
