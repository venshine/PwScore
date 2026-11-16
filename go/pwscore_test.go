package pwscore

import (
	"errors"
	"fmt"
	"os"
	"strings"
	"testing"
	"time"
)

// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv
func TestSnapshot(t *testing.T) {
	data, err := os.ReadFile("../spec/score-snapshot.tsv")
	if err != nil {
		t.Fatal(err)
	}
	checked := 0
	for _, line := range strings.Split(string(data), "\n") {
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		cols := strings.SplitN(line, "\t", 3)
		score, err := Score(cols[2])
		if err != nil {
			t.Fatalf("[%s]: %v", cols[2], err)
		}
		level, _ := LevelOf(cols[2])
		if got, want := fmt.Sprintf("%d\t%s", score, level), cols[0]+"\t"+cols[1]; got != want {
			t.Errorf("[%s]: want %q, got %q", cols[2], want, got)
		}
		checked++
	}
	if checked == 0 {
		t.Fatal("snapshot has no cases")
	}
}

func TestErrors(t *testing.T) {
	if _, err := Score(""); !errors.Is(err, ErrEmpty) {
		t.Errorf("empty password: got %v", err)
	}
	if level, _ := LevelOf("2hAj5#mne-ix.86H"); level != ExtremelyStrong {
		t.Errorf("got %v", level)
	}
	// 非法 UTF-8 按 U+FFFD 处理，不报错
	if _, err := Score("\xff\xfeAb1!xyz"); err != nil {
		t.Errorf("invalid utf-8: %v", err)
	}
}

func TestLinearTime(t *testing.T) {
	start := time.Now()
	Score(strings.Repeat("a", 200000) + "b")
	if d := time.Since(start); d > 2*time.Second {
		t.Errorf("took %v", d)
	}
}
