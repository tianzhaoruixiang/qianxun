# 数据画像规格 (Data Portrait)

> 来源: Change `build-data-visualization` | 归档日期: 2026-04-30

## Requirements

### Requirement: ECharts 通用包装组件

系统 SHALL 提供 `ChartWrapper.vue` 组件作为 ECharts 实例的统一生命周期管理者。该组件 SHALL 使用 SVG 渲染模式 (`renderer: 'svg'`)，并在组件卸载时调用 `charts.dispose()` 销毁实例。

#### Scenario: 正常初始化与渲染
- **WHEN** `ChartWrapper` 挂载且接收到有效的 ECharts option prop
- **THEN** 组件在内部 DOM 容器中初始化 ECharts 实例并渲染图表

#### Scenario: Option 更新时重新渲染
- **WHEN** 父组件传入新的 option prop
- **THEN** ECharts 实例调用 `setOption` 更新图表配置

#### Scenario: 组件销毁时释放资源
- **WHEN** `ChartWrapper` 组件被卸载
- **THEN** ECharts 实例调用 `dispose()` 释放内存，避免内存泄漏

#### Scenario: 窗口大小变化自适应
- **WHEN** 浏览器窗口尺寸发生变化
- **THEN** ECharts 实例调用 `resize()` 重新计算尺寸

---

### Requirement: 双系列折线图组件

系统 SHALL 提供 `LineChart.vue` 组件，渲染双系列（数据A / 数据B）对比折线图。图表 SHALL 包含以下元素：

- **X 轴**: 月份标签（1月 至 6月）
- **Y 轴**: 数值刻度，自动适应数据范围
- **数据A 系列**: 使用主色调 `#0ea5e9`（科技蓝）
- **数据B 系列**: 使用强调色 `#14b8a6`（青绿）
- **Tooltip**: 悬停显示格式化数据标注（日期 + 数据A值 + 数据B值）
- **平滑曲线**: 使用 smooth 平滑过渡

#### Scenario: 渲染双系列折线图
- **WHEN** `LineChart` 接收到包含 dataA 和 dataB 数组的 trend 数据
- **THEN** 图表展示两条不同颜色的平滑折线，X 轴显示 1-6 月

#### Scenario: 悬停显示数据标注
- **WHEN** 用户鼠标悬停在图表数据点上
- **THEN** 显示 tooltip 浮层，内容格式为 "YYYY.MM.DD 数据A: xx 数据B: xx"

#### Scenario: 无数据时的展示
- **WHEN** 传入的数据数组为空或未定义
- **THEN** 图表区域显示空状态提示

---

### Requirement: 数据画像面板组件

系统 SHALL 提供 `DataPortrait.vue` 作为数据画像区域的容器组件。该组件 SHALL 整合以下子元素：

- **标题栏**: "数据画像" 文字 + 设置图标按钮（`SettingOutlined`）
- **单位标识**: 右上角显示 "单位：个"，字号 12px，次要颜色
- **折线图**: 内嵌 `LineChart.vue` 组件，填充剩余空间
- **图例**: 底部右侧显示两个图例项（蓝色圆点+"数据A"、青绿圆点+"数据B"）

#### Scenario: 完整面板渲染
- **WHEN** `DataPortrait` 组件挂载到右侧面板
- **THEN** 面板按从上到下顺序展示标题栏 → 单位标识 → 折线图 → 图例

#### Scenario: 单位标识正确显示
- **WHEN** 面板正常渲染
- **THEN** 单位标识 "单位：个" 显示于图表区域右上角，字号 12px，颜色 rgba(255,255,255,0.6)

#### Scenario: 图例正确区分两个数据系列
- **WHEN** 面板正常渲染
- **THEN** 底部显示两个图例项：蓝色圆点 + "数据A"、青绿圆点 + "数据B"

---

### Requirement: Dashboard 状态管理

系统 SHALL 提供 `useDashboardStore` Pinia Store 管理数据画像相关的状态和数据。Store SHALL 包含：

- **trendData**: 趋势数据（含月份、数据A、数据B 的 Mock 数据集）
- **unit**: 数据单位字符串（默认值为 "个"）
- **annotation**: 关键节点标注信息（日期 + 各系列数值）

Store SHALL 在初始化时加载 Mock 趋势数据，并暴露 computed 属性供组件消费。

#### Scenario: Store 提供趋势数据
- **WHEN** 组件访问 `dashboardStore.trendData`
- **THEN** 返回包含 6 个月份数据的双系列数据对象

#### Scenario: Store 提供单位信息
- **WHEN** 组件访问 `dashboardStore.unit`
- **THEN** 返回字符串 "个"

#### Scenario: Store 提供标注信息
- **WHEN** 组件访问 `dashboardStore.annotation`
- **THEN** 返回包含日期和数据A/B数值的标注对象

---

### Requirement: RightPanel 数据画像集成

系统 SHALL 修改 `RightPanel.vue`，将当前数据画像区域的占位符替换为实际的 `DataPortrait` 组件。修改 SHALL：

- 移除 `.chart-placeholder` 占位符 DOM 及其内部静态内容
- 移除硬编码的 `.legend-bar` 图例 DOM（由 DataPortrait 内部管理）
- 引入并挂载 `DataPortrait` 组件
- 保留 `.panel-section` 容器和 `.section-header` 标题栏结构

#### Scenario: 占位符替换为实际组件
- **WHEN** 右侧面板展开且数据画像区域可见
- **THEN** 显示完整的 DataPortrait 组件（含实际 ECharts 折线图），不再显示占位符文字

#### Scenario: 保持中间数据区域不变
- **WHEN** RightPanel 重构完成
- **THEN** 中间数据面板（FileSearch + FileList + FileStats）的功能和外观不受影响
