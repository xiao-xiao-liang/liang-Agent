-- ============================================================
-- liang-Agent 数据库初始化脚本
-- ============================================================

DROP TABLE IF EXISTS conversation;
DROP TABLE IF EXISTS chatMessage;

-- 会话表（元信息）
CREATE TABLE conversation (
    id              BIGINT       PRIMARY KEY COMMENT '主键（雪花算法）',
    conversation_id VARCHAR(64)  NOT NULL COMMENT '会话唯一标识（UUID）',
    agent_type      VARCHAR(32)  NOT NULL COMMENT '智能体类型（websearch/file/deepresearch/pptx）',
    title           VARCHAR(500) DEFAULT NULL COMMENT '会话标题（取自首条用户问题）',
    last_time       DATETIME     DEFAULT NULL COMMENT '最近消息时间',
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted         TINYINT      DEFAULT 0 NOT NULL COMMENT '是否删除 0:正常 1:删除',
    UNIQUE KEY uk_conversation_id (conversation_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话表';

-- 消息表（对话记录）
CREATE TABLE chat_message (
    id                  BIGINT       PRIMARY KEY COMMENT '主键（雪花算法）',
    conversation_id     VARCHAR(64)  NOT NULL COMMENT '所属会话ID',
    role                VARCHAR(32)  NOT NULL COMMENT '消息角色：user/assistant',
    content             TEXT         DEFAULT NULL COMMENT '消息内容（user=问题, assistant=回答）',
    thinking            LONGTEXT     DEFAULT NULL COMMENT '思考过程（仅 assistant）',
    tools               VARCHAR(500) DEFAULT NULL COMMENT '使用的工具（仅 assistant，逗号分隔）',
    `reference`         TEXT         DEFAULT NULL COMMENT '参考链接 JSON（仅 assistant）',
    recommend           TEXT         DEFAULT NULL COMMENT '推荐问题 JSON（仅 assistant）',
    file_id             VARCHAR(64)  DEFAULT NULL COMMENT '关联文件ID',
    first_response_time BIGINT       DEFAULT NULL COMMENT '首次响应时间ms（仅 assistant）',
    total_response_time BIGINT       DEFAULT NULL COMMENT '整体回复时间ms（仅 assistant）',
    create_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted             TINYINT      DEFAULT 0 NOT NULL COMMENT '是否删除 0:正常 1:删除',
    INDEX idx_conversation_time (conversation_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='会话消息记录表';

-- 文件元数据表
CREATE TABLE `file_info`
(
    `id`              bigint        NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `file_id`         varchar(255)  NOT NULL COMMENT '文件唯一标识',
    `file_name`       varchar(500)  NOT NULL COMMENT '原始文件名',
    `file_type`       varchar(50)   NULL DEFAULT NULL COMMENT '文件类型（pdf/doc/docx/txt/png/jpg等）',
    `file_size`       bigint        NULL DEFAULT NULL COMMENT '文件大小（字节）',
    `minio_path`      varchar(1000) NULL DEFAULT NULL COMMENT 'MinIO中的存储路径',
    `extracted_text`  longtext      NULL COMMENT '解析后的纯文本内容',
    `conversation_id` varchar(255)  NULL DEFAULT NULL COMMENT '会话ID（可选，用于关联特定会话）',
    `status`          varchar(50)   NULL DEFAULT 'PENDING' COMMENT '文件状态：PENDING/PROCESSING/SUCCESS/FAILED',
    `embed`           tinyint       NULL DEFAULT NULL COMMENT '是否向量化 0:否 1:是',
    `create_time`     datetime      NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     datetime      NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted`         TINYINT       DEFAULT 0 NOT NULL COMMENT '是否删除 0:正常 1:删除',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_file_id` (`file_id` ASC) USING BTREE,
    INDEX `idx_conversation_id` (`conversation_id` ASC) USING BTREE
) ENGINE = InnoDB COMMENT = '文件元数据表';
