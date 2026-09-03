# CODEBUDDY.md This file provides guidance to CodeBuddy when working with code in this repository.

## 项目概述

千寻智能体 - 企业级AI智能问答与知识管理系统。前端采用 **Vue 3 + TypeScript + Ant Design Vue 4 + Pinia + Vite** 技术栈，基于 OpenSpec 规范驱动开发。

## 需求文档

生成代码前必须参考以下文档：

| 文档 | 路径 | 用途 |
|------|------|------|
| 需求规格说明书 | `openspec/specs/qianxun-agent-requirements.md` | 功能模块定义、技术架构、API规划 |
| UI设计稿 | `UI.png` | **高保真还原蓝本** - 页面布局、组件样式、交互细节、色彩精确还原 |
| 开发进度跟踪 | `.codebuddy/progress/development-progress.md` | **开发计划执行状态** - 阶段进度、任务清单、产出文件 |

### 开发进度使用规范
1. **每次执行 OpenSpec Change 前**，先查看进度文件确认当前阶段和任务范围
2. **任务完成后**，在进度文件中更新对应任务状态（⬜ → ✅）
3. **阶段完成后**，更新阶段状态（⚪ 未开始 → 🟢 进行中 → ✅ 完成）并计算进度百分比
4. **新增需求或变更**，同步更新需求文档和进度文件，确保两者一致

### 需求文档核心内容
- **2.1 核心功能模块**：左侧导航栏(N1-N7)、AI智能问答(C1-C13)、知识源配置(K1-K4)、文档管理(D1-D6)、数据画像(V1-V6)
- **4.1 前端技术栈**：Vue 3 + TypeScript + Ant Design Vue 4 + Pinia + Vite + ECharts
- **6. 开发优先级**：P0-MVP（导航框架、AI对话、知识源选择、推荐问题）、P1（文档管理、文件上传）、P2（数据画像、主题优化）

### 需求文档核心内容
- **2.1 核心功能模块**：左侧导航栏(N1-N6)、AI智能问答(C1-C9)、知识源配置(K1-K4)、文档管理(D1-D6)、数据可视化(V1-V5)
- **4.1 前端技术栈**：Vue 3 + TypeScript + Ant Design Vue 4 + Pinia + Vite + ECharts
- **6. 开发优先级**：P0-MVP（导航框架、AI对话、知识源选择、推荐问题）、P1（文档管理、文件上传）、P2（图表、主题切换）

### UI设计稿核心布局
```
左侧导航(200px) + 主内容区(flex:1) + 右侧面板(320px可折叠)
```

## 常用命令

| 命令 | 说明 |
|------|------|
| `npm install` | 安装依赖 |
| `npm run dev` | 启动开发服务器 |
| `npm run build` | 生产环境打包 |
| `npm run preview` | 预览打包结果 |
| `npm run lint` | ESLint 检查 |
| `npm run test` | 运行单元测试 |
| `npm run test:watch` | 监听模式运行测试 |

## 架构设计

```
src/
├── api/              # API 接口层（基于 YAPI 规范）
├── assets/           # 静态资源
├── components/       # 公共组件
├── composables/      # 组合式函数（Hook）
├── layouts/          # 布局组件
├── router/           # 路由配置（懒加载）
├── stores/           # Pinia 状态管理
├── types/            # TypeScript 类型定义
├── utils/            # 工具函数
└── views/            # 页面组件
```

### 核心模块

- **AI对话模块**：多轮对话、数据源选择、流式响应、新建对话、历史会话
- **文档管理模块**：文件上传、列表展示（两列网格）、搜索、统计信息
- **数据画像模块**：ECharts 折线图（数据A/B对比）、单位标识、数据标注

### 开发阶段（详见进度文件）

| 阶段 | Change ID | 说明 | 优先级 |
|------|-----------|------|--------|
| 0. API接口定义 | `define-apis` | 接口声明、YAPI Schema生成 | P0 |
| 1. 项目初始化 | `init-project-scaffold` | Vue3+TS+Vite项目搭建 | P0 |
| 2. 布局框架 | `build-layout-framework` | 三栏布局、导航、右侧面板 | P0 |
| 3. AI对话核心 | `build-chat-core` | 对话界面、输入区、气泡组件 | P0 |
| 4. 知识源配置 | `build-knowledge-selector` | 数据源选择、知识库复选框 | P0 |
| 5. 文档管理 | `build-document-management` | 文件列表、搜索、上传 | P1 |
| 6. 数据画像 | `build-data-visualization` | ECharts折线图、数据标注 | P2 |
| 7. 优化与增强 | `build-theme-enhancement` | 浅色主题、动画、快捷键 | P2 |

## 规范要求（必须遵守）

所有代码生成必须遵循 `.codebuddy/rules/` 下的规范：

### 接口规范 (api-rules.md)
- Input 绑定值为空串 `""`，下拉框绑定值为 `undefined`
- 状态码：200成功、400业务失败、401无权限、500服务异常
- 返回数组而非 null，排序规则需明确
- 提示方式：业务校验用 `message`，接口操作成功/失败用 `notification`

### 组件交互 (component-interaction.md)
- 弹框：`:mask-closable="false"` + `:closable="true"`，关闭时重置表单
- 防重复提交：loading 禁用按钮，接口返回后才可再次点击
- `handleCancel` 必须同时执行 `modalVisible.value = false` 和 `formRef.value?.resetFields()`

### 时间日期 (datetime-rules.md)
- 前后端交互使用**毫秒级时间戳**（number 类型）
- 时间范围：开始 00:00:00，结束 23:59:59
- 日期选择要校验开始时间不晚于结束时间

### ECharts 图表 (echarts-rules.md)
- 默认 SVG 渲染 `renderer: 'svg'`
- 组件销毁时调用 `charts && charts.dispose()`
- charts 实例不放 data 里，窗口变化时 resize

### 表单校验 (form-validation.md)
- 必填项星号标识，校验提示统一格式
- 输入框 `.trim()` 去头尾空格，特殊字符限制
- 关闭弹框后 resetFields + nextTick 赋值

### 表格组件 (table-rules.md)
- loading 蒙层不遮表头，分页位置右下角
- 新增/编辑后保持当前页和筛选条件
- 列宽：固定内容（时间、手机号）定宽，其他自适应

### 性能优化 (performance.md)
- 路由懒加载：`component: () => import(/* webpackChunkName: "xxx" */ '@/views/...')`
- 大数据量用分页或虚拟列表，图片懒加载

### 数值处理 (number-handling.md)
- 大数格式化：101200 → 10.1万 或 101,200
- JS 精度问题 `0.1 + 0.2` 需处理

## YAPI 接口导入

接口定义需生成符合 `yapi-import.json` 规范的 JSON Schema，响应结构：
```json
{ "code": number, "message": string, "data": object }
```
时间字段类型必须为 `number`（毫秒时间戳），不得定义为 string。

## UI 设计规范（frontend-design 技能）

生成前端界面时必须遵循以下设计原则：

### 设计理念
- **Purpose**: AI智能问答系统，服务企业用户，强调专业、高效、可信赖
- **Tone**: **科技感 / 极简主义** - 深色主题 + 蓝绿渐变强调色，营造AI高科技氛围
- **Differentiation**: 流动的渐变背景、微妙的毛玻璃效果、精心设计的动画过渡

### 字体选择
- 主字体：`"PingFang SC", "Microsoft YaHei", sans-serif`（中文）
- 英文/数字：`"SF Mono", "JetBrains Mono", monospace`（代码/数据）
- 标题字体：中等粗细、较大字号、适度字间距

### 色彩系统（CSS Variables）
```css
:root {
  /* 主色调 - 深邃科技蓝 */
  --color-primary: #0ea5e9;
  --color-primary-dark: #0284c7;
  --color-primary-light: #38bdf8;

  /* 强调色 - 翠绿/青绿 */
  --color-accent: #14b8a6;
  --color-accent-glow: rgba(20, 184, 166, 0.3);

  /* 背景层次（浅色主题） */
  --bg-base: #f0f5ff;
  --bg-surface: rgba(255, 255, 255, 0.9);
  --bg-elevated: rgba(255, 255, 255, 0.6);
  --bg-glass: rgba(255, 255, 255, 0.05);

  /* 文字层次 */
  --text-primary: #1e293b;
  --text-secondary: #64748b;
  --text-muted: #94a3b8;

  /* 边框与分割 */
  --border-subtle: rgba(148, 163, 184, 0.2);
  --border-accent: rgba(14, 165, 233, 0.3);

  /* 卡片与输入 */
  --card-bg: #ffffff;
  --input-bg: #f8fafc;
}
```

### 布局结构
```
+----------+----------------------------------+------------------+
| 侧边导航  |          主内容区 (AI对话)          |   右侧面板        |
| 200px    |            flex: 1               |     320px        |
| 毛玻璃背景 |      渐变微光背景                 |   可折叠          |
+----------+----------------------------------+------------------+
```

### UI 高保真还原要求 ⚠️
**开发时必须以 `UI.png` 为准进行像素级还原：**
- 布局结构、间距、尺寸必须与设计稿完全一致
- 颜色值必须从设计稿中精确提取，不可随意更改
- 字体大小、行高、字重需与设计稿匹配
- 图标、装饰元素需与设计稿保持一致
- 交互状态（悬停、点击、禁用）效果需还原
- 遇到实现困难时先参考设计稿，禁止自行简化设计

### 动效设计
- **页面加载**: 渐入 + 向上位移，时长 400ms，ease-out
- **卡片悬停**: scale(1.02) + 阴影加深 + 边框发光
- **按钮交互**: 按下 scale(0.98)，过渡 150ms
- **对话气泡**: 打字机效果 + 淡入动画
- **面板切换**: 滑入/滑出，300ms cubic-bezier(0.4, 0, 0.2, 1)

### 视觉效果
- **背景**: 浅色渐变 + 微妙网格纹理 + 角落光晕
- **卡片**: 白色背景 + 细微阴影 + 圆角12px
- **边框**: 1px 细边框，发光效果用 box-shadow
- **图标**: 线性风格，2px 描边，与主题色一致
- **输入框**: 浅灰背景 `#f8fafc` + 聚焦时主题色边框

### 禁止事项
- ❌ 使用 Inter、Roboto、Arial 等通用字体
- ❌ 紫色渐变 + 白色背景（俗套AI风格）
- ❌ 纯色平铺背景无层次感
- ❌ 生硬的阴影和边框
- ❌ 缺乏动画或动画过于花哨
