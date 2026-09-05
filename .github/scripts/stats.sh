#!/usr/bin/env bash
# Inf's Farlands 源码统计——输出 5 个 badge 计数（key=value 到 stdout）。
#
# 统计范围：src/main/java + src/client/java（排除根目录工具类）。
# 规则：
#   @Overwrite / @Inject / @Redirect —— 行首注解（^\s*@X），排除注释/javadoc 提及。
#   Field / Method —— 反射句柄缓存：`final Field/Method` 类型变量声明，
#                     实例与静态 final 都计（当前未移植模块，值 0，随移植增长）。
set -euo pipefail

SRC="${1:-src}"
count_annot() { # $1 = 注解名
    grep -rEn "^\s*@$1\b" "$SRC/main/java" "$SRC/client/java" --include="*.java" | wc -l
}
count_handle() { # $1 = Field|Method
    grep -rEn "\bfinal\s+$1\s+[A-Za-z_]\w*\s*;" "$SRC/main/java" "$SRC/client/java" --include="*.java" | wc -l
}

printf 'overwrite=%s\n' "$(count_annot 'Overwrite')"
printf 'inject=%s\n' "$(count_annot 'Inject')"
printf 'redirect=%s\n' "$(count_annot 'Redirect')"
printf 'field=%s\n' "$(count_handle 'Field')"
printf 'method=%s\n' "$(count_handle 'Method')"
