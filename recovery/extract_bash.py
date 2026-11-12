# -*- coding: utf-8 -*-
"""从 db.sqlite 重放今天对 NowPlayingScreen.kt 的 Bash python 写操作"""
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
    # 只保留含写操作的
    if "open(" not in cmd or "'w'" not in cmd:
        continue
    if 'recovery/' in cmd or 'nps_ops' in cmd or 'nps_bash' in cmd:
        continue
    bashes.append((ts, cmd))

print('bash writes:', len(bashes))
for i, (ts, cmd) in enumerate(bashes):
    # 提取 python 脚本中的所有 replace(old, new) 对
    print(f'--- bash {i} ts={ts} len={len(cmd)}')
    for m in re.finditer(r'''\.replace\(\s*(?:"""(.*?)"""|'(.*?)'),\s*(?:"""(.*?)"""|'(.*?)')(?:,\s*(\d))?\s*\)''', cmd, re.S):
        o = (m.group(1) if m.group(1) is not None else m.group(2) or '')
        n = (m.group(3) if m.group(3) is not None else m.group(4) or '')
        cnt = int(m.group(5)) if m.group(5) else 1
        o2 = o.replace('\r\n', '\n')
        n2 = n.replace('\r\n', '\n')
        print(f'  replace old={len(o2)} new={len(n2)} count={cnt} old_head={o2[:40]!r}')

# 保存解析结果供下一步用
import io
with io.open('recovery/bash_replaces.txt', 'w', encoding='utf-8') as f:
    for i, (ts, cmd) in enumerate(bashes):
        f.write(f'=== BASH {i} ts={ts} ===\n')
        f.write(cmd + '\n')
        f.write('\n')
print('saved bash_replaces.txt')
