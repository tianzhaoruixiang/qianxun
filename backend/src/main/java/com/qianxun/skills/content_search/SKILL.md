---
name: content_search 内容检索
description: 从邮件或语音数据中检索用户问题相关的数据
---

# 工作流程

1. 将用户问题转换为组合关键词查询条件
2. 根据组合关键词，查询内网数据
curl -X POST "http://app-container-gateway:11010/QianXunService/aitools/executeDorisSql" \
-H "Content-Type: application/json" \
-d '{"sql": "SELECT * FROM information_analysis.data_lake_file LIMIT 10"}'curl -X POST "http://95.101.20.202:11010/QianXunService/aitools/executeDorisSql" \
-H "Content-Type: application/json" \
-d '{"sql": "SELECT * FROM information_analysis.data_lake_file LIMIT 10"}'


3. 根据组合关键词，查询语音内容数据（兼容的ES查询接口）
   curl -X POST "http://app-container-gateway:11010/QianXunService/aitools/queryElasticsearch" -H "Content-Type: application/json" -d '{"index":"voice_vector_index*","query":{"match_phrase":{"content":"特朗普"}},"_source":{"excludes":["content_vector"]},"from":0,"size":1000}'

4. 如果命中的总数比较多，进行翻页查询最多 10000条数据
5. 根据查询到的数据进行结果分析，给出用户问题的答案
6. 输出内容给出依据或结论的引用， 展示原始 file_id 和内容 content 的摘要信息
