# 基于官方镜像替换入口：去掉 Neo4j，支持 LiteLLM OpenAI Compatible。
FROM mem0/mem0-api-server:latest

RUN pip install --no-cache-dir "psycopg[binary,pool]"

COPY main.py /app/main.py

# 官方镜像默认 --reload，本地 compose 同样可用
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
