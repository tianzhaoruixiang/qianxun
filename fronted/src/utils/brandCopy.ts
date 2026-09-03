/** 数智干警：面向用户的品牌与界面文案（集中维护，避免各处口径不一） */
export const DEFAULT_BRAND_NAME = '数智干警'
/** @deprecated 使用 bootstrap.systemName；此处仅作未加载时的回退 */
export const BRAND_NAME = DEFAULT_BRAND_NAME

export const brandCopy = {
  /** 登录页眉标 */
  loginTag: '千寻 · 智能协作',
  /** 登录页主副标题 */
  loginLine: '说清目标，我来规划与调度。',
  loginCta: '登录',

  /** 对话欢迎 */
  welcomeGreeting: `你好，我是${DEFAULT_BRAND_NAME}`,
  welcomeCapability: '把模糊需求理清，拆成可执行任务，再交给合适的专业智能体去完成。',
  welcomeRecommend: '试试这样开始：',
  welcomeDisclaimer: '内容由 AI 生成，请结合业务判断后使用。',
  /** 已选业务智能体时的中性能力描述 */
  welcomeCapabilityNeutral: '理解你的问题，进行多轮对话，帮你高效获取信息与结论。',

  /** 工作台首页 */
  spaceGreetingFallback: '今天想推进哪件事？',
  spaceQuickChatTitle: '开始对话',
  spaceQuickChatDesc: '提出目标，我来规划与跟进',
  spaceQuickMarketTitle: '专业智能体',
  spaceQuickMarketDesc: '浏览并选用专业能力',
  spaceRecentMore: '全部会话',
  spaceEmpty: '还没有会话。点击「新建对话」开始吧。',

  /** 市场 */
  marketAgentsSub: '管理专业智能体，在对话中随时切换。',
  marketSkillsSub: '技能是可复用的工作说明；关闭后不会进入系统提示。工具开关请到「工具」页。',
  marketToolsSub: '为当前专业智能体开关工具。关闭后，下一轮对话不再注入对应工具。',
  marketPluginsSub: '为当前专业智能体增减插件。关闭后，下次对话不再加载该插件。',
  marketMcpSub: '为当前专业智能体配置 MCP Server。已启用的服务会在下次对话时加载。',

  /**
   * 首 token 到达前的阶段性加载提示（助手气泡已出现、尚无正文/工具卡片）。
   * 语气：稳重、可执行；不要写成正式答复。
   */
  pendingReplyStages: [
    '正在理解用户意图…',
    '正在拆解任务…',
    '正在核对可用能力…',
    '正在调度执行…',
    '正在组织答复要点…',
  ],

  /** 输入框 */
  inputPlaceholder: '直接提问，输入 @ 指定智能体，或输入 / 使用技能、计划与目标',

  /** 斜杠菜单 */
  slashOfficerCurrent: '当前默认智能体',
  slashOfficerSwitch: '切换为默认智能体',
} as const
