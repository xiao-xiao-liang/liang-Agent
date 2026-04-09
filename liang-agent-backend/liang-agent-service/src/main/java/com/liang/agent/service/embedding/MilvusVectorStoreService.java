package com.liang.agent.service.embedding;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.preretrieval.query.transformation.CompressionQueryTransformer;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Milvus 向量存储服务实现
 * <p>
 * 当配置 {@code vectorstore.type=milvus}（或未配置时默认激活）时生效。
 * 使用 Spring AI 自动配置的 VectorStore（Milvus）进行向量化存储和 RAG 检索。
 * </p>
 * <p>
 * RAG 检索流程：
 * <ol>
 *   <li>问题压缩重写（CompressionQueryTransformer）</li>
 *   <li>多查询扩展（MultiQueryExpander，3路 + 原始问题）</li>
 *   <li>按 fileid 元数据过滤的语义向量检索（top-K=5）</li>
 *   <li>结果去重返回</li>
 * </ol>
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "vectorstore.type", havingValue = "milvus", matchIfMissing = true)
public class MilvusVectorStoreService implements VectorStoreService {

    private final VectorStore vectorStore;
    private final ChatModel chatModel;

    /**
     * 向量化批次大小
     */
    private static final int EMBEDDING_BATCH_SIZE = 9;

    /**
     * 多查询扩展数量
     */
    private static final int MULTI_QUERY_COUNT = 3;

    /**
     * RAG 检索 Top-K
     */
    private static final int RAG_TOP_K = 5;

    /**
     * 删除时最大检索数量
     */
    private static final int DELETE_MAX_FETCH = 1000;

    /**
     * 问题压缩重写器（无状态，复用实例）
     */
    private CompressionQueryTransformer queryTransformer;

    /**
     * 多查询扩展器（无状态，复用实例）
     */
    private QueryExpander queryExpander;

    /**
     * 初始化 RAG 检索组件（避免每次请求重复创建）
     */
    @PostConstruct
    public void init() {
        ChatClient chatClient = ChatClient.builder(chatModel).build();
        this.queryTransformer = CompressionQueryTransformer.builder()
                .chatClientBuilder(chatClient.mutate())
                .build();
        this.queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClient.mutate())
                .numberOfQueries(MULTI_QUERY_COUNT)
                .includeOriginal(true)
                .build();
        log.info("RAG 检索组件初始化完成");
    }

    @Override
    public void embedAndStore(List<Document> documents) {
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            List<Document> batch = documents.subList(i, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
            vectorStore.add(batch);
            log.debug("向量化存储批次完成: 第{}-{}条", i + 1, Math.min(i + EMBEDDING_BATCH_SIZE, documents.size()));
        }
        log.info("向量化存储全部完成: 总数={}", documents.size());
    }

    @Override
    public List<String> ragRetrieve(String fileId, String question) {
        log.info("RAG 检索开始: fileId={}, question={}", fileId, question);

        if (StringUtils.isBlank(fileId) || StringUtils.isBlank(question)) {
            log.warn("RAG 检索参数为空: fileId={}, question={}", fileId, question);
            return Collections.singletonList("检索参数不能为空");
        }

        try {
            Query query = Query.builder().text(question).build();

            // 1. 问题压缩重写
            Query compressed = queryTransformer.transform(query);
            log.info("压缩重写后的Query: {}", compressed.text());

            // 2. 多查询扩展（3路 + 原始问题）
            List<Query> expandedQueries = queryExpander.expand(compressed);
            log.info("扩展后的Query数量：{}", expandedQueries.size());

            // 3. 按 fileid 过滤的语义向量检索
            List<String> results = new ArrayList<>();
            Set<String> seenIds = new HashSet<>();

            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            var filter = builder.eq("fileid", fileId).build();

            for (Query eq : expandedQueries) {
                List<Document> docs = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(eq.text())
                                .topK(RAG_TOP_K)
                                .filterExpression(filter)
                                .build());

                for (Document doc : docs) {
                    if (seenIds.add(doc.getId()))
                        results.add(doc.getText());
                }
            }

            log.info("RAG 检索完成: fileId={}, 返回结果数={}", fileId, results.size());
            return results;

        } catch (Exception e) {
            log.error("RAG 检索失败: fileId={}, question={}", fileId, question, e);
            return Collections.singletonList("RAG 检索失败: " + e.getMessage());
        }
    }

    @Override
    public void deleteByFileId(String fileId) {
        log.info("删除向量记录: fileId={}", fileId);
        try {
            // 通过元数据过滤查询出所有属于该文件的文档，然后逐一删除
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            var filter = builder.eq("fileid", fileId).build();

            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("*")
                            .topK(DELETE_MAX_FETCH)
                            .filterExpression(filter)
                            .build());

            if (!docs.isEmpty()) {
                List<String> ids = docs.stream().map(Document::getId).toList();
                vectorStore.delete(ids);
                log.info("向量记录删除完成: fileId={}, 删除数量={}", fileId, ids.size());
            }
        } catch (Exception e) {
            log.warn("向量记录删除失败: fileId={}, error={}", fileId, e.getMessage());
        }
    }
}
