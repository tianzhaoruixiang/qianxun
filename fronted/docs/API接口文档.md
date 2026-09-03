# 千寻智能体 - API 接口文档

> 本文档汇总项目中所有后端接口定义，基于 YAPI Schema。

## 目录

- [通用说明](#通用说明)
- [对话模块](#1-对话模块)
- [知识库模块](#2-知识库模块)
- [文件管理模块](#3-文件管理模块)
- [数据概览模块](#4-数据概览模块)

---

## 通用说明

### 基础信息
- **Base URL**: `/api`
- **Content-Type**: `application/json`（除文件上传外）
- **响应格式**: 统一 JSON 结构

### 响应结构
```typescript
interface ApiResponse<T> {
  code: number;      // 状态码：200成功、400业务失败、401无权限、500服务异常
  message: string;    // 提示信息
  data: T;           // 响应数据
}
```

### 时间字段
- 所有时间字段类型为 `number`（毫秒时间戳）
- 格式：`1704067200000`（2024-01-01 00:00:00）

---

## 1. 对话模块

### 1.1 发送问题

**接口**: `POST /api/chat/send`

**描述**: 提交用户问题到 AI 助手

**请求头**:
| 参数 | 必填 | 说明 |
|------|------|------|
| Content-Type | 是 | `application/json` |

**请求参数**:
```typescript
interface SendChatRequest {
  question: string;          // 用户问题内容（必填，最大2000字符）
  sessionId?: string;        // 会话ID（继续对话时传入）
  knowledgeBaseIds?: string[]; // 选中的知识库ID列表
  model?: string;            // 使用的AI模型，默认"千寻大模型"
}
```

**响应参数**:
```typescript
interface SendChatResponse {
  messageId: string;   // 消息唯一标识
  sessionId: string;   // 会话ID
  status: 'pending' | 'processing' | 'completed' | 'failed'; // 消息处理状态
}
```

**示例**:
```json
// 请求
{
  "question": "列出扬州市发展政策",
  "sessionId": "conv_123456",
  "knowledgeBaseIds": ["kb_001", "kb_002"],
  "model": "千寻大模型"
}

// 响应
{
  "code": 200,
  "message": "success",
  "data": {
    "messageId": "msg_abc123",
    "sessionId": "conv_123456",
    "status": "processing"
  }
}
```

---

### 1.2 获取流式回复 (SSE)

**接口**: `GET /api/chat/stream`

**描述**: 通过 SSE 流式获取 AI 回复

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| messageId | 是 | string | 消息ID |

**响应**: SSE 流式响应，格式：
```
data: {"code":200,"message":"success","data":{"messageId":"msg_abc123","content":"您好","isEnd":false,"timestamp":1704067200000}}

data: {"code":200,"message":"success","data":{"messageId":"msg_abc123","content":"，关于","isEnd":false,"timestamp":1704067201000}}

data: {"code":200,"message":"success","data":{"messageId":"msg_abc123","content":"您的问题","isEnd":true,"timestamp":1704067202000}}
```

**流式数据字段**:
```typescript
interface StreamResponse {
  messageId: string;   // 消息ID
  content: string;     // 当前流式响应内容片段
  isEnd: boolean;      // 是否为最后一条消息
  timestamp: number;   // 时间戳（毫秒）
}
```

---

### 1.3 获取推荐问题

**接口**: `GET /api/questions/suggested`

**描述**: 获取预设的推荐问题列表

**请求参数**: 无

**响应参数**:
```typescript
// 与项目 RecommendedQuestion 类型保持一致
interface SuggestedQuestionsResponse {
  list: Array<{
    id: string;       // 问题ID
    text: string;     // 问题内容（与前端 RecommendedQuestion.text 对应）
    category?: string; // 问题分类
  }>;
}
```

**示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      { "id": "q1", "text": "如何使用千寻智能体？", "category": "入门指南" },
      { "id": "q2", "text": "知识库有哪些类型？", "category": "知识库" }
    ]
  }
}
```

---

## 2. 知识库模块

### 2.1 获取知识库列表

**接口**: `GET /api/knowledge/list`

**描述**: 获取所有可用的知识库列表

**请求参数**: 无

**响应参数**:
```typescript
// 与项目 KnowledgeBase 类型保持一致
interface KnowledgeListResponse {
  list: Array<{
    id: string;           // 知识库ID
    name: string;         // 知识库名称
    description: string;  // 知识库描述
    type: '知识库' | '档案库' | '资源库'; // 知识库类型
    createdAt: number;    // 创建时间（毫秒）
    updatedAt: number;    // 更新时间（毫秒）
  }>;
}
```

**示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "kb_001",
        "name": "政策文件库",
        "description": "存放各类政策文件",
        "type": "知识库",
        "createdAt": 1704067200000,
        "updatedAt": 1704153600000
      }
    ]
  }
}
```

---

### 2.2 获取知识子库

**接口**: `GET /api/knowledge/sub`

**描述**: 获取指定知识库下的子库列表

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| knowledgeBaseId | 是 | string | 知识库ID |

**响应参数**:
```typescript
// 与项目 SubLibrary 类型保持一致
interface SubLibraryResponse {
  list: Array<{
    id: string;           // 子库ID
    parentId: string;     // 父知识库ID
    name: string;         // 子库名称
    type: string;         // 子库类型
  }>;
}
```

**示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "list": [
      {
        "id": "sub_001",
        "parentId": "kb_001",
        "name": "2024年政策",
        "type": "知识库"
      }
    ]
  }
}
```

---

## 3. 文件管理模块

### 3.1 获取文件列表

**接口**: `GET /api/file/list`

**描述**: 分页获取文件列表，支持按知识库筛选

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| knowledgeBaseId | 否 | string | 知识库ID筛选 |
| page | 否 | number | 页码，默认1 |
| pageSize | 否 | number | 每页数量，默认20 |
| sortField | 否 | string | 排序字段：name/size/createTime/updateTime |
| sortOrder | 否 | string | 排序方向：asc/desc |

**响应参数**:
```typescript
// 与项目 FileInfo 类型保持一致
interface FileListResponse {
  list: Array<{
    id: string;               // 文件ID
    name: string;             // 文件名
    type: string;             // 文件类型：docx/xlsx/xls/txt/pdf
    size: number;             // 文件大小（字节）
    status: 'uploading' | 'processing' | 'completed' | 'failed'; // 处理状态
    knowledgeBaseId?: string;  // 所属知识库ID
    knowledgeBaseName?: string; // 所属知识库名称
    createdAt: number;         // 上传时间（毫秒）
    updatedAt: number;         // 更新时间（毫秒）
  }>;
  total: number;  // 总数
  page: number;  // 当前页码
  pageSize: number;  // 每页数量
  totalPages: number;  // 总页数
}
```

---

### 3.2 上传文件

**接口**: `POST /api/file/upload`

**描述**: 上传文档到知识库

**请求头**:
| 参数 | 必填 | 说明 |
|------|------|------|
| Content-Type | 是 | `multipart/form-data` |

**请求参数** (FormData):
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| file | 是 | File | 文件内容 |
| knowledgeBaseId | 否 | string | 目标知识库ID |

**响应参数**:
```typescript
// 与项目 FileUploadResponse 类型保持一致
interface UploadResponse {
  id: string;      // 文件ID
  name: string;    // 文件名
  size: number;    // 文件大小（字节）
  status: string;  // 处理状态
  createdAt: number; // 上传时间（毫秒）
}
```

---

### 3.3 搜索文件

**接口**: `GET /api/file/search`

**描述**: 根据关键词搜索文件

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| keyword | 是 | string | 搜索关键词 |
| knowledgeBaseId | 否 | string | 知识库ID筛选 |
| page | 否 | number | 页码，默认1 |
| pageSize | 否 | number | 每页数量，默认20 |

**响应参数**:
```typescript
// 与项目 FileSearchResponse 类型保持一致
interface FileSearchResponse {
  list: FileInfo[];  // 文件列表（同 FileListResponse.list）
}
```

---

### 3.4 删除文件

**接口**: `DELETE /api/file/delete`

**描述**: 删除指定文件（物理删除+索引清理）

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| id | 是 | string | 文件ID |

**响应参数**:
```typescript
interface DeleteResponse {
  success: boolean; // 是否删除成功
}
```

---

## 4. 数据概览模块

### 4.1 获取统计数据

**接口**: `GET /api/dashboard/statistics`

**描述**: 获取系统核心统计数据

**请求参数**: 无

**响应参数**:
```typescript
// 与项目 DashboardStats 类型保持一致
interface StatisticsResponse {
  fileCount: number;           // 文档总数
  totalSize: number;           // 文档总大小
  conversationCount: number;    // 累计对话次数
  knowledgeBaseCount: number;   // 知识库总数
  todayQueryCount: number;      // 今日查询次数
}
```

**示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "fileCount": 3456,
    "totalSize": 1073741824,
    "conversationCount": 12580,
    "knowledgeBaseCount": 12,
    "todayQueryCount": 156
  }
}
```

---

### 4.2 获取趋势数据

**接口**: `GET /api/dashboard/trend`

**描述**: 获取指定时间范围内的数据趋势

**请求参数**:
| 参数 | 必填 | 类型 | 说明 |
|------|------|------|------|
| startDate | 是 | string | 开始日期（YYYY-MM-DD） |
| endDate | 是 | string | 结束日期（YYYY-MM-DD） |
| type | 否 | string | 数据类型 |

**响应参数**:
```typescript
// 与项目 TrendDataResponse 类型保持一致
interface TrendResponse {
  unit: string;              // 数据单位
  series: Array<{
    name: string;            // 系列名称
    data: number[];         // 数据数组
  }>;
  xAxis: string[];           // X轴标签
}
```

**示例**:
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "unit": "个",
    "series": [
      { "name": "本月", "data": [10, 12, 8, 15, 13, 18] },
      { "name": "上月", "data": [8, 9, 11, 12, 16, 14] }
    ],
    "xAxis": ["1月", "2月", "3月", "4月", "5月", "6月"]
  }
}
```

---

## 错误码说明

| 错误码 | 说明 |
|--------|------|
| 200 | 成功 |
| 400 | 业务失败（参数错误、无权限等） |
| 401 | 未授权（登录失效等） |
| 500 | 服务器内部错误 |

---

*文档生成时间: 2026-05-06*
