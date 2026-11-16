// Package pwscore 计算密码强度分值（0 到 14）和等级，规则见仓库 spec/SPEC.md。
//
// 长度按 UTF-16 码元计；输入按 UTF-8 解码，非法字节按 U+FFFD 处理。
//
// Licensed under the Apache License, Version 2.0
package pwscore

import (
	"errors"
	"unicode/utf16"
)

// Level 是强度等级，按分值从低到高
type Level int

const (
	Easy Level = iota
	Medium
	Strong
	VeryStrong
	ExtremelyStrong
)

// String 返回与其他语言一致的等级名，如 "VERY_STRONG"
func (l Level) String() string {
	switch l {
	case Easy:
		return "EASY"
	case Medium:
		return "MEDIUM"
	case Strong:
		return "STRONG"
	case VeryStrong:
		return "VERY_STRONG"
	default:
		return "EXTREMELY_STRONG"
	}
}

// ErrEmpty 表示密码为空串
var ErrEmpty = errors.New("password is empty")

var dictionary = []string{"password", "abc123", "iloveyou", "adobe123", "123123", "sunshine",
	"1314520", "a1b2c3", "123qwe", "aaa111", "qweasd", "admin", "passwd"}

var sequences = [][]string{
	{"abcdefghijklmnopqrstuvwxyz", "zyxwvutsrqponmlkjihgfedcba"},
	{"01234567890", "09876543210"},
	{"qwertyuiop", "poiuytrewq", "asdfghjkl", "lkjhgfdsa", "zxcvbnm", "mnbvcxz"},
}

const sequenceMin = 4

// 每组序列所有长度为 4 的片段
var sequenceKeys = func() []map[[sequenceMin]uint16]bool {
	keys := make([]map[[sequenceMin]uint16]bool, len(sequences))
	for g, group := range sequences {
		keys[g] = map[[sequenceMin]uint16]bool{}
		for _, seq := range group {
			for i := 0; i+sequenceMin <= len(seq); i++ {
				var k [sequenceMin]uint16
				for j := 0; j < sequenceMin; j++ {
					k[j] = uint16(seq[i+j])
				}
				keys[g][k] = true
			}
		}
	}
	return keys
}()

var dictionaryUnits = func() [][]uint16 {
	units := make([][]uint16, len(dictionary))
	for i, w := range dictionary {
		units[i] = utf16.Encode([]rune(w))
	}
	return units
}()

var daysInMonthTable = [12]int{31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31}

const (
	num = iota
	small
	capital
	other
)

func charType(c uint16) int {
	switch {
	case c >= '0' && c <= '9':
		return num
	case c >= 'a' && c <= 'z':
		return small
	case c >= 'A' && c <= 'Z':
		return capital
	}
	return other
}

func repeatUnitLength(s []uint16) int {
	n := len(s)
	prefix := make([]int, n)
	for i := 1; i < n; i++ {
		k := prefix[i-1]
		for k > 0 && s[i] != s[k] {
			k = prefix[k-1]
		}
		if s[i] == s[k] {
			k++
		}
		prefix[i] = k
	}
	period := n - prefix[n-1]
	if n%period == 0 {
		return period
	}
	return n
}

func isSameChar(s []uint16, from, to int) bool {
	for i := from + 1; i < to; i++ {
		if s[i] != s[from] {
			return false
		}
	}
	return true
}

func contains(haystack, needle []uint16) bool {
	for i := 0; i+len(needle) <= len(haystack); i++ {
		match := true
		for j := range needle {
			if haystack[i+j] != needle[j] {
				match = false
				break
			}
		}
		if match {
			return true
		}
	}
	return false
}

func digits(s []uint16, start, count int) int {
	v := 0
	for i := start; i < start+count; i++ {
		v = v*10 + int(s[i]-'0')
	}
	return v
}

func daysInMonth(year, month int) int {
	leap := year%4 == 0 && (year%100 != 0 || year%400 == 0)
	if month == 2 && leap {
		return 29
	}
	return daysInMonthTable[month-1]
}

func containsDate(s []uint16) bool {
	run := 0
	for i, c := range s {
		if charType(c) == num {
			run++
		} else {
			run = 0
		}
		if run >= 8 {
			start := i - 7
			year, month, day := digits(s, start, 4), digits(s, start+4, 2), digits(s, start+6, 2)
			if year >= 1950 && year <= 2049 && month >= 1 && month <= 12 && day >= 1 && day <= daysInMonth(year, month) {
				return true
			}
		}
	}
	return false
}

// Score 计算密码强度分值，0 到 14；password 为空串时返回 ErrEmpty
func Score(password string) (int, error) {
	if password == "" {
		return 0, ErrEmpty
	}
	units := utf16.Encode([]rune(password))
	// 整串由更短片段重复组成时，按重复单元评分
	s := units[:repeatUnitLength(units)]
	n := len(s)

	var count [4]int
	for _, c := range s {
		count[charType(c)]++
	}
	nNum, nSmall, nCapital, nOther := count[num], count[small], count[capital], count[other]
	classes := 0
	for _, c := range count {
		if c > 0 {
			classes++
		}
	}

	level := 0
	inc := func(cond bool) {
		if cond {
			level++
		}
	}
	// 加分
	inc(nNum > 0)
	inc(nSmall > 0)
	inc(n > 4 && nCapital > 0)
	inc(n > 6 && nOther > 0)
	inc(n > 4 && classes >= 2)
	inc(n > 6 && classes >= 3)
	inc(n > 8 && classes == 4)
	inc(n > 6 && (nNum >= 3 && nSmall >= 3 || nNum >= 3 && nCapital >= 3 || nNum >= 3 && nOther >= 2 ||
		nSmall >= 3 && nCapital >= 3 || nSmall >= 3 && nOther >= 2 || nCapital >= 3 && nOther >= 2))
	inc(n > 8 && (nNum >= 2 && nSmall >= 2 && nCapital >= 2 || nNum >= 2 && nSmall >= 2 && nOther >= 2 ||
		nNum >= 2 && nCapital >= 2 && nOther >= 2 || nSmall >= 2 && nCapital >= 2 && nOther >= 2))
	inc(n > 10 && nNum >= 2 && nSmall >= 2 && nCapital >= 2 && nOther >= 2)
	inc(nOther >= 3)
	inc(nOther >= 6)
	inc(n > 12)
	inc(n >= 16)

	// 减分；只把 A-Z 转小写
	lower := make([]uint16, n)
	for i, c := range s {
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		lower[i] = c
	}
	for _, keys := range sequenceKeys {
		for i := 0; i+sequenceMin <= n; i++ {
			var k [sequenceMin]uint16
			copy(k[:], lower[i:i+sequenceMin])
			if keys[k] {
				level--
				break
			}
		}
	}
	if nNum == n || nSmall == n || nCapital == n {
		level--
	}
	if n%2 == 0 && isSameChar(s, 0, n/2) && isSameChar(s, n/2, n) {
		level--
	}
	if containsDate(s) {
		level--
	}
	for _, w := range dictionaryUnits {
		if contains(lower, w) || contains(w, lower) {
			level--
			break
		}
	}
	if n <= 6 {
		level--
	}
	if n <= 4 {
		level--
	}
	if n <= 3 || isSameChar(s, 0, n) {
		level = 0
	}
	if level < 0 {
		level = 0
	}
	return level, nil
}

// LevelOf 计算密码强度等级；password 为空串时返回 ErrEmpty
func LevelOf(password string) (Level, error) {
	score, err := Score(password)
	if err != nil {
		return Easy, err
	}
	switch {
	case score <= 3:
		return Easy, nil
	case score <= 6:
		return Medium, nil
	case score <= 9:
		return Strong, nil
	case score <= 12:
		return VeryStrong, nil
	}
	return ExtremelyStrong, nil
}
