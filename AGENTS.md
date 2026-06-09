# AGENTS.md

## 1. 协作默认偏好

- 可以用英文搜索、查资料和思考，但回复用户时使用中文。
- 编写文档时使用中文；写入文件的中文内容优先使用 UTF-8。
- 修改代码前，先用简短中文说明计划。
- 修改完成后，在可行时运行相关测试或构建。
- 非必要不要新增依赖，优先复用现有 Spring Boot、Vue、原生 JavaScript/CSS 能力。
- 不要默认用户提供的前提一定正确；遇到故障先定位真实原因，再给方案。
- 每次执行用户交代的任务前，按用户偏好先称呼“爸爸”。

## 2. 项目定位

`CodeMate AI` 是一个前后端分离的本地 AI 对话助手项目，面向学习、演示、代码问答、截图分析和资料问答场景。

当前重点能力：

- 多轮对话
- SSE 流式输出
- 会话列表管理
- 文本/代码附件上传
- 图片理解
- 联网搜索补充上下文
- 本地 JSON 持久化会话历史

这个项目目前不是企业级多用户平台，而是单用户、本地运行、快速迭代的原型。后续改动时应优先保持简单、可验证、低依赖，不要一上来引入数据库、登录系统、状态管理库、消息队列或复杂 RAG。

## 3. 仓库结构

```text
ai-agent/
├─ ai-agent-backend/      Spring Boot 后端
├─ ai-agent-frontend/     Vue 3 前端
├─ chat-history/          运行期生成的会话历史目录
├─ README.md              面向使用者的项目说明
└─ AGENTS.md              面向协作者/代理的项目认知说明
```

## 4. 技术栈与版本基线

### 后端

- Java 21
- Spring Boot 4.0.6
- Spring Web
- LangChain4j 0.31.0
- Maven
- JUnit 5

### 前端

- Vue 3.5.x
- Vite 8
- 原生 JavaScript
- 原生 CSS

### 关键事实

- 后端 `pom.xml` 要求 Java 21。
- 前端没有 TypeScript、Pinia、UI 组件库或测试框架。
- 依赖较少，新增依赖前要先判断是否真的解决当前问题。

## 5. 当前架构现状

### 5.1 后端整体

后端当前仍是“Controller 编排业务”的轻量结构，聊天主流程集中在：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/controller/AiChatController.java`

后端职责包括：

- 请求校验
- 会话创建和消息落盘
- prompt 组装
- LangChain4j 文本模型调用
- 手写 OpenAI-compatible 图片理解调用
- SSE 流式事件输出
- 联网搜索
- 本地 JSON 会话持久化
- 运行期上下文记忆管理

注意：此前曾讨论过可优化为 `ChatOrchestrationService`、模型适配层、搜索接口等结构，但当前主工作区代码尚未完成该拆分。不要把“计划中的架构”误当作当前事实。

### 5.2 前端整体

前端目前是单页面、单组件为主：

- `ai-agent-frontend/src/main.js`：挂载 Vue 应用。
- `ai-agent-frontend/src/App.vue`：包含状态、事件、请求、SSE 解析、附件处理、富文本渲染和模板。
- `ai-agent-frontend/src/style.css`：全局样式。

`App.vue` 当前约 900 多行，职责偏重。小需求可以继续沿用当前结构；中等以上功能建议优先拆分 API 工具、消息渲染、打字机队列、会话侧栏、消息区和输入区。

## 6. 后端模块认知

### 6.1 聊天入口

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/controller/AiChatController.java`

接口：

- `POST /api/chat`：非流式对话。
- `POST /api/chat/stream`：SSE 流式对话。
- `DELETE /api/chat/memory`：清空指定会话的历史消息和运行期记忆。

典型流程：

1. `AttachmentPromptService.validate` 校验消息和附件。
2. `ConversationStore.ensure` 确保会话存在。
3. 先把用户消息写入本地历史。
4. `buildPrompt` 组装 prompt。
5. 如果有图片，走 `OpenAiVisionStreamService`；否则走 LangChain4j 文本链路。
6. 流式接口通过 SSE 发送 `token`、`done`、`error` 事件。
7. 成功后写入 assistant 消息。
8. 更新 `ChatMemoryManager` 中的运行期上下文。

关键取舍：

- 用户消息会先落盘，AI 失败时可能只留下用户消息。
- 图片链路和文本链路不是同一套实现。
- 流式接口的前端展示有“打字机队列”，不要把前端主动减速误判为后端慢。

### 6.2 会话管理与持久化

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/controller/ConversationController.java`
- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/service/ConversationStore.java`

`ConversationStore` 特点：

- 默认保存到 `chat-history/conversations.json`。
- 内存中维护 `LinkedHashMap<String, ConversationDto>`。
- 方法使用 `synchronized` 做基础同步。
- 创建、追加消息、重命名、置顶、删除、清空消息后立即保存。

排序策略：

- 置顶优先。
- 再按更新时间倒序。

标题策略：

- 默认标题是“新的对话”。
- 第一条用户消息写入后，按内容截断生成标题。

边界：

- 适合本地 demo。
- 不适合多用户、高并发或强事务场景。
- JSON 文件损坏时恢复能力有限。

### 6.3 上下文记忆

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/memory/ChatMemoryManager.java`

当前使用 `MessageWindowChatMemory`，最大消息窗口为 80 条。

必须区分：

- `chat-history/conversations.json` 是历史存档，重启后仍可查看。
- `ChatMemoryManager` 是进程内上下文窗口，重启后会丢失，也会被窗口大小裁剪。

如果用户说“历史还在但模型不记得”，优先检查这两个概念是否被混淆。

### 6.4 附件处理

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/service/AttachmentPromptService.java`

支持类型：

- `image`
- `text`

限制：

- 最多 8 个附件。
- 单张图片最大 5MB。
- 单个文本文件最大 200KB。
- 文本附件拼进 prompt 时最多保留 60000 字符，超出会追加截断提示。

处理方式：

- 文本附件：直接拼接进 prompt。
- 图片附件：发送给多模态接口，使用 Data URL。

取舍：

- 文本附件不是知识库检索，只是 prompt 内联。
- 没有语义切块、向量检索、引用定位。
- 文件越大，prompt 成本和上下文压力越明显。

### 6.5 图片理解

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/service/OpenAiVisionStreamService.java`

当前实现：

- 使用 Java `HttpClient` 直连 OpenAI-compatible `/chat/completions`。
- 请求体手写 JSON。
- 图片用 `image_url`，URL 值为 Data URL。
- 历史消息会转换成 OpenAI Chat Completions 风格。
- 响应按 SSE 行读取，解析 `delta.content` 或 `message.content`。

注意事项：

- `base-url` 只需要配置到 `/v1`，不要配置到 `/chat/completions`。
- 供应商必须兼容 OpenAI Chat Completions 的多模态和 SSE 格式。
- 图片失败时，优先检查模型是否支持视觉、代理是否支持图片、返回格式是否兼容。

### 6.6 联网搜索

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/service/WebSearchService.java`

当前方案：

- 直接请求 Bing 搜索页面。
- 用 Jsoup 抓取 `li.b_algo`。
- 提取标题、摘要和链接。
- 最多取 5 条。
- 超时时间 8 秒。

这是低成本但脆弱的方案。若用户反馈联网搜索不好用，先验证搜索摘要是否抓到有效内容，不要只调 prompt。

### 6.7 AI 配置

文件：

- `ai-agent-backend/src/main/java/com/cx/ai_agent_backend/config/AiConfig.java`
- `ai-agent-backend/src/main/resources/application.properties`

当前配置：

```properties
spring.application.name=ai-agent-backend

langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.base-url=${OPENAI_BASE_URL:https://api.openai.com/v1}
langchain4j.open-ai.chat-model.model-name=${OPENAI_MODEL:gpt-5.4}
```

含义：

- `OPENAI_API_KEY` 必须在启动后端前设置，否则 Spring Boot 会因占位符无法解析而启动失败。
- `OPENAI_BASE_URL` 可覆盖默认 API 地址。
- `OPENAI_MODEL` 可覆盖默认模型名。
- 项目使用的是 OpenAI-compatible 接口，不要默认一定连 OpenAI 官方。

PowerShell 临时启动示例：

```powershell
cd E:\SpringBootVueTest\ai-agent\ai-agent-backend

$env:OPENAI_API_KEY="你的真实 API Key"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:OPENAI_MODEL="gpt-5.4"

mvn spring-boot:run
```

安全提醒：

- 不要把真实 API Key 写进 `application.properties`、README 或测试文件。
- 如果从代理服务切到 OpenAI 官方，确认模型名是账号可用模型。
- 出现 `Service Unavailable` 时，优先判断是上游模型/代理返回 503，而不是前端页面问题。

## 7. 前端模块认知

### 7.1 页面组成

`App.vue` 可按职责理解为四块：

1. 左侧会话栏：新建、分组、置顶、重命名、删除。
2. 主聊天区：空状态、消息列表、assistant 富文本展示。
3. 输入区：文本输入、附件上传、深度思考、联网搜索。
4. 辅助弹层：会话菜单、账号菜单、设置弹层。

### 7.2 请求与代理

文件：

- `ai-agent-frontend/vite.config.js`

Vite 将 `/api` 代理到：

```text
http://localhost:8080
```

所以前端开发时：

- 前端默认地址：`http://localhost:5173`
- 后端默认地址：`http://localhost:8080/api`

如果前端报接口不可用，先检查：

1. 后端 8080 是否启动。
2. Vite 代理是否仍指向 8080。
3. 请求失败发生在代理层、后端层还是模型上游层。

### 7.3 SSE 与打字机效果

前端流式请求逻辑在 `App.vue` 中：

- `streamChat`
- `extractSseEvents`
- `handleSseEvent`
- `enqueueTokens`
- `startTypewriter`
- `waitForTokenQueue`

行为：

- 后端 `token` 事件不会立刻全部显示。
- 前端先把 token 拆成字符队列。
- 定时器每次取少量字符追加到 assistant 消息。
- 后端 `done` 后，前端还会等待本地 token 队列吐完。

排查流式延迟时要区分：

- 后端/上游模型真的慢。
- 前端打字机队列在主动慢速渲染。

### 7.4 附件前端处理

前端限制与后端基本一致：

- 最多 8 个附件。
- 图片最大 5MB。
- 文本最大 200KB。
- 支持图片、文本和常见代码文件扩展名。

图片通过 `FileReader.readAsDataURL` 读取；文本通过 `readAsText(file, 'UTF-8')` 读取。

### 7.5 富文本渲染

前端手写轻量 Markdown 风格渲染：

- 标题
- 无序列表
- 有序列表
- 加粗
- 行内代码
- 链接

这不是完整 Markdown 解析器，不要默认支持表格、代码块、多级嵌套列表或复杂 Markdown。

## 8. 典型数据流

### 8.1 流式文本对话

1. 用户输入文本。
2. 前端乐观插入 user 消息和空 assistant 消息。
3. 前端请求 `POST /api/chat/stream`。
4. 后端校验并写入 user 消息到 `ConversationStore`。
5. 后端构建 prompt。
6. 后端将 prompt 加入 `ChatMemoryManager`。
7. LangChain4j 文本流式模型返回 token。
8. 后端发送 SSE `token`。
9. 前端进入打字机队列。
10. 后端完成后写入 assistant 消息并发送 `done`。
11. 前端刷新当前会话，使本地展示和持久化历史收敛。

### 8.2 图片对话

1. 前端读取图片为 Data URL。
2. 后端识别到附件中有 `image`。
3. 走 `OpenAiVisionStreamService`，不走纯文本 LangChain4j 流式模型。
4. 图片和文本 prompt 一起组装到 OpenAI-compatible 多模态请求。
5. 流式 token 继续通过 SSE 回前端。

### 8.3 联网搜索对话

1. 前端打开联网搜索开关。
2. 后端判断是否是短追问。
3. 如果不是依赖上文的追问，调用 `WebSearchService.search`。
4. 搜索摘要拼进 prompt。
5. 如果判断是依赖上文的追问，则跳过联网搜索，避免搜索结果覆盖会话上下文。

## 9. 接口清单

### 聊天相关

- `POST /api/chat`
- `POST /api/chat/stream`
- `DELETE /api/chat/memory`

### 会话相关

- `GET /api/conversations`
- `POST /api/conversations`
- `GET /api/conversations/{id}`
- `PATCH /api/conversations/{id}/title`
- `PATCH /api/conversations/{id}/pin`
- `DELETE /api/conversations/{id}`

## 10. 测试与验证

### 后端测试

已有测试：

- `ConversationStoreTests`：会话落盘重载、清空消息后重载仍为空。
- `AttachmentPromptServiceTests`：附件消息、文本附件拼接、不支持类型、短追问识别。
- `OpenAiVisionStreamServiceTests`：图片请求体包含历史消息和 `image_url`。
- `AiAgentBackendApplicationTests`：Spring 上下文基础启动测试。

运行：

```powershell
cd E:\SpringBootVueTest\ai-agent\ai-agent-backend
mvn test
```

### 前端验证

当前没有自动化测试。前端改动后至少运行：

```powershell
cd E:\SpringBootVueTest\ai-agent\ai-agent-frontend
npm run build
```

如涉及页面交互，还应手动验证：

- 创建会话
- 发送普通文本
- 流式显示
- 上传文本附件
- 上传图片附件
- 联网搜索开关
- 深度思考开关
- 重命名、置顶、删除会话
- 清空记忆

## 11. 运行方式

### 后端启动

```powershell
cd E:\SpringBootVueTest\ai-agent\ai-agent-backend

$env:OPENAI_API_KEY="你的真实 API Key"
$env:OPENAI_BASE_URL="https://api.openai.com/v1"
$env:OPENAI_MODEL="gpt-5.4"

mvn spring-boot:run
```

默认接口：

```text
http://localhost:8080/api
```

### 前端启动

```powershell
cd E:\SpringBootVueTest\ai-agent\ai-agent-frontend
npm install
npm run dev
```

默认地址：

```text
http://localhost:5173
```

## 12. 常见故障排查

### 12.1 后端启动失败：找不到 OPENAI_API_KEY

典型错误：

```text
Could not resolve placeholder 'OPENAI_API_KEY'
```

原因：当前终端没有设置 `OPENAI_API_KEY`。

处理：在同一个 PowerShell 会话中设置环境变量后重新启动后端。

### 12.2 页面显示 Service Unavailable

不要直接认定前端坏了。按层排查：

1. 请求 `/api/conversations` 是否正常。如果正常，前端代理和后端 REST 基本没问题。
2. 请求 `/api/chat/stream` 是否失败。如果失败，多半在模型调用层。
3. 检查 `OPENAI_BASE_URL` 是否仍指向废弃代理。
4. 检查 `OPENAI_MODEL` 是否为当前供应商支持的模型。
5. 直接调用供应商 `/chat/completions` 做最小请求，确认是否上游返回 503。

### 12.3 历史在但模型不记得

优先确认：

- 历史是否在 `chat-history/conversations.json`。
- 相关上下文是否仍在 `ChatMemoryManager` 的 80 条窗口内。
- 后端是否重启过。

### 12.4 图片识别失败

优先确认：

- 模型是否支持视觉。
- `base-url` 是否兼容 OpenAI Chat Completions 多模态格式。
- 图片是否超过大小限制。
- Data URL 是否完整。
- 返回流是否包含 `delta.content` 或 `message.content`。

### 12.5 中文乱码

仓库中部分历史内容或会话标题可能出现编码显示异常。读写文件时优先使用 UTF-8，修改中文文案后要实际打开页面或文件确认显示。

## 13. 开发取舍

### 13.1 保持简单

当前项目的价值在于功能清楚、链路可解释、适合面试展示。不要因为“看起来更高级”而过早引入复杂架构。

### 13.2 可以优先做的优化

如果继续演进，优先级建议：

1. 把 `AiChatController` 的聊天编排抽到服务层。
2. 抽象文本模型、流式文本模型、视觉模型适配层。
3. 为 prompt 构建、聊天成功/失败落盘、SSE 错误事件补测试。
4. 拆分 `App.vue` 中的 API 请求、SSE 解析、打字机队列和富文本渲染。
5. 为联网搜索增加更清晰的失败兜底和日志。

### 13.3 暂不优先做的事

除非用户明确要求，否则不优先：

- 登录鉴权
- 多用户隔离
- 数据库替换 JSON
- 向量库/RAG
- UI 组件库
- 前端状态管理库
- 大规模重构

## 14. 给后续协作者的排查顺序

接手问题时，建议按这个顺序判断：

1. 问题发生在前端展示、后端接口、模型上游还是本地持久化。
2. 判断是普通文本链路还是图片链路。
3. 判断是否开启联网搜索或深度思考。
4. 如果是流式问题，区分后端 SSE 慢还是前端打字机队列慢。
5. 如果是记忆问题，区分会话历史和运行期上下文窗口。
6. 最后再下钻到具体类和接口。

这样能减少误改，也更容易把问题解释清楚。
