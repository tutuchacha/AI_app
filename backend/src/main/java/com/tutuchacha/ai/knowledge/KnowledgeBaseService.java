package com.tutuchacha.ai.knowledge;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.tutuchacha.ai.common.BusinessException;
import com.tutuchacha.ai.common.ErrorCode;
import com.tutuchacha.ai.knowledge.dto.KnowledgeBaseRequest;
import com.tutuchacha.ai.knowledge.dto.KnowledgeBaseView;
import com.tutuchacha.ai.user.User;
import com.tutuchacha.ai.user.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * 知识库 CRUD：只看得到自己的库；不属于本人的一律 403，不存在 404。
 * 文档与切片挂在库下，删库由外键级联清理。
 */
@Service
public class KnowledgeBaseService {

    private static final String DEFAULT_EMBEDDING_MODEL = "text-embedding-v3";
    private static final int DEFAULT_CHUNK_SIZE = 800;
    private static final int DEFAULT_CHUNK_OVERLAP = 120;
    private static final int MAX_KNOWLEDGE_BASES = 50;
    private static final DateTimeFormatter ISO_LOCAL_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final KnowledgeBaseMapper knowledgeBaseMapper;
    private final UserMapper userMapper;

    public KnowledgeBaseService(KnowledgeBaseMapper knowledgeBaseMapper, UserMapper userMapper) {
        this.knowledgeBaseMapper = knowledgeBaseMapper;
        this.userMapper = userMapper;
    }

    public List<KnowledgeBaseView> list(String username) {
        User user = findUser(username);
        if (user == null) {
            return List.of();
        }
        return knowledgeBaseMapper.selectList(Wrappers.<KnowledgeBase>lambdaQuery()
                        .eq(KnowledgeBase::getUserId, user.getId())
                        .orderByDesc(KnowledgeBase::getId)
                        .last("LIMIT " + MAX_KNOWLEDGE_BASES))
                .stream()
                .map(this::toView)
                .toList();
    }

    public KnowledgeBaseView detail(String username, Long id) {
        return toView(owned(username, id));
    }

    @Transactional
    public KnowledgeBaseView create(String username, KnowledgeBaseRequest request) {
        User user = requireUser(username);
        Long count = knowledgeBaseMapper.selectCount(Wrappers.<KnowledgeBase>lambdaQuery()
                .eq(KnowledgeBase::getUserId, user.getId()));
        if (count != null && count >= MAX_KNOWLEDGE_BASES) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "最多创建 " + MAX_KNOWLEDGE_BASES + " 个知识库");
        }

        KnowledgeBase entity = new KnowledgeBase();
        entity.setUserId(user.getId());
        apply(entity, request);
        knowledgeBaseMapper.insert(entity);
        return toView(entity);
    }

    /** 全量覆盖：未传的字段回落默认值，便于前端「只改名称」也直接提交整表单。 */
    @Transactional
    public KnowledgeBaseView update(String username, Long id, KnowledgeBaseRequest request) {
        KnowledgeBase entity = owned(username, id);
        apply(entity, request);
        knowledgeBaseMapper.updateById(entity);
        return toView(knowledgeBaseMapper.selectById(entity.getId()));
    }

    @Transactional
    public void delete(String username, Long id) {
        knowledgeBaseMapper.deleteById(owned(username, id).getId());
    }

    private void apply(KnowledgeBase entity, KnowledgeBaseRequest request) {
        int chunkSize = request.chunkSize() == null ? DEFAULT_CHUNK_SIZE : request.chunkSize();
        int chunkOverlap = request.chunkOverlap() == null ? DEFAULT_CHUNK_OVERLAP : request.chunkOverlap();
        if (chunkOverlap >= chunkSize) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "chunkOverlap 必须小于 chunkSize");
        }
        entity.setName(request.name().trim());
        entity.setDescription(request.description() == null ? "" : request.description().trim());
        entity.setEmbeddingModel(StringUtils.hasText(request.embeddingModel())
                ? request.embeddingModel().trim() : DEFAULT_EMBEDDING_MODEL);
        entity.setChunkSize(chunkSize);
        entity.setChunkOverlap(chunkOverlap);
        if (entity.getDocumentCount() == null) {
            entity.setDocumentCount(0);
        }
    }

    /** 取当前用户名下的知识库：不存在 404，别人的 403。 */
    private KnowledgeBase owned(String username, Long id) {
        KnowledgeBase entity = knowledgeBaseMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "知识库不存在");
        }
        User user = findUser(username);
        if (user == null || !Objects.equals(entity.getUserId(), user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权访问该知识库");
        }
        return entity;
    }

    private User requireUser(String username) {
        User user = findUser(username);
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在");
        }
        return user;
    }

    private User findUser(String username) {
        return userMapper.selectOne(Wrappers.<User>lambdaQuery().eq(User::getUsername, username));
    }

    private KnowledgeBaseView toView(KnowledgeBase entity) {
        return new KnowledgeBaseView(entity.getId(), entity.getName(), entity.getDescription(),
                entity.getEmbeddingModel(), entity.getChunkSize(), entity.getChunkOverlap(),
                entity.getDocumentCount(), iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private String iso(LocalDateTime time) {
        return time == null ? "" : time.format(ISO_LOCAL_DATE_TIME);
    }
}
