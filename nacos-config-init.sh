#!/bin/bash
# ============================================================
# Nacos 配置中心初始化脚本
# 将共享配置推送到 Nacos，供 user-service 和 property-service 读取
#
# 前置条件：Nacos 已启动（localhost:8848），账号密码均为 nacos
# 执行方式：在 Git Bash 中运行 bash nacos-config-init.sh
# ============================================================

NACOS_URL="http://localhost:8848"
USERNAME="nacos"
PASSWORD="nacos"

# ---- 1. 共享配置：JWT 密钥 ----
# 两个微服务共用同一个 JWT secret，统一在 Nacos 管理
curl -s -X POST "${NACOS_URL}/nacos/v1/cs/configs" \
  -d "dataId=community-property.yaml" \
  -d "group=DEFAULT_GROUP" \
  -d "username=${USERNAME}" \
  -d "password=${PASSWORD}" \
  -d "content=jwt:
  secret: community-property-ms-secret-key-2026
  expiration: 86400000
"

echo ""
echo "============================================"
echo "  Nacos 配置初始化完成！"
echo "  dataId: community-property.yaml"
echo "  group:  DEFAULT_GROUP"
echo "============================================"
echo ""
echo "验证方式："
echo "  浏览器打开 http://localhost:8848/nacos"
echo "  → 配置管理 → 配置列表 → 查看 community-property.yaml"
