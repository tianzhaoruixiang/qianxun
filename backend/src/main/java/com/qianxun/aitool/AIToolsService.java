package com.qianxun.aitool;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonpMapper;
import com.alibaba.fastjson2.JSONObject;
import com.qianxun.web.SqlRequest;
import com.qianxun.web.ApiRequestSupport;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.EsQueryRequest;
import jakarta.json.stream.JsonParser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static co.elastic.clients.json.JsonData.of;

@Slf4j
@RestController
@RequestMapping("/QianXunService/aitools")
public class AIToolsService {
    private final JdbcTemplate tidbJdbcTemplate;
    private final ElasticsearchClient elasticsearchClient;
    private final JdbcTemplate dorisJdbcTemplate;

    @Autowired
    public AIToolsService(
            JdbcTemplate tidbJdbcTemplate,
            ElasticsearchClient elasticsearchClient,
            @Autowired(required = false) @Qualifier("dorisJdbcTemplate") JdbcTemplate dorisJdbcTemplate
    ) {
        this.tidbJdbcTemplate = tidbJdbcTemplate;
        this.elasticsearchClient = elasticsearchClient;
        this.dorisJdbcTemplate = dorisJdbcTemplate;
        if (this.dorisJdbcTemplate != null) {
            log.info("Doris support enabled");
        } else {
            log.info("Doris support disabled (doris-dataSource not available)");
        }
    }

    @PostMapping("executeSql")
    public ApiResponse<List<Map<String, Object>>> executeSql(@RequestBody ApiRequest<SqlRequest> request) {
        try {
            ApiRequestSupport.applyGeneralArgument(request);
            SqlRequest jsonArg = ApiRequestSupport.jsonArg(request);
            String sql = jsonArg == null ? null : jsonArg.sql();
            if (sql == null || sql.trim().isEmpty()) {
                return ApiResponse.error(400, "SQL语句不能为空");
            }
            validateReadOnly(sql, "TiDB");
            log.info("[TiDB] SQL执行 >>> {}", sql);
            long start = System.currentTimeMillis();
            List<Map<String, Object>> result = tidbJdbcTemplate.queryForList(sql);
            long cost = System.currentTimeMillis() - start;
            log.info("[TiDB] SQL执行 <<< 耗时: {}ms, 返回行数: {}", cost, result.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[TiDB] SQL执行异常", e);
            return ApiResponse.error(500, "SQL执行失败: " + e.getMessage());
        }
    }

    @PostMapping("executeDorisSql")
    public ApiResponse<List<Map<String, Object>>> executeDorisSql(@RequestBody ApiRequest<SqlRequest> request) {
        if (dorisJdbcTemplate == null) {
            return ApiResponse.error(503, "Doris服务未启用，请检查 doris.datasource.enable 配置");
        }
        try {
            ApiRequestSupport.applyGeneralArgument(request);
            SqlRequest jsonArg = ApiRequestSupport.jsonArg(request);
            String sql = jsonArg == null ? null : jsonArg.sql();
            if (sql == null || sql.trim().isEmpty()) {
                return ApiResponse.error(400, "SQL语句不能为空");
            }
            validateReadOnly(sql, "Doris");
            log.info("[Doris] SQL执行 >>> {}", sql);
            long start = System.currentTimeMillis();
            List<Map<String, Object>> result = dorisJdbcTemplate.queryForList(sql);
            long cost = System.currentTimeMillis() - start;
            log.info("[Doris] SQL执行 <<< 耗时: {}ms, 返回行数: {}", cost, result.size());
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("[Doris] SQL执行异常", e);
            return ApiResponse.error(500, "SQL执行失败: " + e.getMessage());
        }
    }


    @PostMapping("queryElasticsearch")
    public ApiResponse<List<JSONObject>> queryElasticsearch(@RequestBody ApiRequest<EsQueryRequest> request) {
        try {
            ApiRequestSupport.applyGeneralArgument(request);
            EsQueryRequest jsonArg = ApiRequestSupport.jsonArg(request);
            if (jsonArg == null) {
                return ApiResponse.error(400, "请求体不能为空");
            }

            String index = jsonArg.index() != null ? jsonArg.index().trim() : "";
            String esIndex = index.isEmpty() ? "_all" : index;
            Integer from = jsonArg.from() != null ? jsonArg.from() : 0;
            Integer size = jsonArg.size() != null ? jsonArg.size() : 10;
            Map<String, Object> queryMap = jsonArg.query();
            Map<String, Object> sourceMap = jsonArg._source();
            Query esQuery = buildQueryFromMap(queryMap);

            // 处理 _source 排除字段
            List<String> excludes = new ArrayList<>();
            if (sourceMap != null && sourceMap.containsKey("excludes")) {
                Object excludesObj = sourceMap.get("excludes");
                if (excludesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> excludesList = (List<String>) excludesObj;
                    excludes.addAll(excludesList);
                }
            }

            log.info("[ES] 查询执行 >>> index={}, from={}, size={}, excludes={}", esIndex, from, size, excludes);
            long start = System.currentTimeMillis();
            SearchResponse<JSONObject> response = elasticsearchClient.search(s -> {
                s.index(esIndex)
                 .query(esQuery)
                 .from(from)
                 .size(size);
                if (!excludes.isEmpty()) {
                    s.source(src -> src.filter(f -> f.excludes(excludes)));
                }
                return s;
            }, JSONObject.class);
            long cost = System.currentTimeMillis() - start;
            long total = response.hits().total() != null ? response.hits().total().value() : response.hits().hits().size();
            log.info("[ES] 查询执行 <<< 耗时: {}ms, 命中总数: {}, 返回条数: {}", cost, total, response.hits().hits().size());

            List<JSONObject> data = response.hits().hits().stream()
                    .map(Hit::source)
                    .collect(Collectors.toList());
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("[ES] 查询执行异常", e);
            return ApiResponse.error(500, "ES查询失败: " + e.getMessage());
        }
    }

    private Query buildQueryFromMap(Map<String, Object> queryMap) {
        if (queryMap == null || queryMap.isEmpty()) {
            return Query.of(q -> q.matchAll(ma -> ma));
        }
        String queryJson = new JSONObject(queryMap).toJSONString();
        return Query.of(q -> q.withJson(new java.io.StringReader(queryJson)));
    }

    private Query buildQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Query.of(q -> q.matchAll(ma -> ma));
        }

        String trimmed = query.trim();
        if (trimmed.startsWith("{")) {
            return parseJsonQuery(trimmed);
        } else {
            return Query.of(q -> q
                    .queryString(qs -> qs.query(trimmed))
            );
        }
    }

    private Query parseJsonQuery(String queryJson) {
        try {
            JsonpMapper mapper = elasticsearchClient._transport().jsonpMapper();
            StringReader reader = new StringReader(queryJson);
            JsonParser parser = mapper.jsonProvider().createParser(reader);
            return Query._DESERIALIZER.deserialize(parser, mapper);
        } catch (Exception e) {
            throw new IllegalStateException("JSON 查询语句解析失败: " + queryJson, e);
        }
    }

    private void validateReadOnly(String sql, String dbType) {
        String normalized = sql.trim();
        String upper = normalized.toUpperCase();

        String[][] DANGEROUS = {
                {"DROP",           "删除数据库/表/索引"},
                {"DELETE",         "删除数据行"},
                {"TRUNCATE",       "清空表数据"},
                {"ALTER",          "修改表结构"},
                {"CREATE",         "创建数据库/表/索引/视图/存储过程"},
                {"INSERT",         "写入数据"},
                {"UPDATE",         "更新数据"},
                {"GRANT",          "授予权限"},
                {"REVOKE",         "撤销权限"},
                {"KILL",           "终止连接/查询"},
                {"SET",            "修改会话/全局变量"},
                {"SHOW PROCESSLIST", "查看全部连接（敏感）"},
                {"SHUTDOWN",       "关闭数据库"},
        };

        for (String[] item : DANGEROUS) {
            if (upper.startsWith(item[0]) || upper.startsWith(item[0] + " ")) {
                String msg = String.format("[%s] 禁止执行 %s 操作：%s",
                        dbType, item[1], normalized.length() > 100 ? normalized.substring(0, 100) + "..." : normalized);
                log.warn("[Security] {}", msg);
                throw new IllegalStateException(msg);
            }
        }
    }
}