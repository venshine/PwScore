/* 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖错误码与线性耗时。参数为快照路径 */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#include "pwscore.h"

static int failures = 0;

static void check(int cond, const char *name) {
    if (!cond) {
        failures++;
        printf("FAIL %s\n", name);
    }
}

int main(int argc, char **argv) {
    char line[4096];
    int checked = 0;
    FILE *f;
    if (argc < 2 || (f = fopen(argv[1], "rb")) == NULL) {
        fprintf(stderr, "usage: %s <score-snapshot.tsv>\n", argv[0]);
        return 2;
    }
    while (fgets(line, sizeof line, f) != NULL) {
        char *tab1, *tab2, *password, expected[64], actual[64];
        size_t len = strlen(line);
        while (len > 0 && (line[len - 1] == '\n' || line[len - 1] == '\r')) line[--len] = '\0';
        if (len == 0 || line[0] == '#') continue;
        tab1 = strchr(line, '\t');
        tab2 = tab1 ? strchr(tab1 + 1, '\t') : NULL;
        if (tab2 == NULL) {
            check(0, "malformed snapshot line");
            continue;
        }
        password = tab2 + 1;
        snprintf(expected, sizeof expected, "%.*s", (int)(tab2 - line), line);
        snprintf(actual, sizeof actual, "%d\t%s", pwscore_score(password),
                 pwscore_level_name(pwscore_level(password)));
        if (strcmp(expected, actual) != 0) {
            failures++;
            printf("FAIL snapshot [%s]: expected %s but was %s\n", password, expected, actual);
        }
        checked++;
    }
    fclose(f);
    check(checked > 0, "snapshot has no cases");

    check(pwscore_score(NULL) == PWSCORE_ERR_NULL, "null password");
    check(pwscore_score("") == PWSCORE_ERR_EMPTY, "empty password");
    check(pwscore_level("2hAj5#mne-ix.86H") == PWSCORE_EXTREMELY_STRONG, "level");
    /* 非法 UTF-8 每字节按 U+FFFD：\xff\xfe 与两个同类「其他」字符得分相同 */
    check(pwscore_score("\xff\xfe" "Ab1!xyz") == pwscore_score("\xef\xbf\xbd\xe2\x82\xac" "Ab1!xyz"),
          "invalid utf-8 decodes as U+FFFD");
    check(pwscore_score_n("ab\0cd", 5) >= 0, "embedded NUL");

    {
        size_t n = 200001;
        char *s = (char *)malloc(n + 1);
        clock_t start;
        double secs;
        memset(s, 'a', n - 1);
        s[n - 1] = 'b';
        s[n] = '\0';
        start = clock();
        pwscore_score(s);
        secs = (double)(clock() - start) / CLOCKS_PER_SEC;
        check(secs < 2.0, "nearly periodic long input is linear");
        free(s);
    }

    if (failures > 0) {
        printf("%d test(s) failed\n", failures);
        return 1;
    }
    printf("c: all tests passed\n");
    return 0;
}
