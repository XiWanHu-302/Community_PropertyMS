package com.community.user.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.community.common.Result;
import com.community.user.entity.Attachment;
import com.community.user.mapper.AttachmentMapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文件服务 —— 上传 / 下载 / 预览（本地磁盘存储）
 */
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Resource
    private AttachmentMapper attachmentMapper;

    /**
     * 上传文件（支持多文件）
     * POST /file/upload?relatedType=repair&relatedId=1
     */
    @PostMapping("/upload")
    @PreAuthorize("hasAnyRole('ADMIN','RESIDENT')")
    public Result<List<Attachment>> upload(@RequestParam("files") MultipartFile[] files,
                                           @RequestParam String relatedType,
                                           @RequestParam Integer relatedId) {
        List<Attachment> list = new java.util.ArrayList<>();
        for (MultipartFile file : files) {
            if (file.isEmpty()) continue;
            try {
                // 生成存储目录：uploads/repair/2026/07/
                String subDir = relatedType + "/" + java.time.LocalDate.now().getYear()
                        + "/" + String.format("%02d", java.time.LocalDate.now().getMonthValue());
                Path dir = Paths.get(uploadDir, subDir);
                Files.createDirectories(dir);

                // UUID 文件名防重名
                String ext = getExt(file.getOriginalFilename());
                String storeName = UUID.randomUUID().toString() + ext;
                Path target = dir.resolve(storeName);
                file.transferTo(target.toFile());

                // 记录元数据
                Attachment att = new Attachment();
                att.setRelatedType(relatedType);
                att.setRelatedId(relatedId);
                att.setFileName(storeName);
                att.setOriginalName(file.getOriginalFilename());
                att.setFilePath(subDir + "/" + storeName);
                att.setFileSize(file.getSize());
                att.setContentType(file.getContentType());
                att.setCreateTime(LocalDateTime.now());
                attachmentMapper.insert(att);
                list.add(att);
            } catch (IOException e) {
                return Result.fail("文件上传失败: " + e.getMessage());
            }
        }
        return Result.ok(list);
    }

    /**
     * 预览/下载文件
     * GET /file/preview/{id}
     */
    @GetMapping("/preview/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> preview(@PathVariable Long id) {
        Attachment att = attachmentMapper.selectById(id);
        if (att == null) return ResponseEntity.notFound().build();

        File file = Paths.get(uploadDir, att.getFilePath()).toFile();
        if (!file.exists()) return ResponseEntity.notFound().build();

        org.springframework.core.io.Resource resource = new FileSystemResource(file);
        MediaType mediaType = MediaType.parseMediaType(
                att.getContentType() != null ? att.getContentType() : "application/octet-stream");

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + att.getOriginalName() + "\"")
                .body(resource);
    }

    /**
     * 查询某关联记录的所有附件
     * GET /file/list?relatedType=repair&relatedId=1
     */
    @GetMapping("/list")
    public Result<List<Attachment>> list(@RequestParam String relatedType,
                                          @RequestParam Integer relatedId) {
        List<Attachment> list = attachmentMapper.selectList(
                new LambdaQueryWrapper<Attachment>()
                        .eq(Attachment::getRelatedType, relatedType)
                        .eq(Attachment::getRelatedId, relatedId)
                        .orderByAsc(Attachment::getCreateTime));
        return Result.ok(list);
    }

    /** 提取文件扩展名 */
    private String getExt(String name) {
        if (name == null || !name.contains(".")) return "";
        return name.substring(name.lastIndexOf("."));
    }
}
