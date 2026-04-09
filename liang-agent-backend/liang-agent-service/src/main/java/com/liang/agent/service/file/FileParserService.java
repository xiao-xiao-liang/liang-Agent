package com.liang.agent.service.file;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文件解析服务
 * <p>
 * 负责解析不同类型文件的内容：
 * <ul>
 *   <li>PDF：使用 Apache PDFBox 解析</li>
 *   <li>DOCX：使用 Apache POI 解析</li>
 *   <li>TXT：直接读取</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class FileParserService {

    /**
     * 最大文本内容长度限制（用于 extracted_text 截断展示）
     */
    private static final int MAX_TEXT_LENGTH = 20000;

    /**
     * 文件解析结果
     *
     * @param fullText      全量文本（用于向量化）
     * @param truncatedText 截断文本（用于数据库存储和展示）
     */
    public record ParseResult(String fullText, String truncatedText) {
    }

    /**
     * 解析上传的文件并返回文本内容
     *
     * @param file 上传的文件
     * @return 解析结果（包含全量文本和截断文本）
     */
    public ParseResult parseFile(MultipartFile file) {
        String fullText = parseFileInternal(file);
        String truncatedText = truncateIfNeeded(fullText);
        return new ParseResult(fullText, truncatedText);
    }

    /**
     * 内部方法：解析文件并返回完整文本内容
     */
    private String parseFileInternal(MultipartFile file) {
        String fileType = FilenameUtils.getExtension(file.getOriginalFilename());
        long fileSize = file.getSize();

        log.info("开始解析文件: {} (类型: {}, 大小: {} bytes)", file.getOriginalFilename(), fileType, fileSize);

        try {
            String content = switch (fileType.toLowerCase()) {
                case "pdf" -> parsePdf(file);
                case "docx" -> parseDocx(file);
                case "doc" -> throw new IllegalArgumentException("暂不支持 .doc 格式，请转换为 .docx");
                case "txt" -> parseTxt(file);
                default -> throw new IllegalArgumentException("不支持的文件类型: " + fileType);
            };

            log.info("文件解析完成，内容长度: {} 字符", content.length());
            return content;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件解析失败: {}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 如果需要则截断文本
     */
    private String truncateIfNeeded(String content) {
        if (content.length() > MAX_TEXT_LENGTH) {
            log.warn("文件内容过长，将截断至 {} 字符", MAX_TEXT_LENGTH);
            return content.substring(0, MAX_TEXT_LENGTH) + "\n\n... (内容已截断，文件过长)";
        }
        return content;
    }

    /**
     * 解析 PDF 文件
     */
    private String parsePdf(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             PDDocument document = PDDocument.load(is)) {

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);

            String text = stripper.getText(document);
            log.info("PDF 解析完成，页数: {}, 文本长度: {}", document.getNumberOfPages(), text.length());
            return text.trim();
        }
    }

    /**
     * 解析 DOCX 文件
     */
    private String parseDocx(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream();
             XWPFDocument document = new XWPFDocument(is)) {

            StringBuilder text = new StringBuilder();
            List<XWPFParagraph> paragraphs = document.getParagraphs();

            for (XWPFParagraph paragraph : paragraphs) {
                String paraText = paragraph.getText();
                if (paraText != null && !paraText.trim().isEmpty()) {
                    text.append(paraText).append("\n");
                }
            }

            log.info("DOCX 解析完成，段落数: {}, 文本长度: {}", paragraphs.size(), text.length());
            return text.toString().trim();
        }
    }

    /**
     * 解析 TXT 文件
     */
    private String parseTxt(MultipartFile file) throws Exception {
        try (InputStream is = file.getInputStream()) {
            String text = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            log.info("TXT 解析完成，文本长度: {}", text.length());
            return text.trim();
        }
    }
}
