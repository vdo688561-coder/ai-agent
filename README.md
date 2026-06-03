# CodeMate AI 智能对话助手

CodeMate AI 是一个基于 Spring Boot 和 Vue 3 的前后端分离对话项目，面向代码学习、截图分析和资料问答场景。项目支持流式回复、会话历史、附件上传、图片理解、联网搜索和本地会话持久化。

## 功能概览

- AI 对话：支持普通对话和 SSE 流式输出。
- 会话管理：支持创建、查看、重命名、置顶、删除会话，以及清空上下文记忆。
- 附件处理：支持文本/代码文件上传，将文件内容拼接进提示词。
- 图片理解：支持图片附件，将图片以多模态请求形式发送给兼容 OpenAI Chat Completions 的接口。
- 联网搜索：通过后端抓取搜索结果摘要，为 AI 回复补充时效性信息。
- 本地持久化：会话历史保存到本地 JSON 文件，便于个人学习和演示。

## 技术栈

### 后端

- Java 21
- Spring Boot 4
- Spring Web
- LangChain4j
- SSE
- Maven
- JUnit 5

### 前端

- Vue 3
- Vite
- JavaScript
- CSS

## 项目结构

```text
ai-agent/
├── ai-agent-backend/     # Spring Boot 后端服务
├── ai-agent-frontend/    # Vue 3 前端页面
├── chat-history/         # 本地会话历史，运行时生成
└── README.md             # 项目说明
```

## 运行前准备

### 环境要求

- JDK 21
- Maven 3.9+
- Node.js 20+
- npm

注意：后端 `pom.xml` 配置的 Java 版本是 21。如果 Maven 实际使用 JDK 17，会出现 class file version 不兼容问题。可以通过以下命令确认：

```bash
mvn -version
```

### 后端环境变量

项目通过环境变量读取 API Key：

```bash
OPENAI_API_KEY=你的 API Key
```

后端当前使用的是 OpenAI-compatible API 配置，`base-url` 和 `model-name` 可在 `ai-agent-backend/src/main/resources/application.properties` 中调整。

## 启动项目

### 启动后端

```bash
cd ai-agent-backend
mvn spring-boot:run
```

默认后端接口路径为：

```text
http://localhost:8080/api
```

### 启动前端

```bash
cd ai-agent-frontend
npm install
npm run dev
```

前端开发服务默认运行在：

```text
http://localhost:5173
```

## 构建与测试

### 前端构建

```bash
cd ai-agent-frontend
npm run build
```

### 后端测试

```bash
cd ai-agent-backend
mvn test
```

如果测试失败并提示 Java class file version 不兼容，优先检查 Maven 是否使用 JDK 21。

## 核心接口

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/chat` | 非流式 AI 对话 |
| `POST` | `/api/chat/stream` | SSE 流式 AI 对话 |
| `DELETE` | `/api/chat/memory` | 清空指定会话记忆 |
| `GET` | `/api/conversations` | 获取会话列表 |
| `POST` | `/api/conversations` | 创建新会话 |
| `GET` | `/api/conversations/{id}` | 获取会话详情 |
| `PATCH` | `/api/conversations/{id}/title` | 重命名会话 |
| `PATCH` | `/api/conversations/{id}/pin` | 置顶或取消置顶 |
| `DELETE` | `/api/conversations/{id}` | 删除会话 |

## 后续优化方向

- 使用数据库替换本地 JSON 文件，提高数据可靠性和扩展能力。
- 增加登录鉴权和用户维度的数据隔离。
- 抽象 AI 服务适配层，支持不同模型供应商切换。
- 增加接口集成测试和前端组件测试。
- 补充 Docker Compose，一键启动前后端服务。
