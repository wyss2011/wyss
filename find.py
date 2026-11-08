"""
REPL 模式文件内容搜索工具
支持正则或纯文本搜索，默认搜索当前目录
"""
import os
import re
import sys

# 硬编码排除目录
EXCLUDE_DIRS = {
    '.git', '.vs', 'node_modules', '__pycache__', '.idea',
    'miniprogram_npm', '_backup_pre_redesign',
}

# 排除的文件扩展名
EXCLUDE_EXTS = {
    '.png', '.jpg', '.jpeg', '.gif', '.svg', '.ico', '.bmp',
    '.mp3', '.mp4', '.wav', '.avi',
    '.zip', '.rar', '.7z', '.tar', '.gz',
    '.exe', '.dll', '.so', '.dylib',
    '.pyc', '.pyo', '.class', '.o', '.obj',
    '.db', '.sqlite', '.vsidx',
}

# 默认搜索根目录
DEFAULT_ROOT = os.path.dirname(os.path.abspath(__file__))


def should_skip(dirpath):
    """判断目录是否应跳过"""
    parts = dirpath.replace('\\', '/').split('/')
    return any(p in EXCLUDE_DIRS for p in parts)


def search(pattern, root=DEFAULT_ROOT, use_regex=True, max_results=50):
    """搜索文件内容"""
    if use_regex:
        try:
            compiled = re.compile(pattern, re.IGNORECASE)
        except re.error as e:
            print(f'  正则语法错误: {e}')
            return

    count = 0
    for dirpath, dirnames, filenames in os.walk(root):
        # 就地修改 dirnames 以跳过排除目录
        dirnames[:] = [d for d in dirnames if d not in EXCLUDE_DIRS]

        for fname in filenames:
            ext = os.path.splitext(fname)[1].lower()
            if ext in EXCLUDE_EXTS:
                continue

            fpath = os.path.join(dirpath, fname)
            relpath = os.path.relpath(fpath, root)
            try:
                with open(fpath, 'r', encoding='utf-8', errors='ignore') as f:
                    for lineno, line in enumerate(f, 1):
                        line_stripped = line.rstrip('\n\r')
                        if use_regex:
                            match = compiled.search(line_stripped)
                            if match:
                                highlight = line_stripped[:match.start()] + \
                                    f'>>> {match.group()} <<<' + \
                                    line_stripped[match.end():]
                                print(f'  {relpath}:{lineno}  {highlight}')
                                count += 1
                        else:
                            if pattern.lower() in line_stripped.lower():
                                print(f'  {relpath}:{lineno}  {line_stripped}')
                                count += 1

                        if count >= max_results:
                            print(f'\n  ... 已达上限 {max_results} 条，停止')
                            return
            except (PermissionError, OSError):
                continue

    if count == 0:
        print('  未找到匹配内容')
    else:
        print(f'\n  共 {count} 条匹配')


def main():
    print('=== 文件内容搜索工具 ===')
    print(f'搜索根目录: {DEFAULT_ROOT}')
    print(f'排除目录: {EXCLUDE_DIRS}')
    print()
    print('命令:')
    print('  /regex <pattern>   正则搜索')
    print('  /text <text>       纯文本搜索')
    print('  /max <n>           设置结果上限(默认50)')
    print('  /dir <path>        切换搜索目录')
    print('  /help              显示帮助')
    print('  /quit              退出')
    print()
    print('直接输入内容默认使用【纯文本】搜索，需要正则请加 /regex 前缀')
    print()

    root = DEFAULT_ROOT
    use_regex = False  # 修改1：默认纯文本
    max_results = 50

    while True:
        try:
            raw = input('find> ').strip()
        except (EOFError, KeyboardInterrupt):
            print('\n再见')
            break

        if not raw:
            continue

        if raw == '/quit':
            print('再见')
            break

        if raw == '/help':
            print('  /regex <pattern>   正则搜索')
            print('  /text <text>       纯文本搜索')
            print('  /max <n>           设置结果上限')
            print('  /dir <path>        切换搜索目录')
            print('  /help              显示帮助')
            print('  /quit              退出')
            print('  直接输入内容默认纯文本搜索')
            continue

        if raw.startswith('/regex '):
            pattern = raw[7:].strip()
            if pattern:
                search(pattern, root, use_regex=True, max_results=max_results)
            continue

        if raw.startswith('/text '):
            pattern = raw[6:].strip()
            if pattern:
                search(pattern, root, use_regex=False, max_results=max_results)
            continue

        if raw.startswith('/max '):
            try:
                max_results = int(raw[5:].strip())
                print(f'  结果上限设为 {max_results}')
            except ValueError:
                print('  请输入数字')
            continue

        if raw.startswith('/dir '):
            new_dir = raw[5:].strip()
            if os.path.isdir(new_dir):
                root = os.path.abspath(new_dir)
                print(f'  搜索目录切换为: {root}')
            else:
                print(f'  目录不存在: {new_dir}')
            continue

        # 修改2：直接输入走纯文本搜索
        search(raw, root, use_regex=False, max_results=max_results)


if __name__ == '__main__':
    main()