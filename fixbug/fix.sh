docker cp ./store.js qianxun-claude-code:/app/src/store.js
docker exec qianxun-claude-code grep -n "e.isSymbolicLink" /app/src/store.js
docker exec qianxun-claude-code supervisorctl restart qianxun-claude
docker commit qianxun-claude-code qianxun/claude-code:amd64-dev
