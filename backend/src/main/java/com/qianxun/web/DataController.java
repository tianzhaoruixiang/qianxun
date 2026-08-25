package com.qianxun.web;

import com.qianxun.config.QianxunProperties;
import com.qianxun.context.UserContext;
import com.qianxun.domain.DataFile;
import com.qianxun.domain.DataPortraitPoint;
import com.qianxun.repo.DataFileRepository;
import com.qianxun.repo.DataPortraitRepository;
import com.qianxun.storage.ArchiveUnpacker;
import com.qianxun.storage.FilePublicLinks;
import com.qianxun.storage.FolderPaths;
import com.qianxun.storage.MinioStorage;
import com.qianxun.storage.OfficeContentExtractor;
import com.qianxun.web.dto.ApiRequest;
import com.qianxun.web.dto.ApiResponse;
import com.qianxun.web.dto.BatchUploadResponse;
import com.qianxun.web.dto.CreateFolderRequest;
import com.qianxun.web.dto.DataFileResponse;
import com.qianxun.web.dto.DataFileDetailResponse;
import com.qianxun.web.dto.DataPortraitResponse;
import com.qianxun.web.dto.DeleteFolderRequest;
import com.qianxun.web.dto.IdRequest;
import com.qianxun.web.dto.QueryDataFileDetailRequest;
import com.qianxun.web.dto.UpsertDataFileRequest;
import com.qianxun.web.dto.UpsertDataPortraitRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据画像与用户云盘接口。
 * 列表/详情/上传按当前用户隔离；支持文件夹、批量上传、ZIP 递归解压、图片理解与下载删除。
 */
@RestController
@RequestMapping("/QianXunService/data")
public class DataController {

    private static final int DEFAULT_FILE_LIMIT = 500;
    private static final int MAX_BATCH_FILES = 100;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final Set<String> ALLOWED_EXT = Set.of(
            "doc", "docx", "xls", "xlsx", "csv", "pdf", "txt", "md", "json", "xml", "html", "htm",
            "png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "tif", "tiff", "eml", "msg", "ppt", "pptx",
            "zip"
    );

    private final DataFileRepository fileRepository;
    private final DataPortraitRepository portraitRepository;
    private final MinioStorage minioStorage;
    private final QianxunProperties properties;

    public DataController(
            DataFileRepository fileRepository,
            DataPortraitRepository portraitRepository,
            MinioStorage minioStorage,
            QianxunProperties properties
    ) {
        this.fileRepository = fileRepository;
        this.portraitRepository = portraitRepository;
        this.minioStorage = minioStorage;
        this.properties = properties;
    }

    @PostMapping("/files/list")
    public ApiResponse<List<DataFileResponse>> listFiles(@RequestBody(required = false) ApiRequest<Object> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        String userId = UserContext.getCurrentUserId();
        List<DataFile> files = fileRepository.listByUserIdOrderByDateDesc(userId, DEFAULT_FILE_LIMIT);
        List<DataFileResponse> data = files.stream().map(this::toResponse).toList();
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
        if (body == null) {
            return ApiResponse.error(400, "参数不能为空");
        }
        java.util.Optional<DataFile> opt;
        if (!isBlank(body.id())) {
            opt = fileRepository.findById(body.id().trim());
            if (opt.isEmpty() || !ownedByCurrentUser(opt.get())) {
                return ApiResponse.error(404, "文件不存在");
            }
        } else if (!isBlank(body.publicToken())) {
            opt = fileRepository.findByPublicToken(body.publicToken().trim());
            if (opt.isEmpty() || opt.get().isFolder()) {
                return ApiResponse.error(404, "文件不存在");
            }
        } else {
            return ApiResponse.error(400, "id 不能为空");
        }
        return ApiResponse.success(toDetailResponse(opt.get()));
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
        String userId = UserContext.getCurrentUserId();
        if (existing.isPresent() && !ownedByCurrentUser(existing.get())) {
            return ApiResponse.error(404, "文件不存在");
        }
        DataFile prev = existing.orElse(null);
        String folderPath = body.folderPath() != null
                ? FolderPaths.normalize(body.folderPath())
                : (prev == null ? "" : FolderPaths.normalize(prev.folderPath()));
        DataFile file = new DataFile(
                id, userId, body.name().trim(), body.date().trim(), body.kind().trim(),
                body.detailText(), body.detailJson(),
                prev == null ? null : prev.objectKey(),
                prev == null ? null : prev.contentType(),
                prev == null ? null : prev.sizeBytes(),
                prev == null ? null : prev.publicToken(),
                folderPath,
                prev == null ? now : prev.createdAt(),
                now
        );
        if (existing.isPresent()) {
            fileRepository.update(file);
        } else {
            fileRepository.insert(file);
        }
        return ApiResponse.success(toResponse(file));
    }

    @PostMapping("/folders/create")
    public ApiResponse<DataFileResponse> createFolder(@RequestBody ApiRequest<CreateFolderRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        CreateFolderRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null) {
            return ApiResponse.error(400, "参数不能为空");
        }
        String name = FolderPaths.sanitizeSegment(body.name());
        if (name.isEmpty()) {
            return ApiResponse.error(400, "文件夹名称无效");
        }
        String parent = FolderPaths.normalize(body.parentPath());
        String userId = UserContext.getCurrentUserId();
        if (!parent.isEmpty() && !folderExists(userId, parent)) {
            return ApiResponse.error(400, "上级文件夹不存在");
        }
        if (fileRepository.findFolder(userId, parent, name).isPresent()) {
            return ApiResponse.error(409, "同名文件夹已存在");
        }
        Instant now = Instant.now();
        DataFile folder = new DataFile(
                newId(), userId, name, DATE_FMT.format(now.atZone(ZoneId.systemDefault())),
                DataFile.KIND_FOLDER, null, null, null, null, null, null, parent, now, now
        );
        fileRepository.insert(folder);
        return ApiResponse.success(toResponse(folder));
    }

    @PostMapping(value = "/files/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchUploadResponse> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folderPath", required = false) String folderPath,
            @RequestParam(value = "relativePath", required = false) String relativePath
    ) {
        UploadAccumulate acc = new UploadAccumulate();
        processOneUpload(file, folderPath, relativePath, acc);
        if (acc.ok.isEmpty() && !acc.errors.isEmpty()) {
            String msg = acc.errors.get(0);
            int code = msg.contains("对象存储未配置") ? 503 : 400;
            return ApiResponse.error(code, msg);
        }
        if (acc.ok.isEmpty()) {
            return ApiResponse.error(400, "请选择要上传的文件");
        }
        return ApiResponse.success(new BatchUploadResponse(acc.ok, acc.errors, acc.ok.size(), acc.errors.size()));
    }

    @PostMapping(value = "/files/upload-batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<BatchUploadResponse> uploadBatch(
            @RequestParam(value = "files", required = false) MultipartFile[] files,
            @RequestParam(value = "folderPath", required = false) String folderPath,
            @RequestParam(value = "relativePaths", required = false) String[] relativePaths
    ) {
        if (files == null || files.length == 0) {
            return ApiResponse.error(400, "请选择要上传的文件");
        }
        if (files.length > MAX_BATCH_FILES) {
            return ApiResponse.error(400, "单次最多上传 " + MAX_BATCH_FILES + " 个文件");
        }
        UploadAccumulate acc = new UploadAccumulate();
        for (int i = 0; i < files.length; i++) {
            String rel = relativePaths != null && i < relativePaths.length ? relativePaths[i] : null;
            processOneUpload(files[i], folderPath, rel, acc);
        }
        return ApiResponse.success(new BatchUploadResponse(acc.ok, acc.errors, acc.ok.size(), acc.errors.size()));
    }

    @PostMapping("/files/delete")
    public ApiResponse<Integer> deleteFile(@RequestBody ApiRequest<IdRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        IdRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null || isBlank(body.id())) {
            return ApiResponse.error(400, "id 不能为空");
        }
        Optional<DataFile> opt = fileRepository.findById(body.id().trim());
        if (opt.isEmpty() || !ownedByCurrentUser(opt.get())) {
            return ApiResponse.error(404, "文件不存在");
        }
        DataFile f = opt.get();
        if (f.isFolder()) {
            return ApiResponse.success(deleteFolderTree(UserContext.getCurrentUserId(), f.fullPath()));
        }
        if (!isBlank(f.objectKey())) {
            minioStorage.deleteObject(f.objectKey());
        }
        fileRepository.deleteById(f.id());
        return ApiResponse.success(1);
    }

    @PostMapping("/folders/delete")
    public ApiResponse<Integer> deleteFolder(@RequestBody ApiRequest<DeleteFolderRequest> request) {
        ApiRequestSupport.applyGeneralArgument(request);
        DeleteFolderRequest body = ApiRequestSupport.jsonArg(request);
        if (body == null) {
            return ApiResponse.error(400, "参数不能为空");
        }
        String path = FolderPaths.normalize(body.path());
        if (path.isEmpty()) {
            return ApiResponse.error(400, "不能删除根目录");
        }
        String userId = UserContext.getCurrentUserId();
        if (!folderExists(userId, path) && descendants(userId, path).isEmpty()) {
            return ApiResponse.error(404, "文件夹不存在");
        }
        return ApiResponse.success(deleteFolderTree(userId, path));
    }

    @GetMapping("/files/download/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable("id") String id) {
        if (isBlank(id)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<DataFile> opt = fileRepository.findById(id.trim());
        if (opt.isEmpty() || !ownedByCurrentUser(opt.get()) || opt.get().isFolder() || isBlank(opt.get().objectKey())) {
            return ResponseEntity.notFound().build();
        }
        return streamObject(opt.get(), true);
    }

    @GetMapping("/files/public/{token}")
    public ResponseEntity<StreamingResponseBody> publicDownload(@PathVariable("token") String token) {
        if (isBlank(token)) {
            return ResponseEntity.badRequest().build();
        }
        Optional<DataFile> opt = fileRepository.findByPublicToken(token.trim());
        if (opt.isEmpty() || isBlank(opt.get().objectKey()) || opt.get().isFolder()) {
            return ResponseEntity.notFound().build();
        }
        return streamObject(opt.get(), false);
    }

    @GetMapping("/files/public/{token}/detail")
    public ApiResponse<DataFileDetailResponse> publicFileDetail(@PathVariable("token") String token) {
        if (isBlank(token)) {
            return ApiResponse.error(400, "token 不能为空");
        }
        Optional<DataFile> opt = fileRepository.findByPublicToken(token.trim());
        if (opt.isEmpty() || opt.get().isFolder()) {
            return ApiResponse.error(404, "文件不存在");
        }
        return ApiResponse.success(toDetailResponse(opt.get()));
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

    private void processOneUpload(MultipartFile file, String rawFolderPath, String relativePath, UploadAccumulate acc) {
        String label = file == null || isBlank(file.getOriginalFilename())
                ? "未命名文件" : file.getOriginalFilename();
        try {
            if (file == null || file.isEmpty()) {
                acc.errors.add(label + "：请选择要上传的文件");
                return;
            }
            String original = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
            if (original.isBlank()) {
                acc.errors.add(label + "：文件名不能为空");
                return;
            }
            // 目录上传：优先用 relativePath（含 webkitRelativePath）
            String effectiveName = original;
            String relParent = "";
            if (!isBlank(relativePath)) {
                String safeRel = ArchiveUnpacker.sanitizeZipRelativePath(relativePath.trim());
                if (safeRel.isEmpty()) {
                    acc.errors.add(label + "：相对路径无效");
                    return;
                }
                effectiveName = FolderPaths.nameOf(safeRel);
                relParent = FolderPaths.parentOf(safeRel);
                if (effectiveName.isEmpty()) {
                    acc.errors.add(label + "：相对路径无效");
                    return;
                }
            }
            String ext = extensionOf(effectiveName);
            if (!ALLOWED_EXT.contains(ext)) {
                acc.errors.add(effectiveName + "：不支持的文件类型");
                return;
            }
            int maxMb = properties.getMinio() == null ? 50 : Math.max(1, properties.getMinio().getMaxFileSizeMb());
            long maxBytes = maxMb * 1024L * 1024L;
            if (file.getSize() > maxBytes) {
                acc.errors.add(effectiveName + "：文件超过 " + maxMb + "MB 限制");
                return;
            }
            if (!minioStorage.isEnabled()) {
                acc.errors.add(effectiveName + "：对象存储未配置");
                return;
            }
            byte[] bytes;
            try {
                bytes = file.getBytes();
            } catch (Exception ex) {
                acc.errors.add(effectiveName + "：读取上传文件失败");
                return;
            }
            String userId = UserContext.getCurrentUserId();
            String baseFolder = ensureFolderChain(userId, FolderPaths.normalize(rawFolderPath));
            String targetFolder = relParent.isEmpty()
                    ? baseFolder
                    : ensureFolderChain(userId, FolderPaths.normalize(
                    baseFolder.isEmpty() ? relParent : baseFolder + "/" + relParent));

            if (ArchiveUnpacker.isZipName(effectiveName)) {
                String zipStem = effectiveName.length() > 4
                        ? FolderPaths.sanitizeSegment(effectiveName.substring(0, effectiveName.length() - 4))
                        : "archive";
                if (zipStem.isEmpty()) {
                    zipStem = "archive";
                }
                String extractRoot = ensureFolderChain(userId, FolderPaths.normalize(
                        targetFolder.isEmpty() ? zipStem : targetFolder + "/" + zipStem));
                try {
                    List<ArchiveUnpacker.ExtractedEntry> entries = ArchiveUnpacker.unpackZip(
                            bytes, ArchiveUnpacker.Limits.defaults());
                    if (entries.isEmpty()) {
                        acc.errors.add(effectiveName + "：压缩包内无可用文件");
                        return;
                    }
                    for (ArchiveUnpacker.ExtractedEntry entry : entries) {
                        String entryParent = FolderPaths.parentOf(entry.relativePath());
                        String entryFolder = entryParent.isEmpty()
                                ? extractRoot
                                : ensureFolderChain(userId, FolderPaths.normalize(
                                extractRoot.isEmpty() ? entryParent : extractRoot + "/" + entryParent));
                        String entryExt = extensionOf(entry.filename());
                        if (!ALLOWED_EXT.contains(entryExt) || ArchiveUnpacker.isZipName(entry.filename())) {
                            acc.errors.add(entry.filename() + "：压缩包内不支持的文件类型");
                            continue;
                        }
                        if (entry.bytes().length > maxBytes) {
                            acc.errors.add(entry.filename() + "：超过 " + maxMb + "MB 限制");
                            continue;
                        }
                        try {
                            acc.ok.add(persistBytes(userId, entry.filename(), entry.bytes(), guessContentType(entry.filename()), entryFolder));
                        } catch (Exception ex) {
                            acc.errors.add(entry.filename() + "：" + (ex.getMessage() == null ? "写入失败" : ex.getMessage()));
                        }
                    }
                } catch (Exception ex) {
                    acc.errors.add(effectiveName + "：解压失败（" + (ex.getMessage() == null ? "未知错误" : ex.getMessage()) + "）");
                }
                return;
            }

            String contentType = file.getContentType();
            if (isBlank(contentType)) {
                contentType = guessContentType(effectiveName);
            }
            try {
                acc.ok.add(persistBytes(userId, effectiveName, bytes, contentType, targetFolder));
            } catch (Exception ex) {
                acc.errors.add(effectiveName + "：" + (ex.getMessage() == null ? "上传失败" : ex.getMessage()));
            }
        } catch (Exception ex) {
            acc.errors.add(label + "：" + (ex.getMessage() == null ? "上传失败" : ex.getMessage()));
        }
    }

    private DataFileResponse persistBytes(
            String userId,
            String filename,
            byte[] bytes,
            String contentType,
            String folderPath
    ) {
        String id = newId();
        String token = newId();
        String kind = DataFile.kindFromFilename(filename);
        String type = isBlank(contentType) ? "application/octet-stream" : contentType;
        OfficeContentExtractor.Result extracted = OfficeContentExtractor.extract(filename, bytes);
        String detailText = extracted.hasText()
                ? extracted.text()
                : buildUploadNote(filename, bytes.length, kind);
        String detailJson = extracted.excelJson();
        Instant now = Instant.now();
        String objectKey;
        try (InputStream in = new java.io.ByteArrayInputStream(bytes)) {
            objectKey = minioStorage.putUserFile(userId, id, filename, in, bytes.length, type);
        } catch (Exception ex) {
            throw new IllegalStateException("上传到对象存储失败", ex);
        }
        DataFile row = new DataFile(
                id, userId, filename, DATE_FMT.format(now.atZone(ZoneId.systemDefault())),
                kind, detailText, detailJson, objectKey, type, (long) bytes.length, token, folderPath, now, now
        );
        fileRepository.insert(row);
        return toResponse(row);
    }

    private static final class UploadAccumulate {
        final List<DataFileResponse> ok = new ArrayList<>();
        final List<String> errors = new ArrayList<>();
    }

    private ResponseEntity<StreamingResponseBody> streamObject(DataFile f, boolean attachment) {
        InputStream in;
        try {
            in = minioStorage.getObject(f.objectKey());
        } catch (Exception ex) {
            return ResponseEntity.status(502).build();
        }
        String contentType = isBlank(f.contentType()) ? MediaType.APPLICATION_OCTET_STREAM_VALUE : f.contentType();
        String encoded = URLEncoder.encode(f.name(), StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = (attachment ? "attachment" : "inline") + "; filename*=UTF-8''" + encoded;
        StreamingResponseBody body = out -> {
            try (in) {
                in.transferTo(out);
            }
        };
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition);
        if (f.sizeBytes() != null && f.sizeBytes() >= 0) {
            builder.header(HttpHeaders.CONTENT_LENGTH, String.valueOf(f.sizeBytes()));
        }
        return builder.body(body);
    }

    private String ensureFolderChain(String userId, String path) {
        String normalized = FolderPaths.normalize(path);
        if (normalized.isEmpty()) {
            return "";
        }
        String parent = "";
        Instant now = Instant.now();
        String date = DATE_FMT.format(now.atZone(ZoneId.systemDefault()));
        for (String seg : normalized.split("/")) {
            if (fileRepository.findFolder(userId, parent, seg).isEmpty()) {
                DataFile folder = new DataFile(
                        newId(), userId, seg, date, DataFile.KIND_FOLDER,
                        null, null, null, null, null, null, parent, now, now
                );
                fileRepository.insert(folder);
            }
            parent = FolderPaths.join(parent, seg);
        }
        return normalized;
    }

    private boolean folderExists(String userId, String fullPath) {
        String path = FolderPaths.normalize(fullPath);
        if (path.isEmpty()) {
            return true;
        }
        String parent = FolderPaths.parentOf(path);
        String name = FolderPaths.nameOf(path);
        return fileRepository.findFolder(userId, parent, name).isPresent();
    }

    private List<DataFile> descendants(String userId, String fullPath) {
        String path = FolderPaths.normalize(fullPath);
        List<DataFile> out = new ArrayList<>();
        for (DataFile f : fileRepository.listByUserId(userId)) {
            if (matchesFolderTree(f, path)) {
                out.add(f);
            }
        }
        return out;
    }

    private int deleteFolderTree(String userId, String fullPath) {
        List<DataFile> rows = descendants(userId, fullPath);
        for (DataFile f : rows) {
            if (!isBlank(f.objectKey())) {
                minioStorage.deleteObject(f.objectKey());
            }
        }
        List<String> ids = rows.stream().map(DataFile::id).toList();
        return fileRepository.deleteByIds(ids);
    }

    static boolean matchesFolderTree(DataFile f, String fullPath) {
        String path = FolderPaths.normalize(fullPath);
        if (path.isEmpty()) {
            return true;
        }
        if (f.isFolder()) {
            String self = FolderPaths.join(f.folderPath(), f.name());
            return FolderPaths.isSelfOrUnder(self, path);
        }
        return FolderPaths.isSelfOrUnder(f.folderPath(), path);
    }

    private DataFileDetailResponse toDetailResponse(DataFile f) {
        List<List<String>> rows = parseExcelRows(f.detailJson());
        return new DataFileDetailResponse(
                f.id(), f.name(), f.displayDate(), f.kind(), nullSafe(f.detailText()), rows,
                FilePublicLinks.relativePath(f.publicToken()), f.publicToken(), f.contentType(), f.sizeBytes(),
                FolderPaths.normalize(f.folderPath())
        );
    }

    private DataFileResponse toResponse(DataFile f) {
        return new DataFileResponse(
                f.id(), f.name(), f.displayDate(), f.kind(),
                FilePublicLinks.url(properties, f.publicToken()),
                f.publicToken(), f.contentType(), f.sizeBytes(),
                previewOf(f.detailText()),
                FolderPaths.normalize(f.folderPath())
        );
    }

    private static String previewOf(String detailText) {
        if (detailText == null) {
            return null;
        }
        String t = detailText.strip().replace('\n', ' ').replace('\t', ' ');
        if (t.startsWith("已上传文档")) {
            return null;
        }
        if (t.length() > 120) {
            t = t.substring(0, 120) + "…";
        }
        return t.isBlank() ? null : t;
    }

    private boolean ownedByCurrentUser(DataFile file) {
        String uid = UserContext.getCurrentUserId();
        return file.userId() == null || file.userId().isBlank() || uid.equals(file.userId());
    }

    private static String buildUploadNote(String name, long size, String kind) {
        if (DataFile.KIND_IMAGE.equals(kind)) {
            return "已上传图片「" + name + "」（" + size + " 字节），可通过公开链接阅读画面。";
        }
        return "已上传文档「" + name + "」（" + size + " 字节），可通过公开链接阅读。";
    }

    private static String extensionOf(String filename) {
        String name = filename.trim().toLowerCase(Locale.ROOT);
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : "";
    }

    private static String guessContentType(String filename) {
        String ext = extensionOf(filename);
        return switch (ext) {
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "svg" -> "image/svg+xml";
            case "pdf" -> "application/pdf";
            case "txt", "md" -> "text/plain";
            case "html", "htm" -> "text/html";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "zip" -> "application/zip";
            case "doc" -> "application/msword";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls" -> "application/vnd.ms-excel";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt" -> "application/vnd.ms-powerpoint";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            default -> "application/octet-stream";
        };
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
