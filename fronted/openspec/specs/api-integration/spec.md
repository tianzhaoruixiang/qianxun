# api-integration Specification

## Purpose
TBD - created by archiving change replace-mock-with-real-api. Update Purpose after archive.
## Requirements
### Requirement: API 请求封装
系统 SHALL 提供统一的 API 请求封装，基于 Axios 实现。

#### Scenarios

- **Given** 前端需要发送 HTTP 请求
- **When** 发起 API 调用
- **Then** 使用统一的请求封装处理错误、loading 状态

### Requirement: 对话模块 API
系统 SHALL 实现对话相关 API 调用：
- 发送问题 (`POST /api/chat/send`)
- 流式获取回复 (`GET /api/chat/stream` - SSE)
- 获取推荐问题 (`GET /api/questions/suggested`)

#### Scenarios

- **Given** 用户发送问题
- **When** 调用 `POST /api/chat/send`
- **Then** 返回对话 ID 和初始回复

- **Given** 需要获取流式回复
- **When** 调用 `GET /api/chat/stream`
- **Then** 使用 SSE 格式返回增量数据

- **Given** 首页需要推荐问题
- **When** 调用 `GET /api/questions/suggested`
- **Then** 返回推荐问题列表（包含 id, text, category 字段）

### Requirement: 知识库模块 API
系统 SHALL 实现知识库相关 API 调用：
- 获取知识库列表 (`GET /api/knowledge/list`)
- 获取知识子库 (`GET /api/knowledge/sub`)

#### Scenarios

- **Given** 用户打开知识库选择器
- **When** 调用 `GET /api/knowledge/list`
- **Then** 返回知识库列表（包含 id, name, description, type 字段）

- **Given** 用户选中某个知识库
- **When** 调用 `GET /api/knowledge/sub`
- **Then** 返回该知识库下的子库列表（包含 id, parentId, name, type 字段）

### Requirement: 文件管理模块 API
系统 SHALL 实现文件管理相关 API 调用：
- 获取文件列表 (`GET /api/file/list`)
- 上传文件 (`POST /api/file/upload`)
- 搜索文件 (`GET /api/file/search`)
- 删除文件 (`DELETE /api/file/delete`)

#### Scenarios

- **Given** 用户打开文件列表
- **When** 调用 `GET /api/file/list`
- **Then** 返回分页文件列表（包含 id, name, type, size, status, knowledgeBaseId 字段）

- **Given** 用户上传文件
- **When** 调用 `POST /api/file/upload`
- **Then** 返回文件 ID 和上传状态

- **Given** 用户搜索文件
- **When** 调用 `GET /api/file/search`
- **Then** 返回匹配的文件列表

- **Given** 用户删除文件
- **When** 调用 `DELETE /api/file/delete`
- **Then** 返回删除结果

### Requirement: 数据概览模块 API
系统 SHALL 实现数据概览相关 API 调用：
- 获取统计数据 (`GET /api/dashboard/statistics`)
- 获取趋势数据 (`GET /api/dashboard/trend`)

#### Scenarios

- **Given** 用户打开数据概览页面
- **When** 调用 `GET /api/dashboard/statistics`
- **Then** 返回统计数据（包含 fileCount, totalSize, conversationCount, knowledgeBaseCount, todayQueryCount 字段）

- **Given** 用户查看趋势图表
- **When** 调用 `GET /api/dashboard/trend`
- **Then** 返回趋势数据（包含 unit, series[], xAxis[] 字段）

### Requirement: Store 改造
系统 SHALL 将 Store 中的 Mock 数据替换为真实 API 调用：
- `useChatStore` - 流式对话
- `useKnowledgeStore` - 知识库数据
- `useFileStore` - 文件列表
- `useDashboardStore` - 趋势数据

#### Scenarios

- **Given** Store 中存在 Mock 数据
- **When** 完成 API 对接
- **Then** 将 Mock 数据替换为真实 API 调用

