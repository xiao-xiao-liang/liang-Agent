package com.liang.agent.service.file;

import com.liang.agent.model.entity.FileInfo;
import com.liang.agent.model.enums.FileStatus;
import com.liang.agent.service.embedding.OverlapParagraphTextSplitter;
import com.liang.agent.service.embedding.VectorStoreService;
import com.liang.agent.service.storage.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 文件管理编排服务
 * <p>
 * 核心业务逻辑：编排文件上传的全流程——元数据保存 → 对象存储上传 → 文本解析 → 大文件向量化 → 图片识别。
 * 通过策略接口 {@link StorageService} 和 {@link VectorStoreService} 实现存储和向量化的可替换。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileManageService {

    private final StorageService storageService;
    private final FileParserService fileParserService;
    private final FileInfoService fileInfoService;
    private final VectorStoreService vectorStoreService;
    @Qualifier("multimodalChatModel")
    private final OpenAiChatModel multimodalChatModel;

    /**
     * 大文件阈值（字符数）——超过此阈值的文件将自动向量化
     */
    private static final int LARGE_FILE_THRESHOLD = 5000;

    /**
     * 文本切分块大小
     */
    private static final int CHUNK_SIZE = 500;

    /**
     * 文本切分重叠字符数
     */
    private static final int CHUNK_OVERLAP = 50;

    /**
     * 文件大小上限（50MB）
     */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    /**
     * 允许的文件类型白名单
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
            "pdf", "docx", "txt", "jpg", "jpeg", "png", "gif", "bmp"
    );

    /**
     * 上传文件（全流程编排）
     * <p>
     * 事务仅覆盖数据库写操作，远程 I/O（MinIO、Milvus、多模态 API）放在事务外，
     * 避免长事务导致数据库连接池耗尽。
     * </p>
     *
     * @param file 上传的文件
     * @return 文件信息实体
     */
    public FileInfo uploadFile(MultipartFile file) {
        // 0. 入参校验（业务层双重限制）
        validateFile(file);

        String fileId = UUID.randomUUID().toString();
        String fileType = FilenameUtils.getExtension(file.getOriginalFilename());
        long fileSize = file.getSize();

        log.info("开始处理文件上传: fileId={}, fileName={}, fileType={}, fileSize={}",
                fileId, file.getOriginalFilename(), fileType, fileSize);

        try {
            // 1. 保存元数据（事务）
            FileInfo fileInfo = saveInitialMetadata(fileId, file.getOriginalFilename(), fileType, fileSize);

            // 2. 上传到对象存储（远程 I/O，事务外）
            String objectName = generateObjectName(fileId, fileType);
            String storagePath = storageService.uploadFile(file, objectName);
            log.info("对象存储上传完成: fileId={}", fileId);

            // 3. 更新存储路径和状态（事务）
            updateStoragePath(fileInfo, storagePath);

            // 4. 根据文件类型进行后续处理（远程 I/O，事务外）
            if (isTextFile(fileType)) {
                processTextFile(fileId, file, fileInfo);
            } else if (isImageFile(fileType)) {
                processImageFile(fileId, file, fileInfo);
            } else {
                log.info("其他类型文件上传完成: fileId={}, 类型: {}", fileId, fileType);
            }

            log.info("文件上传完成: fileId={}", fileId);
            return fileInfo;
        } catch (Exception e) {
            log.error("文件上传失败: fileId={}", fileId, e);
            markAsFailed(fileId);
            throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
        }
    }

    /**
     * 校验上传文件（大小 + 类型白名单）
     */
    private void validateFile(MultipartFile file) {
        if (file.getSize() > MAX_FILE_SIZE)
            throw new IllegalArgumentException("文件大小不能超过 50MB");

        String ext = FilenameUtils.getExtension(file.getOriginalFilename());
        if (StringUtils.isBlank(ext) || !ALLOWED_TYPES.contains(ext.toLowerCase()))
            throw new IllegalArgumentException("不支持的文件类型: " + ext);
    }

    /**
     * 保存初始文件元数据
     */
    private FileInfo saveInitialMetadata(String fileId, String fileName, String fileType, long fileSize) {
        FileInfo fileInfo = FileInfo.builder()
                .fileId(fileId)
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .status(FileStatus.PROCESSING.name())
                .build();
        fileInfoService.saveFileInfo(fileInfo);
        return fileInfo;
    }

    /**
     * 更新存储路径和状态
     */
    private void updateStoragePath(FileInfo fileInfo, String storagePath) {
        fileInfo.setMinioPath(storagePath);
        fileInfo.setStatus(FileStatus.SUCCESS.name());
        fileInfoService.updateByFileId(fileInfo);
    }

    /**
     * 标记文件为失败状态
     */
    private void markAsFailed(String fileId) {
        fileInfoService.getByFileId(fileId).ifPresent(info -> {
            info.setStatus(FileStatus.FAILED.name());
            fileInfoService.updateByFileId(info);
        });
    }

    /**
     * 处理文本类文件（PDF/DOCX/TXT）
     */
    private void processTextFile(String fileId, MultipartFile file, FileInfo fileInfo) {
        try {
            FileParserService.ParseResult parseResult = fileParserService.parseFile(file);
            String fullText = parseResult.fullText();
            String extractedText = parseResult.truncatedText();

            log.info("文件解析完成: fileId={}, 全量文本长度: {}, 截断后长度: {}",
                    fileId, fullText.length(), extractedText.length());

            // 存储截断后的文本用于展示
            fileInfo.setExtractedText(extractedText);
            fileInfoService.updateByFileId(fileInfo);

            // 判断是否为大文件，如果是则使用全量文本进行向量化
            if (isLargeFile(fullText)) {
                log.info("检测到大文件，开始向量化处理: fileId={}, 全量文本长度: {}", fileId, fullText.length());
                try {
                    processLargeFileEmbedding(fileId, fullText);
                    fileInfo.setEmbed(1);
                    fileInfoService.updateByFileId(fileInfo);
                    log.info("大文件向量化完成: fileId={}", fileId);
                } catch (Exception e) {
                    log.error("大文件向量化失败: fileId={}", fileId, e);
                    // 向量化失败不影响文件上传，embed 保持为 0
                }
            }
        } catch (Exception e) {
            log.error("文件解析失败: fileId={}", fileId, e);
            fileInfo.setStatus(FileStatus.FAILED.name());
            fileInfoService.updateByFileId(fileInfo);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 处理图片文件（调用多模态 AI 识别图片内容）
     */
    private void processImageFile(String fileId, MultipartFile file, FileInfo fileInfo) {
        try {
            String extractedText = image2Text(file);
            fileInfo.setExtractedText(extractedText);
            fileInfoService.updateByFileId(fileInfo);
            log.info("图片识别完成: fileId={}, 识别文本长度: {}", fileId, extractedText.length());
        } catch (Exception e) {
            log.error("图片识别失败: fileId={}", fileId, e);
            fileInfo.setStatus(FileStatus.FAILED.name());
            fileInfoService.updateByFileId(fileInfo);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 识别图片内容
     *
     * @param file 图片文件
     * @return 图片内容的详细描述
     */
    private String image2Text(MultipartFile file) {
        if (multimodalChatModel == null)
            return "[多模态模型未配置，无法识别图片]";

        try (InputStream inputStream = file.getInputStream()) {
            byte[] imageBytes = IOUtils.toByteArray(inputStream);

            if (imageBytes.length == 0)
                throw new RuntimeException("图片文件内容为空");

            ByteArrayResource imageResource = new ByteArrayResource(imageBytes);
            var userMessage = UserMessage.builder()
                    .text("请描述这张图片的内容，包括场景、对象、布局、颜色、文字信息，直接输出纯文本描述，不要多余说明。")
                    .media(List.of(new Media(MimeTypeUtils.IMAGE_PNG, imageResource)))
                    .build();
            var response = multimodalChatModel.call(new Prompt(List.of(userMessage)));
            String resp = response.getResult().getOutput().getText();

            if (resp == null || resp.trim().isEmpty())
                return "[无法识别图片内容]";
            return resp.trim();
        } catch (Exception e) {
            log.error("图片识别异常", e);
            throw new RuntimeException("图片识别失败: " + e.getMessage(), e);
        }
    }

    /**
     * 根据 fileId 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    public FileInfo getFileInfo(String fileId) {
        return fileInfoService.getByFileId(fileId)
                .orElseThrow(() -> new IllegalArgumentException("文件不存在: " + fileId));
    }

    /**
     * 根据 fileId 获取文件内容
     *
     * @param fileId 文件ID
     * @return 文件内容
     */
    public String getFileContent(String fileId) {
        FileInfo fileInfo = getFileInfo(fileId);

        if (!FileStatus.SUCCESS.name().equals(fileInfo.getStatus()))
            throw new IllegalStateException("文件尚未处理完成，当前状态: " + fileInfo.getStatus());

        String content = fileInfo.getExtractedText();
        if (content == null || content.trim().isEmpty())
            return "该文件没有可识别的内容";
        return content;
    }

    /**
     * 检查文件是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    public boolean exists(String fileId) {
        return fileInfoService.exists(fileId);
    }

    /**
     * 删除文件（级联删除：对象存储 + DB + 向量库）
     *
     * @param fileId 文件ID
     */
    public void deleteFile(String fileId) {
        FileInfo fileInfo = getFileInfo(fileId);

        try {
            // 从对象存储删除
            if (fileInfo.getMinioPath() != null) {
                String objectName = extractObjectName(fileInfo.getMinioPath());
                storageService.deleteFile(objectName);
            }

            // 从向量库删除（如果已向量化）
            if (fileInfo.getEmbed() != null && fileInfo.getEmbed() == 1) {
                try {
                    vectorStoreService.deleteByFileId(fileId);
                } catch (Exception e) {
                    log.warn("向量库删除失败: fileId={}, error={}", fileId, e.getMessage());
                }
            }

            // 从数据库删除
            fileInfoService.deleteByFileId(fileId);
            log.info("文件删除成功: fileId={}", fileId);
        } catch (Exception e) {
            log.error("文件删除失败: fileId={}", fileId, e);
            throw new RuntimeException("文件删除失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取所有文件列表
     *
     * @return 文件列表
     */
    public List<FileInfo> getAllFiles() {
        return fileInfoService.listAll();
    }

    /**
     * 获取文件数量
     *
     * @return 文件数量
     */
    public long getFileCount() {
        return fileInfoService.count();
    }

    /**
     * 处理大文件向量化
     */
    private void processLargeFileEmbedding(String fileId, String text) {
        log.info("开始处理大文件向量化: fileId={}, 文本长度: {}", fileId, text.length());

        // 1. 创建文档
        Document document = new Document(text);
        List<Document> documents = List.of(document);

        // 2. 切分文档
        OverlapParagraphTextSplitter splitter = new OverlapParagraphTextSplitter(CHUNK_SIZE, CHUNK_OVERLAP);
        List<Document> chunks = splitter.apply(documents);
        log.info("文档切分完成: fileId={}, 切分数量: {}", fileId, chunks.size());

        // 3. 为每个切分添加元数据
        for (int i = 0; i < chunks.size(); i++) {
            Document chunk = chunks.get(i);
            chunk.getMetadata().put("fileid", fileId);
            chunk.getMetadata().put("chunkId", i);
        }

        // 4. 向量化并存储
        vectorStoreService.embedAndStore(chunks);
        log.info("大文件向量化存储完成: fileId={}, 切分数量: {}", fileId, chunks.size());
    }

    /**
     * 生成对象存储路径名称
     */
    public static String generateObjectName(String fileId, String fileType) {
        return "file-" + fileId.replace("-", "") + "." + fileType.toLowerCase();
    }

    /**
     * 判断是否为文本文件
     */
    private boolean isTextFile(String fileType) {
        return "pdf".equalsIgnoreCase(fileType)
                || "docx".equalsIgnoreCase(fileType)
                || "doc".equalsIgnoreCase(fileType)
                || "txt".equalsIgnoreCase(fileType);
    }

    /**
     * 判断是否为图片文件
     */
    private boolean isImageFile(String fileType) {
        return "jpg".equalsIgnoreCase(fileType)
                || "jpeg".equalsIgnoreCase(fileType)
                || "png".equalsIgnoreCase(fileType)
                || "gif".equalsIgnoreCase(fileType)
                || "bmp".equalsIgnoreCase(fileType);
    }

    /**
     * 从完整路径中提取对象名称
     */
    private String extractObjectName(String fullPath) {
        if (fullPath == null || !fullPath.contains("/"))
            return fullPath;
        return fullPath.substring(fullPath.lastIndexOf("/") + 1);
    }

    /**
     * 判断是否为大文件
     */
    private boolean isLargeFile(String text) {
        if (StringUtils.isBlank(text))
            return false;
        return text.length() >= LARGE_FILE_THRESHOLD;
    }
}
