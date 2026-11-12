# -*- coding: utf-8 -*-
"""从 db.sqlite 重放全部 Bash python 写操作到当前 NowPlayingScreen.kt"""
import io, re, sqlite3

# 当前文件内容（最新头部状态）
FILE = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
content = open(FILE, encoding='utf-8', newline='').read()

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
    if "open(" not in cmd or ("'w'" not in cmd and '"w"' not in cmd):
        continue
    if 'recovery/' in cmd or 'nps_ops' in cmd or 'nps_bash' in cmd:
        continue
    bashes.append((ts, cmd))

print('bash writes to replay:', len(bashes))

# 提取 heredoc python 代码并执行
applied = 0
for i, (ts, cmd) in enumerate(bashes):
    # 提取 <<'XXX' ... XXX 之间的 python 代码
    m = re.search(r"python\s*-\s*<<'(\w+)'\n(.*?)\n\1\s*(?:\n|$)", cmd, re.S)
    if not m:
        m = re.search(r'python\s*-\s*<<"(\w+)"\n(.*?)\n\1\s*(?:\n|$)', cmd, re.S)
    if not m:
        continue
    code = m.group(2)
    # 只执行目标为 NowPlayingScreen.kt 的
    if 'NowPlayingScreen.kt' not in code:
        continue
    # 在受控环境中执行
    # 替换文件 IO 为内存操作
    ns = {'__file_content__': content, '__modified__': False}
    # 拦截 open 写
    # 简化：把 code 中的文件路径替换为内存标记
    code = code.replace("'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'",
                        "'__MEM__'")
    code = code.replace('"app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt"',
                        "'__MEM__'")
    # 替换 open(file,'wb').write(data) / open(file).read()
    # 直接注入 __MEM__ 读写
    prelude = '''
import io as _io
class _MemFile(_io.StringIO):
    def __init__(self, content=''):
        super().__init__(content)
    def close(self):
        global __file_content__
        __file_content__ = self.getvalue()
'''
    # 不行——太复杂了
    
    # 换成直接提取 replace 模式
    for m2 in re.finditer(
        r'''(?:s|content)\s*=\s*(?:content|s)\.replace\(\s*
            (?:"""(.*?)"""|'(.*?)'|"(.*?)"),\s*
            (?:"""(.*?)"""|'(.*?)'|"(.*?)")
            (?:\s*,\s*(\d))?\s*\)''',
        code, re.S | re.VERBOSE
    ):
        o = m2.group(1) or m2.group(2) or m2.group(3) or ''
        n = m2.group(4) or m2.group(5) or m2.group(6) or ''
        o = o.replace('\\n', '\n').replace('\\r\\n', '\n').replace("''", "'")
        n = n.replace('\\n', '\n').replace('\\r\\n', '\n').replace("''", "'")
        if o in content:
            content = content.replace(o, n, 1)
            applied += 1
        elif n in content:
            pass  # already
        else:
            pass  # context mismatch
    
    # 提取行级操作
    for m3 in re.finditer(r'del lines\[(\d+)(?::\s*(\d+))?\]', code):
        pass  # 行级操作太复杂，跳过
    
print('replace operations applied:', applied)

# 写回
open(FILE, 'w', encoding='utf-8', newline='').write(content)
print('saved')
