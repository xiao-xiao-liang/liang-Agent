package com.liang.agent.controller;

import com.liang.agent.common.convention.result.Result;
import com.liang.agent.common.convention.result.Results;
import com.liang.agent.model.dto.FileInfoDTO;
import com.liang.agent.model.entity.FileInfo;
import com.liang.agent.service.file.FileManageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件管理控制器
 * <p>
 * 提供文件上传、查询、删除等 REST 接口。
 * </p>
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final FileManageService fileManageService;

    /**
     * 上传文件
     *
     * @param file 上传的文件
     * @return 文件信息
     */
    @PostMapping("/upload")
    public Result<FileInfoDTO> upload(@RequestParam("file") MultipartFile file) {
        log.info("收到文件上传请求: fileName={}, size={}", file.getOriginalFilename(), file.getSize());

        FileInfo fileInfo = fileManageService.uploadFile(file);
        FileInfoDTO dto = convertToDTO(fileInfo);
        return Results.success(dto);
    }

    /**
     * 获取文件信息
     *
     * @param fileId 文件ID
     * @return 文件信息
     */
    @GetMapping("/info/{fileId}")
    public Result<FileInfoDTO> getInfo(@PathVariable("fileId") String fileId) {
        FileInfo fileInfo = fileManageService.getFileInfo(fileId);
        FileInfoDTO dto = convertToDTO(fileInfo);
        return Results.success(dto);
    }

    /**
     * 获取文件内容
     *
     * @param fileId 文件ID
     * @return 文件内容文本
     */
    @GetMapping("/content/{fileId}")
    public Result<String> getContent(@PathVariable("fileId") String fileId) {
        String content = fileManageService.getFileContent(fileId);
        return Results.success(content);
    }

    /**
     * 删除文件
     *
     * @param fileId 文件ID
     * @return 操作结果
     */
    @DeleteMapping("/{fileId}")
    public Result<Void> delete(@PathVariable("fileId") String fileId) {
        log.info("收到文件删除请求: fileId={}", fileId);
        fileManageService.deleteFile(fileId);
        return Results.success();
    }

    /**
     * 获取所有文件列表
     *
     * @return 文件列表
     */
    @GetMapping("/list")
    public Result<List<FileInfoDTO>> list() {
        List<FileInfoDTO> dtoList = fileManageService.getAllFiles().stream()
                .map(this::convertToDTO)
                .toList();
        return Results.success(dtoList);
    }

    /**
     * 检查文件是否存在
     *
     * @param fileId 文件ID
     * @return 是否存在
     */
    @GetMapping("/exists/{fileId}")
    public Result<Boolean> exists(@PathVariable("fileId") String fileId) {
        boolean exists = fileManageService.exists(fileId);
        return Results.success(exists);
    }

    /**
     * 将 FileInfo 实体转换为 DTO
     */
    private FileInfoDTO convertToDTO(FileInfo fileInfo) {
        return new FileInfoDTO(
                fileInfo.getFileId(),
                fileInfo.getFileName(),
                fileInfo.getFileType(),
                fileInfo.getFileSize(),
                fileInfo.getMinioPath(),
                fileInfo.getConversationId(),
                fileInfo.getStatus(),
                fileInfo.getEmbed(),
                fileInfo.getCreateTime()
        );
    }
}
