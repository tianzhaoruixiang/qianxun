---
name: email_search 邮件数据检索
description: 从邮件主题数据中检索关联的邮件内容信息
---

# 工作流程

1. 将用户问题转换为组合关键词查询条件
2. 根据组合关键词，查询邮件内容数据（兼容的ES查询接口）
   curl -X POST "http://app-container-gateway:11010/QianXunService/aitools/queryElasticsearch" -H "Content-Type: application/json" -d '{"index":"emailcenter_vector_index*","query":{"match_phrase":{"content":"特朗普"}},"_source":{"excludes":["content_vector"]},"from":0,"size":1000}'
3. 如果命中的总数比较多，进行翻页查询最多1万条数据
4. 根据查询到的数据进行结果分析，给出用户问题的答案
5. 输出内容给出依据或结论的引用， 展示原始 file_id 和