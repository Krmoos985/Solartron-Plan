# ADR-001：从 Quarkus 迁移到 Spring Boot

## 状态
已采纳 (2026-03-10)

## 背景
项目初始使用 Quarkus 脚手架生成（pom.xml packaging=quarkus），但团队主要技术栈为 Spring Boot。

## 决策
将项目框架从 Quarkus 3.32.1 迁移到 Spring Boot 3.2.0。

## 理由
1. 团队对 Spring Boot 更熟悉，降低维护成本
2. Timefold 同时提供 Quarkus 和 Spring Boot starter，功能等价
3. Spring Boot 生态更大，社区资源更丰富

## 影响
- `pom.xml` 完全重写（parent、依赖、插件）
- 配置从 `application.properties` 改为 `application.yml`
- 包名从 `org.acme` 改为 `com.changyang.scheduling`
- 删除 Quarkus 相关的 Docker 文件
