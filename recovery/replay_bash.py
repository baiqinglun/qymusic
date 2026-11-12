# -*- coding: utf-8 -*-
"""通过 exec 重放 Bash python 写脚本"""
import json, re, sqlite3

con = sqlite3.connect('C:/Users/bql/.zcode/cli/db/db.sqlite')
cur = con.cursor()
cur.execute("""
SELECT p.time_created, p.data FROM part p
WHERE p.data LIKE '%NowPlayingScreen.kt%'
ORDER BY p.time_created, p.sequence
""")
bashes = []
for ts, data in cur.fetchall():
    try:
        d = json.loads(data)
    except Exception:
        continue
    if d.get('type') != 'tool' or d.get('tool') != 'Bash':
        continue
    cmd = (d.get('state') or {}).get('input', {}).get('command', '')
    if 'NowPlayingScreen.kt' not in cmd:
        continue
    if "open(" not in cmd or "'w'" not in cmd:
        continue
    if 'recovery/' in cmd or 'nps_ops' in cmd or 'nps_bash' in cmd:
        continue
    bashes.append((ts, cmd))
print('bash writes:', len(bashes))

TARGET = 'NowPlayingScreen.kt'

# 重放文件：当前损坏文件（含最新头部+弹层区）
p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
s = open('../' + p, encoding='utf-8', newline='').read()

applied = 0
skipped = 0

for ts, cmd in bashes:
    # 提取 heredoc python 代码
    m = re.search(r"python\s*-\s*<<'(\w+)'\n(.*?)\n\1", cmd, re.S)
    if not m:
        m = re.search(r'python\s*-\s*<<"(\w+)"\n(.*?)\n\1', cmd, re.S)
    if not m:
        continue
    code = m.group(2)
    # 跳过不含 NowPlayingScreen 路径写入的（只重放以 NowPlayingScreen.kt 为目标的）
    if TARGET not in code:
        continue
    # 重定向文件 IO 到内存
    # 替换 open(p, ...) / open('...NowPlayingScreen.kt', ...) 为全局 s_var
    # 简化：把整个 code 的文件读写剥离，只执行 replace/del/insert 等对 s 的操作
    # 更实际：把 code 里的 open(p,...).read() 和 write 调用替换为对 s 的操作
    code = code.replace("p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'",
                        "FN = 'NowPlayingScreen.kt'")
    code = code.replace("p = \"app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt\"",
                        "FN = 'NowPlayingScreen.kt'")
    # 移除所有文件 IO 行（open(...).read/write），让 exec 只做字符串操作
    # 这太脆弱。换方案：用受控 namespace 执行，拦截 open()
    try:
        # 使用受控执行
        loc = {'__fn_content__': s, 'FN': TARGET}
        # 把 code 中的 open() 调用替换为 io操作拦截
        # 简单做法：在 code 前面注入 s 变量和 open wrapper
        wrapper = (
            "import io\n"
            "class _MemFile(io.StringIO):\n"
            "    def __init__(self, content):\n"
            "        super().__init__(content)\n"
            "    def close(self):\n"
            "        pass\n"
            "_real_open = open\n"
            "def _open(file, mode='r', *a, **kw):\n"
            "    if 'w' in mode and 'NowPlayingScreen' in str(file):\n"
            "        return _MemFile(loc_s[0])\n"
            "    if 'r' in mode and 'NowPlayingScreen' in str(file):\n"
            "        return _MemFile(loc_s[0])\n"
            "    return _real_open(file, mode, *a, **kw)\n"
        )
        # 太复杂——改用简单 exec + 手动拦截
        pass
    except Exception:
        pass

print('(analysis only — will use manual approach)')
# 输出所有 bash 脚本到文件供人工分析
with open('recovery/all_bash_scripts.txt', 'w', encoding='utf-8') as f:
    for i, (ts, cmd) in enumerate(bashes):
        f.write(f'===== BASH OP {i} ts={ts} =====\n')
        f.write(cmd + '\n\n')
print('saved to recovery/all_bash_scripts.txt')
