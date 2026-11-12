# -*- coding: utf-8 -*-
# 把 PlaybackPage 顶层所有弹窗 if 块搬到 MIN2 探针之后（坏死区修复）
import io

p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
s = io.open(p, encoding='utf-8').read()
lines = s.split('\n')

anchors = [
    '    if (showPlaylist) {',
    '    if (showAddToPlaylist) {',
    '    if (showCreatePlaylist) {',
    '    if (showEqualizer) {',
    '    if (showSleepTimerSheet) {',
    '    if (showLyricSettingsSheet) {',
    '    if (showBackgroundSettingsSheet) {',
    '    if (showClipPicker) {',
    '    if (showCommentSheet) {',
    '    if (showCoverStyleSheet) {',
    '    if (showMagicSheet) {',
]

# 找到各块的行区间 [start, end]（含闭合行）
ranges = []
for a in anchors:
    hits = [i for i, l in enumerate(lines) if l == a]
    assert len(hits) == 1, (a, len(hits))
    idx = hits[0]
    depth = 0
    started = False
    end = None
    for i in range(idx, len(lines)):
        depth += lines[i].count('{') - lines[i].count('}')
        if depth > 0 and not started:
            started = True
        if started and depth == 0:
            end = i
            break
    ranges.append((idx, end))

# 提取块文本（按源顺序），并从原位置删除
blocks = []
keep = [True] * len(lines)
for (a, b) in ranges:
    blocks.append('\n'.join(lines[a:b + 1]))
    for i in range(a, b + 1):
        keep[i] = False

# 粘贴点：MIN2 探针块的结尾（"MIN2 here" 的 Text 闭合行之后）
min2_line = next(i for i, l in enumerate(lines) if '"MIN2 here"' in l)
# Text( ... ) 结构：min2_line 是 text= 行，找它后面第一个 ") " 行
paste_after = None
for i in range(min2_line, min2_line + 8):
    if lines[i].strip() == ')':
        paste_after = i
        break
assert paste_after is not None, 'MIN2 text close paren not found'

out = []
for i, l in enumerate(lines):
    if i == paste_after:
        out.append(l)
        for b in blocks:
            out.append(b)
            out.append('')
    elif keep[i]:
        out.append(l)

io.open(p, 'w', encoding='utf-8', newline='').write('\n'.join(out))
print('moved blocks:', len(blocks))
