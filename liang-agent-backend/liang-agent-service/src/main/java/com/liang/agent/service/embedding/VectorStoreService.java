package com.liang.agent.service.embedding;

import org.springframework.ai.document.Document;

import java.util.List;

/**
 * 向量存储服务策略接口
 * <p>
 * 抽象向量化和检索操作，支持多种向量存储后端（Milvus、Elasticsearch、PGVector 等）无缝替换。
 * 通过配置 {@code vectorstore.type} 选择具体实现。
 * </p>
 */
public interface VectorStoreService {

    /**
     * 向量化并存储文档
     *
     * @param documents 已切分的文档列表（需包含 fileid 元数据）
     */
    void embedAndStore(List<Document> documents);

    /**
     * RAG 检索——根据文件ID和用户问题检索相关文档片段
     *
     * @param fileId   文件ID（用于元数据过滤）
     * @param question 用户问题
     * @return 相关文档内容列表
     */
    List<String> ragRetrieve(String fileId, String question);

    /**
     * 按文件ID删除向量记录
     *
     * @param fileId 文件ID
     */
    void deleteByFileId(String fileId);
}
