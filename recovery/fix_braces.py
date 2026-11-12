# -*- coding: utf-8 -*-
# 在 damaged_head 基底上修复 2 处括号（PlaybackBottomSheet 缺闭合 + 文件尾多一个）
import io

p = 'app/src/main/java/com/qymusic/player/ui/NowPlayingScreen.kt'
s = io.open(p, encoding='utf-8').read()

# 1. PlaybackBottomSheet 函数末尾补闭合（Popup 内容 lambda 结束后函数缺一个 }）
old = """                        CompositionLocalProvider(
                            LocalSheetClose provides {
                                close()
                            },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetDragHandle() {"""
new = """                        CompositionLocalProvider(
                            LocalSheetClose provides {
                                close()
                            },
                        ) {
                            content()
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SheetDragHandle() {"""
assert s.count(old) == 1, s.count(old)
s = s.replace(old, new, 1)

# 2. 文件尾删多余的一个 }
trimmed = s.rstrip()
assert trimmed.endswith('}')
trimmed = trimmed[:-1].rstrip() + '\n'
io.open(p, 'w', encoding='utf-8', newline='').write(trimmed)
print('ok')
