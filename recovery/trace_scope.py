# -*- coding: utf-8 -*-
# Trace the enclosing brace-stack of every "if (show" line.
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

# map cleaned char index -> original line number
lines = []
ln = 1
for ch in clean:
    lines.append(ln)
    if ch == '\n':
        ln += 1

stack = []  # list of (char_idx, line_no)
targets = []
for i, ch in enumerate(clean):
    if ch == '{':
        stack.append(i)
    elif ch == '}':
        if stack:
            stack.pop()
    if ch == '\n':
        continue
    # detect "if (show" at this position
    if clean.startswith('if (show', i):
        ctx = [(lines[j], clean[j+1:clean.find('\n', j)].strip()[:60]) for j in stack[-4:]]
        targets.append((lines[i], ctx))

for t_ln, ctx in targets:
    print('line %d: if(show...)' % t_ln)
    for c_ln, c_txt in ctx:
        print('    in { at line %d: %s' % (c_ln, c_txt))
