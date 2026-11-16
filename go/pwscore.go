// Package pwscore 计算密码强度分值（0 到 14）和等级，规则见仓库 spec/SPEC.md。
//
// 按 UTF-16 码元处理；输入按 UTF-8 解码，非法字节按 U+FFFD 处理。
//
// Licensed under the Apache License, Version 2.0
package pwscore

import (
	"errors"
	"strings"
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

var dictionary = []string{
	"password", "passwd", "admin", "iloveyou", "sunshine", "adobe123", "abc123", "123123", "1314520", "a1b2c3",
	"123qwe", "aaa111", "qweasd",
	"welcome", "letmein", "login", "master", "monkey", "dragon", "shadow", "princess", "football", "baseball",
	"soccer", "superman", "batman", "starwars", "pokemon", "freedom", "whatever", "trustno1", "secret", "hello",
	"summer", "winter", "spring", "autumn", "flower", "angel", "lovely", "loveyou", "killer", "hunter", "cookie",
	"banana", "orange", "purple", "silver", "golden", "diamond", "chocolate", "computer", "internet", "google",
	"apple", "changeme", "default", "guest",
	"woaini", "iloveu", "zhang", "wang", "yang", "huang", "zhao", "xiaoming", "laopo", "laogong", "baobao",
	"nihao", "wodemima", "mima", "qq123",
	"5201314", "520520", "147258", "159753", "147852", "789456", "741852", "963852", "258369", "123321", "112233",
}

// 序列分组，每组命中扣 1 分；每个序列的反向自动加入
var sequences = [][]string{
	{"abcdefghijklmnopqrstuvwxyz"},
	{"01234567890"},
	{"qwertyuiop", "asdfghjkl", "zxcvbnm", "1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/", "qazwsxedcrfvtgbyhnujmikolp",
		"1q2w3e4r5t6y7u8i9o0p", "q1w2e3r4t5y6u7i8o9p0", "zaq1xsw2cde3vfr4bgt5nhy6mju7", "!@#$%^&*()_+"},
}

const (
	sequenceMin = 4
	maxBlock    = 16
	leetFrom    = "@401!3$57"
	leetTo      = "aaoiiesst"
)

// 每组序列（含反向）所有长度为 4 的片段
var sequenceKeys = func() []map[[sequenceMin]uint16]bool {
	keys := make([]map[[sequenceMin]uint16]bool, len(sequences))
	for g, group := range sequences {
		keys[g] = map[[sequenceMin]uint16]bool{}
		for _, base := range group {
			for _, seq := range []string{base, reverse(base)} {
				for i := 0; i+sequenceMin <= len(seq); i++ {
					var k [sequenceMin]uint16
					for j := 0; j < sequenceMin; j++ {
						k[j] = uint16(seq[i+j])
					}
					keys[g][k] = true
				}
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
	intl
	other
)

func reverse(s string) string {
	b := []byte(s)
	for i, j := 0, len(b)-1; i < j; i, j = i+1, j-1 {
		b[i], b[j] = b[j], b[i]
	}
	return string(b)
}

func charType(c uint16) int {
	switch {
	case c >= '0' && c <= '9':
		return num
	case c >= 'a' && c <= 'z':
		return small
	case c >= 'A' && c <= 'Z':
		return capital
	case c >= 128:
		return intl
	}
	return other
}

func isPair(s []uint16, i, end int) bool {
	return i+1 < end && s[i] >= 0xD800 && s[i] <= 0xDBFF && s[i+1] >= 0xDC00 && s[i+1] <= 0xDFFF
}

func regionEquals(s []uint16, a, b, count int) bool {
	for k := 0; k < count; k++ {
		if s[a+k] != s[b+k] {
			return false
		}
	}
	return true
}

func characterCount(s []uint16, from, to int) int {
	count := 0
	for i := from; i < to; i++ {
		if isPair(s, i, to) {
			i++
		}
		count++
	}
	return count
}

// effectiveLength：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
func effectiveLength(s []uint16) int {
	n := len(s)
	length := 0
	for i := 0; i < n; {
		block := 0
		for m := 1; m <= maxBlock && i+2*m <= n; m++ {
			if s[i] == s[i+m] && regionEquals(s, i, i+m, m) {
				block = m
				break
			}
		}
		if block > 0 {
			r := 2
			for i+(r+1)*block <= n && regionEquals(s, i, i+r*block, block) {
				r++
			}
			if block == 1 {
				length += 2
			} else {
				length += characterCount(s, i, i+block)
			}
			i += r * block
		} else {
			if isPair(s, i, n) {
				i += 2
			} else {
				i++
			}
			length++
		}
	}
	return length
}

func lengthPoints(length int) int {
	switch {
	case length <= 4:
		return -2
	case length == 5:
		return -1
	case length < 8:
		return 0
	}
	if p := (length-8)/4 + 1; p < 5 {
		return p
	}
	return 5
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
		if equalAt(haystack, i, needle) {
			return true
		}
	}
	return false
}

func equalAt(haystack []uint16, i int, needle []uint16) bool {
	for j := range needle {
		if haystack[i+j] != needle[j] {
			return false
		}
	}
	return true
}

func digits(s []uint16, start, count int) int {
	v := 0
	for i := start; i < start+count; i++ {
		v = v*10 + int(s[i]-'0')
	}
	return v
}

func maxInt(a, b int) int {
	if a > b {
		return a
	}
	return b
}

func isYear(year int) bool { return year >= 1950 && year <= 2049 }

func isValidDate(year, month, day int) bool {
	if month < 1 || month > 12 || day < 1 {
		return false
	}
	leap := year%4 == 0 && (year%100 != 0 || year%400 == 0)
	if month == 2 && leap {
		return day <= 29
	}
	return day <= daysInMonthTable[month-1]
}

func isDate8(s []uint16, k int) bool {
	head := digits(s, k, 4)
	if isYear(head) && isValidDate(head, digits(s, k+4, 2), digits(s, k+6, 2)) {
		return true
	}
	tail := digits(s, k+4, 4)
	return isYear(tail) && (isValidDate(tail, digits(s, k+2, 2), digits(s, k, 2)) ||
		isValidDate(tail, digits(s, k, 2), digits(s, k+2, 2)))
}

// containsDateOrYear：8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
func containsDateOrYear(s []uint16) bool {
	for i := 0; i < len(s); {
		if charType(s[i]) != num {
			i++
			continue
		}
		start := i
		for i < len(s) && charType(s[i]) == num {
			i++
		}
		run := i - start
		for k := start; k+8 <= i; k++ {
			if isDate8(s, k) {
				return true
			}
		}
		if run == 6 && isValidDate(2000+digits(s, start, 2), digits(s, start+2, 2), digits(s, start+4, 2)) {
			return true
		}
		if run == 4 && isYear(digits(s, start, 4)) {
			return true
		}
	}
	return false
}

// Score 计算密码强度分值，0 到 14；password 为空串时返回 ErrEmpty
func Score(password string) (int, error) {
	if password == "" {
		return 0, ErrEmpty
	}
	s := utf16.Encode([]rune(password))
	// 全角 ASCII 还原为半角
	for i, c := range s {
		if c >= 0xFF01 && c <= 0xFF5E {
			s[i] = c - 0xFEE0
		}
	}
	n := len(s)
	length := effectiveLength(s)

	// 各类统计「不同字符」的个数
	var sets [5]map[uint16]bool
	for i := range sets {
		sets[i] = map[uint16]bool{}
	}
	all := map[uint16]bool{}
	for _, c := range s {
		sets[charType(c)][c] = true
		all[c] = true
	}
	nNum, nSmall, nCapital, nIntl, nOther := len(sets[num]), len(sets[small]), len(sets[capital]), len(sets[intl]), len(sets[other])
	classes := 0
	for _, set := range sets {
		if len(set) > 0 {
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
	inc(nIntl > 0)
	inc(length > 4 && nCapital > 0)
	inc(length > 6 && nOther > 0)
	inc(length > 4 && classes >= 2)
	inc(length > 6 && classes >= 3)
	inc(length > 8 && classes >= 4)
	inc(length > 6 && (nNum >= 3 && nSmall >= 3 || nNum >= 3 && nCapital >= 3 || nNum >= 3 && nOther >= 2 ||
		nSmall >= 3 && nCapital >= 3 || nSmall >= 3 && nOther >= 2 || nCapital >= 3 && nOther >= 2))
	inc(length > 8 && (nNum >= 2 && nSmall >= 2 && nCapital >= 2 || nNum >= 2 && nSmall >= 2 && nOther >= 2 ||
		nNum >= 2 && nCapital >= 2 && nOther >= 2 || nSmall >= 2 && nCapital >= 2 && nOther >= 2))
	inc(length > 10 && nNum >= 2 && nSmall >= 2 && nCapital >= 2 && nOther >= 2)
	inc(nOther >= 3)
	inc(nOther >= 6)
	level += lengthPoints(length)

	// 减分；只把 A-Z 转小写
	lower := make([]uint16, n)
	leet := make([]uint16, n)
	for i, c := range s {
		if c >= 'A' && c <= 'Z' {
			c += 'a' - 'A'
		}
		lower[i] = c
		leet[i] = c
		if c < 128 {
			if k := strings.IndexByte(leetFrom, byte(c)); k >= 0 {
				leet[i] = uint16(leetTo[k])
			}
		}
	}
	covered := make([]bool, n)
	for _, keys := range sequenceKeys {
		hit := false
		for i := 0; i+sequenceMin <= n; i++ {
			var k [sequenceMin]uint16
			copy(k[:], lower[i:i+sequenceMin])
			if keys[k] {
				hit = true
				for j := i; j < i+sequenceMin; j++ {
					covered[j] = true
				}
			}
		}
		if hit {
			level--
		}
	}
	coveredCount := 0
	for _, b := range covered {
		if b {
			coveredCount++
		}
	}
	if 2*coveredCount >= n {
		level--
	}
	if length < 6 && classes == 1 && (nNum > 0 || nSmall > 0 || nCapital > 0) {
		level--
	}
	if n%2 == 0 && isSameChar(s, 0, n/2) && isSameChar(s, n/2, n) {
		level--
	}
	if containsDateOrYear(s) {
		level--
	}
	if length >= 8 && len(all) <= maxInt(4, length/3) {
		level--
	}
	for _, w := range dictionaryUnits {
		if contains(lower, w) || contains(w, lower) || contains(leet, w) {
			level -= 2
			break
		}
	}
	if length <= 3 || isSameChar(s, 0, n) {
		level = 0
	}
	if level < 0 {
		level = 0
	}
	if level > 14 {
		level = 14
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
