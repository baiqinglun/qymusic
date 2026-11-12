# -*- coding: utf-8 -*-
"""从 HEAD 基线重放全部 Edit + Bash 写操作恢复 NowPlayingScreen.kt"""
import json, re, sys

HEAD = open('recovery/head_version.kt', encoding='utf-8', newline='').read()
ops = json.load(open('recovery/nps_ops_all.json', encoding='utf-8'))
bashes = json.load(open('recovery/nps_bash_all.json', encoding='utf-8'))

# 合并按时间排序（Edit/Bash 都是 (ts, sid, kind, ...)）
all_ops = []
for ts, sid, kind, old, new in ops:
    all_ops.append((ts, sid, kind, old, new))
for ts, sid, cmd in bashes:
    all_ops.append((ts, sid, 'Bash', cmd, ''))
all_ops.sort(key=lambda x: x[0])

s = HEAD.replace('\r\n', '\n')
fail_edits = []
applied_edit = 0
applied_bash = 0

def do_replace(old, new, label):
    global s
    o = old.replace('\r\n', '\n')
    n = new.replace('\r\n', '\n')
    if o in s:
        s = s.replace(o, n, 1)
        return 'applied'
    if n in s:
        return 'already'
    return 'MISSED: ' + label

for ts, sid, kind, a, b in all_ops:
    if kind == 'Edit':
        r = do_replace(a, b, 'Edit')
        if r.startswith('MISSED'):
            fail_edits.append(('Edit', ts, r))
        else:
            applied_edit += 1
    elif kind == 'Bash':
        # 重放 python 写脚本：只重放其中的 replace/write 逻辑
        cmd = a
        # 提取 heredoc python 的 replace 调用（old/new 字符串对）
        # 简化：找到脚本里的 s.replace(old, new) 或 replace('''old''','''new''') 模式
        for m in re.finditer(r'''\.replace\((?:"""(.*?)"""|'(.*?)'),\s*(?:"""(.*?)"""|'(.*?)')''', cmd, re.S):
            o = (m.group(1) or m.group(2) or '').replace('\r\n', '\n')
            n = (m.group(3) or m.group(4) or '').replace('\r\n', '\n')
            if o in s:
                s = s.replace(o, n, 1)
                applied_bash += 1
        # 提取 del lines[X] 和 insert 操作太复杂——跳过
        # 提取直接整文件写入（Write 模式已在上面）
print('Edit applied:', applied_edit, 'Bash replaces:', applied_bash, 'Edit missed:', len(fail_edits))
for f in fail_edits[:10]:
    print('  MISS', f[1], f[2][:60])

open('recovery/replayed.kt', 'w', encoding='utf-8', newline='').write(s)
print('output lines:', s.count('\n') + 1)
d = 0
for l in s.split('\n'):
    d += l.count('{') - l.count('}')
print('final depth:', d)
