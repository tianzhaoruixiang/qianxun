## ADDED Requirements

### Requirement: 全局动画样式文件

系统 SHALL 创建 `src/styles/animations.css` 文件，集中定义所有全局 `@keyframes` 动画。该文件 SHALL 在 `main.ts` 中被导入。

文件 SHALL 定义以下关键帧动画：

1. **fade-in-up**: opacity 0→1, translateY(16px)→0, duration 0.4s, ease-out
2. **avatar-float**: translateY(0)↔translateY(-5px), duration 3s, ease-in-out, infinite
3. **slide-in-right**: translateX(100%)→translateX(0), duration 0.3s, cubic-bezier(0.4,0,0.2,1)
4. **slide-in-left**: translateX(-100%)→translateX(0), duration 0.3s, cubic-bezier(0.4,0,0.2,1)
5. **scale-in**: opacity 0→1, scale(0.95)→1, duration 0.25s, ease-out
6. **pulse-glow**: box-shadow 从基础值到发光值循环, duration 2s, ease-in-out, infinite
7. **typing-bounce**: 用于打字指示器的弹跳动画
8. **shimmer**: 用于加载状态的闪光效果
9. **float-subtle**: 微妙的悬浮漂移动画

此外，SHALL 定义 Vue Transition 的 enter/leave 过渡类名：
- `.fade-enter/leave-*`
- `.slide-right-enter/leave-*`
- `.scale-enter/leave-*`

以及 `@media (prefers-reduced-motion: reduce)` 规则，禁用所有装饰性 infinite 动画。

#### Scenario: animations.css 被导入后全局可用
- **WHEN** `main.ts` 导入 `animations.css`
- **THEN** 所有组件均可通过 `animation: <name>` 引用上述关键帧

#### Scenario: 减少动画偏好下装饰性动画被禁用
- **WHEN** 用户操作系统设置了"减少动画"偏好
- **THEN** avatar-float 和 pulse-glow 等 infinite 动画停止执行，fade/slide 等一次性动画保留但移除过渡效果

---

### Requirement: Vue Transition 工具函数

系统 SHALL 提供 `src/composables/useAnimation.ts`，导出 Vue `<Transition>` 组件的预设配置对象：

- **fadeTransition**: 用于通用淡入淡出（opacity 过渡）
- **slideRightTransition**: 用于右侧面板滑入/滑出（translateX + opacity）
- **slideLeftTransition**: 用于侧边栏滑入/滑出
- **scaleTransition**: 用于弹窗/模态框缩放淡入

每个预设 SHALL 包含 Vue Transition 的 `css: true` 模式所需的 enter/leave 钩子类名配置。

#### Scenario: RightPanel 使用 slideRightTransition
- **WHEN** RightPanel 组件包裹 `<Transition>` 并绑定 slideRightTransition
- **THEN** 面板展开时从右侧滑入，收起时向右侧滑出，时长 0.3s
- **AND** 折叠栏（collapsed-bar）始终可见不受 Transition 影响

#### Scenario: SideNav 使用 slideLeftTransition
- **WHEN** SideNav 组件包裹 `<Transition>` 并绑定 slideLeftTransition
- **THEN** 导航栏折叠/展开时有滑动过渡效果

---

### Requirement: 推荐问题卡片交错入场

WelcomeSection 组件中的推荐问题卡片网格 SHALL 实现交错入场动画：

- 第一张卡片立即开始动画
- 后续每张卡片延迟 80ms（stagger delay）
- 每张卡片使用 fade-in-up 效果
- 总动画时长控制在 600ms 以内

#### Scenario: 推荐问题卡片依次入场
- **WHEN** WelcomeSection 渲染且推荐问题列表非空
- **THEN** 卡片按 index * 80ms 延迟依次淡入上滑，形成波浪式入场效果

---

### Requirement: 组件内联动画迁移

系统 SHALL 将分散在各组件中的内联 `@keyframes` 定义迁移到 `animations.css`，组件中仅保留 `animation` 属性引用：

- **WelcomeSection.vue**: 移除 `fade-in-up` 和 `avatar-float` keyframes 定义
- **ChatHeader.vue**: 移除 `avatar-float` keyframes 定义
- **ChatBubble.vue**: 移除 `bubble-in` 和 `typing-bounce` keyframes 定义

#### Scenario: 迁移后组件动画效果不变
- **WHEN** 内联 keyframes 被删除，组件改用全局 animations.css 中的同名 keyframes
- **THEN** 所有视觉效果（头像悬浮、气泡进入、打字指示器）与迁移前完全一致
