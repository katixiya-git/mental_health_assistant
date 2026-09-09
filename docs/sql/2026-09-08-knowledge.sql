-- 知识库分类表 + 文章表（授权自动执行：MYSQL_PWD=123456 mysql -uroot mental_health_assistant < 本文件）
CREATE TABLE IF NOT EXISTS knowledge_category (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_name VARCHAR(50) NOT NULL COMMENT '分类名称',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '父分类ID，0=顶级（预留层级）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序值，越小越靠前',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_category_name (category_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文章分类表';

CREATE TABLE IF NOT EXISTS knowledge_article (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    category_id BIGINT NOT NULL COMMENT '分类ID',
    title VARCHAR(200) NOT NULL COMMENT '标题',
    summary VARCHAR(1000) DEFAULT NULL COMMENT '摘要',
    content LONGTEXT COMMENT '正文（富文本 HTML）',
    cover_image VARCHAR(255) DEFAULT NULL COMMENT '封面相对路径（OSS）',
    tags VARCHAR(500) DEFAULT NULL COMMENT '标签，逗号分隔',
    author_id BIGINT NOT NULL COMMENT '作者(管理员)用户ID',
    read_count INT NOT NULL DEFAULT 0 COMMENT '阅读数',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0草稿 1已发布 2已下线',
    published_at DATETIME DEFAULT NULL COMMENT '发布时间',
    created_at DATETIME NOT NULL COMMENT '创建时间',
    updated_at DATETIME NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_category (category_id),
    KEY idx_status_published (status, published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识文章表';

-- 种子分类（幂等）
INSERT IGNORE INTO knowledge_category (category_name, parent_id, sort_order, created_at, updated_at) VALUES
('心理健康', 0, 1, NOW(), NOW()),
('情绪管理', 0, 2, NOW(), NOW()),
('压力应对', 0, 3, NOW(), NOW()),
('人际交往', 0, 4, NOW(), NOW()),
('自我成长', 0, 5, NOW(), NOW());
