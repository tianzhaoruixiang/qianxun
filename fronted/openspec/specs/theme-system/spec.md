## ADDED Requirements

### Requirement: 浅色主题 CSS 变量体系

系统 SHALL 在 `src/styles/variables.css` 中定义完整的浅色主题 CSS 变量集。所有变量 SHALL 定义在 `:root` 选择器下，**不包含深色模式切换**。

变量 SHALL 覆盖以下类别：

**主色调（基于 UI 设计稿 UI.png 精确提取）：**
- `--color-primary`: #3b82f6 （品牌蓝 — 按钮、激活态、链接）
- `--color-primary-dark`: #2563eb （深蓝 — 按钮悬停）
- `--color-primary-light`: #93c5fd （浅蓝 — 辅助高亮）
- `--color-accent`: #14b8a6 （青绿 — 图表数据线B、辅助标识）

**背景层次（纯浅色）：**
- `--bg-base`: #f0f7ff （主背景 — 极淡蓝底色）
- `--bg-surface`: #ffffff （表面 — 卡片、面板、侧栏背景）
- `--bg-elevated`: #f8fafc （抬升面 — 下拉菜单、弹窗）
- `--bg-glass`: rgba(255,255,255,0.85) （毛玻璃效果）

**文字层次：**
- `--text-primary`: #1e293b （主文字 — 标题、重要内容）
- `--text-secondary`: #475569 （次要文字 — 正文描述）
- `--text-muted`: #94a3b8 （弱化文字 — 占位符、辅助信息）

**边框与分割：**
- `--border-subtle`: rgba(148,163,184,0.2) （细分割线）
- `--border-accent`: rgba(59,130,246,0.3) （强调边框）

**布局专用（基于设计稿精确值）：**
- `--sidebar-bg`: #ffffff （左侧导航栏纯白背景）
- `--sidebar-border`: #e2e8f0 （导航栏右边框）
- `--panel-bg`: #ffffff （右侧面板纯白背景）
- `--panel-text`: #334155 （面板文字颜色）
- `--nav-text-primary`: #1e293b （导航文字激活态）
- `--nav-text-secondary`: #64748b （导航文字默认态）
- `--input-area-bg`: rgba(255,255,255,0.92) （输入区半透明白底）

**图表专用：**
- `--chart-line-a`: #3b82f6 （数据线A — 品牌蓝）
- `--chart-line-b`: #14b8a6 （数据线B — 青绿）
- `--chart-grid`: #e2e8f0 （图表网格线）

#### Scenario: 所有组件使用统一 CSS 变量
- **WHEN** 组件引用 `var(--xxx)` 样式属性
- **THEN** 渲染结果与 UI 设计稿色彩一致，呈现统一的浅色蓝白风格

---

### Requirement: 主题 Store（保留结构）

系统 SHALL 提供 `useThemeStore` Pinia Store，当前仅支持单一浅色模式。Store 结构 SHALL 保留以便后续扩展：

- **isDark**: boolean，固定为 `false`
- **initTheme()**: 初始化方法（当前仅做基础设置）
- **toggleTheme()**: 方法保留但无实际切换逻辑

> **设计决策**：基于 UI 设计稿，全局采用浅色系配色方案，移除深色模式切换功能。

---

### Requirement: 组件样式统一

系统 SHALL 将各组件中的硬编码颜色值替换为 CSS 变量引用。**必须适配的组件及替换规则：**

1. **SideNav.vue**:
   - 背景 → `var(--sidebar-bg)` 纯白底
   - 文字 → `var(--nav-text-primary/secondary)` 深灰系
   - 边框 → `var(--border-subtle)`
   - 搜索框 → `var(--input-bg)` + `var(--border-subtle)`
   - Logo 文字 → `var(--text-primary)`
   - 导航 hover 背景 → `rgba(59,130,246,0.08)` 浅蓝
   - **不含主题切换按钮**

2. **RightPanel.vue**:
   - 背景 → `var(--panel-bg)` 纯白
   - 文字 → `var(--panel-text)` 深灰色
   - 展开按钮颜色 → `var(--text-muted)` / hover `var(--color-primary)`
   - 操作按钮 hover 背景 → `rgba(59,130,246,0.08)`

3. **ChatInput.vue**:
   - 背景 → `var(--input-area-bg)` 半透明白

4. **LineChart.vue**:
   - 图表配色固定为浅色值（白底 tooltip、深色轴文字）
   - 数据线 A → `#3b82f6`（品牌蓝），数据线 B → `#14b8a6`（青绿）
   - **不含 `themeStore.isDark` 条件分支**

5. **FileList.vue**:
   - 文件项背景 → `#f8fafc`
   - 文字颜色 → `var(--text-primary)`
   - 日期颜色 → `var(--text-muted)`

6. **FileSearch.vue**:
   - 输入框背景 → `var(--input-bg)`
   - 文字/占位符 → `var(--text-primary)` / `var(--text-muted)`

7. **DataPortrait.vue** / **ChatHeader.vue** / **ChatBubble.vue** / **WelcomeSection.vue**:
   - 所有硬编码颜色替换为对应 CSS 变量

#### Scenario: 全局视觉一致性
- **WHEN** 所有组件完成 CSS 变量替换后渲染
- **THEN** 整体界面与 UI 设计稿像素级对齐，无深色残留
