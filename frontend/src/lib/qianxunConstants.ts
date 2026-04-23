/**
 * 前端统一命名前缀：localStorage、根 DOM id、与模型约定围栏等。
 */
export const QIANXUN_PREFIX = "qianxun";

/** 与后端一致的实体围栏语言标记：`qianxun-entities` */
export const QIANXUN_ENTITIES_FENCE = `${QIANXUN_PREFIX}-entities`;

/** localStorage 等：`qianxun_user_id` */
export function qianxunStorageKey(suffix: string): string {
  return `${QIANXUN_PREFIX}_${suffix}`;
}

/** HTML id / 可访问性：`qianxun-root` */
export function qianxunDomId(suffix: string): string {
  return `${QIANXUN_PREFIX}-${suffix}`;
}

export const QIANXUN_ROOT_ELEMENT_ID = qianxunDomId("root");

/** 根布局 CSS 类名，便于主题 / 测试选择器 */
export const QIANXUN_APP_CLASSNAME = qianxunDomId("app");
