package com.community.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 文件附件元数据表 —— 报修图片等
 */
@TableName("attachment")
public class Attachment {

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 关联类型：repair=报修工单 */
    private String relatedType;
    /** 关联ID */
    private Integer relatedId;
    /** 存储文件名（UUID） */
    private String fileName;
    /** 原始文件名 */
    private String originalName;
    /** 存储路径 */
    private String filePath;
    /** 文件大小（字节） */
    private Long fileSize;
    /** MIME类型 */
    private String contentType;
    /** 创建时间 */
    private LocalDateTime createTime;

    // getter/setter
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getRelatedType() { return relatedType; }
    public void setRelatedType(String relatedType) { this.relatedType = relatedType; }
    public Integer getRelatedId() { return relatedId; }
    public void setRelatedId(Integer relatedId) { this.relatedId = relatedId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
