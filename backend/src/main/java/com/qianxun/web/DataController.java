package com.qianxun.web;

import com.qianxun.domain.DataFile;
import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.DataPortraitRepository;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.DataFileResponse;
import com.qianxun.web.dto.DataFileDetailResponse;
import com.qianxun.web.dto.DataPortraitResponse;
import com.qianxun.web.dto.QueryDataFileDetailRequest;
import com.qianxun.web.dto.UpsertDataFileRequest;
import com.qianxun.web.dto.UpsertDataPortraitRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 中间数据 / 数据画像 接口（用于右侧面板展示）。
 * 所有数据均落地至 TiDB（data_file / data_portrait_point 两张表）。
 * 接口规范同其它业务接口：POST + ApiRequest + ApiResponse。
 */
@RestController
@RequestMapping("/QianXunService/data")
public class DataController {

    private static final int DEFAULT_FILE_LIMIT = 200;

    private final DataFileRepository fileRepository;
    private final DataPortraitRepository portraitRepository;

    public DataController(DataFileRepository fileRepository, DataPortraitRepository portraitRepository) {
        this.fileRepository = fileRepository;
        this.portraitRepository = portraitRepository;
    }

    @PostMapping("/files/list")
    public ApiResponse<List<DataFileResponse>> listFiles(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        List<DataFile> files = fileRepository.listOrderByDateDesc(DEFAULT_FILE_LIMIT);
        List<DataFileResponse> data = files.stream()
                .map(f -> new DataFileResponse(f.id(), f.name(), f.displayDate(), f.kind()))
                .toList();
        return ApiResponse.success(data);
    }

    @PostMapping("/portrait")
    public ApiResponse<DataPortraitResponse> portrait(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        List<DataPortraitPoint> points = portraitRepository.listByGroup(DataPortraitPoint.DEFAULT_GROUP);
        if (points.isEmpty()) {
            return ApiResponse.success(new DataPortraitResponse("", List.of(), List.of(), List.of(), null, null));
        }
        List<String> labels = new ArrayList<>(points.size());
        List<Integer> a = new ArrayList<>(points.size());
        List<Integer> b = new ArrayList<>(points.size());
        Integer focusIndex = null;
        String focusLabel = null;
        String unit = points.get(0).unit();
        for (int i = 0; i < points.size(); i++) {
            DataPortraitPoint p = points.get(i);
            labels.add(p.label());
            a.add(p.seriesA());
            b.add(p.seriesB());
            if (p.focused()) {
                focusIndex = i;
                focusLabel = p.focusLabel();
            }
        }
        return ApiResponse.success(new DataPortraitResponse(unit, labels, a, b, focusIndex, focusLabel));
    }

    @PostMapping("/files/detail")
    public ApiResponse<DataFileDetailResponse> fileDetail(@RequestBody ApiRequest<QueryDataFileDetailRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        QueryDataFileDetailRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.id())) {
            return ApiResponse.error(400, "id 不能为空");
        }
        var opt = fileRepository.findById(body.id().trim());
        if (opt.isEmpty()) {
            return ApiResponse.error(404, "文件不存在");
        }
        DataFile f = opt.get();
        List<List<String>> rows = parseExcelRows(f.detailJson());
        return ApiResponse.success(new DataFileDetailResponse(
                f.id(), f.name(), f.displayDate(), f.kind(), nullSafe(f.detailText()), rows
        ));
    }

    @PostMapping("/files/upsert")
    public ApiResponse<DataFileResponse> upsertFile(@RequestBody ApiRequest<UpsertDataFileRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertDataFileRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.name()) || isBlank(body.date()) || isBlank(body.kind())) {
            return ApiResponse.error(400, "name/date/kind 不能为空");
        }
        String id = isBlank(body.id()) ? newId() : body.id().trim();
        Instant now = Instant.now();
        var existing = fileRepository.findById(id);
        DataFile file = new DataFile(
                id, body.name().trim(), body.date().trim(), body.kind().trim(),
                body.detailText(), body.detailJson(), now, now
        );
        if (existing.isPresent()) {
            fileRepository.update(file);
        } else {
            fileRepository.insert(file);
        }
        return ApiResponse.success(new DataFileResponse(file.id(), file.name(), file.displayDate(), file.kind()));
    }

    @PostMapping("/portrait/upsert")
    public ApiResponse<DataPortraitResponse> upsertPortrait(@RequestBody ApiRequest<UpsertDataPortraitRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        UpsertDataPortraitRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || body.points() == null || body.points().isEmpty()) {
            return ApiResponse.error(400, "points 不能为空");
        }
        String groupCode = isBlank(body.groupCode()) ? DataPortraitPoint.DEFAULT_GROUP : body.groupCode().trim();
        String unit = body.unit() == null ? "" : body.unit().trim();
        portraitRepository.deleteByGroup(groupCode);
        Integer focusIndex = null;
        String focusLabel = null;
        List<String> labels = new ArrayList<>(body.points().size());
        List<Integer> a = new ArrayList<>(body.points().size());
        List<Integer> b = new ArrayList<>(body.points().size());
        for (int i = 0; i < body.points().size(); i++) {
            UpsertDataPortraitRequest.PortraitPoint p = body.points().get(i);
            String label = p.label() == null ? ("P" + (i + 1)) : p.label().trim();
            int seriesA = p.seriesA() == null ? 0 : p.seriesA();
            int seriesB = p.seriesB() == null ? 0 : p.seriesB();
            boolean focused = p.focused() != null && p.focused();
            portraitRepository.insert(new DataPortraitPoint(
                    newId(), groupCode, unit, i, label, seriesA, seriesB, focused, p.focusLabel()
            ));
            labels.add(label);
            a.add(seriesA);
            b.add(seriesB);
            if (focused) {
                focusIndex = i;
                focusLabel = p.focusLabel();
            }
        }
        return ApiResponse.success(new DataPortraitResponse(unit, labels, a, b, focusIndex, focusLabel));
    }

    private static boolean isBlank(String v) {
        return v == null || v.isBlank();
    }

    private static String nullSafe(String v) {
        return v == null ? "" : v;
    }

    private static List<List<String>> parseExcelRows(String detailJson) {
        if (isBlank(detailJson)) {
            return List.of();
        }
        try {
            String json = detailJson.trim();
            if (!json.startsWith("[") || !json.endsWith("]")) {
                return List.of();
            }
            String body = json.substring(1, json.length() - 1).trim();
            if (body.isEmpty()) {
                return List.of();
            }
            return List.of(body.split("\\],\\[")).stream()
                    .map(s -> s.replace("[", "").replace("]", ""))
                    .map(s -> List.of(s.split(",")).stream()
                            .map(v -> v.trim().replace("\"", ""))
                            .collect(Collectors.toList()))
                    .collect(Collectors.toList());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
