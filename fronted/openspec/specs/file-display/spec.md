## Capability: 文件展示 (file-display)

### Requirement: 文件列表展示
系统 SHALL 在右侧面板中间数据区域以两列网格布局展示文件列表，每个文件项 SHALL 显示文件类型图标、文件名（单行省略）和创建日期。

#### Scenario: 默认加载文件列表
- **WHEN** 用户展开右侧面板查看中间数据区域
- **THEN** 系统显示两列网格排列的文件卡片列表，每个卡片包含文件类型图标（Word蓝色/Excel绿色）、文件名和日期

#### Scenario: 区分文件类型图标
- **WHEN** 文件列表中包含 .docx 和 .xlsx/.xls 类型文件
- **THEN** .docx 文件显示蓝色 Word 图标，.xlsx/.xls 文件显示绿色 Excel 图标

#### Scenario: 文件名超长截断
- **WHEN** 文件名超过容器宽度
- **THEN** 文件名以省略号(...)截断显示，悬停时显示完整文件名

---

### Requirement: 文件搜索过滤
系统 SHALL 提供搜索输入框（占位符"请输入关键词搜索"），支持按文件名关键词实时过滤文件列表。

#### Scenario: 关键词搜索过滤
- **WHEN** 用户在搜索框输入关键词（如"销售"）
- **THEN** 文件列表即时刷新，仅显示文件名包含该关键词的文件项

#### Scenario: 清空搜索恢复全量
- **WHEN** 用户清空搜索框内容
- **THEN** 文件列表恢复显示全部文件

#### Scenario: 无匹配结果提示
- **WHEN** 搜索关键词无匹配文件
- **THEN** 文件列表区域显示"未找到匹配文件"空态提示

---

### Requirement: 统计信息展示
系统 SHALL 在文件列表底部显示统计信息栏，包含目录数量和文件数量。

#### Scenario: 显示动态统计信息
- **WHEN** 文件列表加载完成或搜索过滤后
- **THEN** 底部统计栏显示"目录: X/Y 文件: M/N"格式的统计信息，其中 M 为当前可见文件数，N 为总文件数

---

### Requirement: 文件数据状态管理
系统 SHALL 通过 Pinia Store 管理文件数据，提供 Mock 数据集和计算属性（过滤后列表、统计数据）。

#### Scenario: Store 提供 Mock 数据
- **WHEN** 组件初始化访问 useFileStore
- **THEN** Store 返回预置的 Mock 文件列表（至少 12 条，含 docx 和 xlsx/xls 类型）

#### Scenario: 计算属性自动更新
- **WHEN** searchKeyword 变化
- **THEN** filteredFiles computed 自动重新计算返回过滤后的文件列表

#### Scenario: 统计数据派生
- **WHEN** 文件列表或过滤条件变化
- **THEN** fileStats computed 返回 { totalFiles, visibleDirs, visibleFiles } 统计对象

---

### Requirement: 右侧面板组件集成
系统 SHALL 将 FileList、FileSearch、FileStats 三个组件集成到 RightPanel 中间数据区域，替换原有硬编码占位内容。

#### Scenario: 替换硬编码占位
- **WHEN** 右侧面板展开且中间数据区域渲染
- **THEN** 区域内显示 FileSearch + FileList + FileStats 组件组合，不再显示原有 v-for 硬编码内容

#### Scenario: 保持面板样式一致性
- **WHEN** 新组件集成到 RightPanel
- **THEN** 文件区域的视觉样式（间距、圆角、背景、字体大小）与 UI.png 设计稿保持一致
