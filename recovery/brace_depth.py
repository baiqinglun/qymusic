# -*- coding: utf-8 -*-
# 统计剥掉注释/字符串后的花括号深度，列出 depth 回到 0 的行号
import io, sys

path = sys.argv[1] if len(sys.argv) > 1 else 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
src = io.open(path, encoding='utf-8').read()

def strip(src):
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == '/' and i + 1 < n and src[i+1] == '/':
            j = src.find('\n', i)
            i = n if j == -1 else j
            continue
        if c == '/' and i + 1 < n and src[i+1] == '*':
            j = src.find('*/', i + 2)
            i = n if j == -1 else j + 2
            continue
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                while j != -1 and (j - i) % 3 == 0 and src[j:j+3] == '"""':
                    break
                i = n if j == -1 else j + 3
                continue
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == '"':
                    break
                j += 1
            i = j + 1
            continue
        if c == "'":
            j = i + 1
            while j < n:
                if src[j] == '\\':
                    j += 2
                    continue
                if src[j] == "'":
                    break
                j += 1
            i = j + 1
            continue
        out.append(c)
        i += 1
    return ''.join(out)

clean = strip(src)
depth = 0
line = 1
depth_at_line_start = 0
zeros = []
neg = None
for ch in clean:
    if ch == '\n':
        line += 1
        depth_at_line_start = depth
    elif ch == '{':
        depth += 1
    elif ch == '}':
        depth -= 1
        if depth == 0:
            zeros.append(line)
        if depth < 0 and neg is None:
            neg = line
print('final depth:', depth, ' first negative:', neg)
print('depth-0 lines (top-level boundaries):', zeros[:40])
