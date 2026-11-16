package PwScore;

# PwScore：密码强度评分，规则见仓库 spec/SPEC.md。按 UTF-16 码元处理。
# 参数应为已解码的字符串（Perl 字符串），而不是 UTF-8 字节串。
#
# Licensed under the Apache License, Version 2.0

use strict;
use warnings;
use Carp qw(croak);
use Exporter 'import';

our $VERSION   = '3.1.0';
our @EXPORT_OK = qw(score level);

my @DICTIONARY = qw(
    password passwd admin iloveyou sunshine adobe123 abc123 123123 1314520 a1b2c3 123qwe aaa111 qweasd
    welcome letmein login master monkey dragon shadow princess football baseball soccer superman batman
    starwars pokemon freedom whatever trustno1 secret hello summer winter spring autumn flower angel lovely
    loveyou killer hunter cookie banana orange purple silver golden diamond chocolate computer internet
    google apple changeme default guest
    woaini iloveu zhang wang yang huang zhao xiaoming laopo laogong baobao nihao wodemima mima qq123
    5201314 520520 147258 159753 147852 789456 741852 963852 258369 123321 112233
);

# 序列分组，每组命中扣 1 分；每个序列的反向自动加入
my @SEQUENCES = (
    ['abcdefghijklmnopqrstuvwxyz', 'acegikmoqsuwy', 'bdfhjlnprtvxz'],
    ['01234567890', '02468', '13579'],
    [   'qwertyuiop', 'asdfghjkl', 'zxcvbnm', '1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/',
        'qazwsxedcrfvtgbyhnujmikolp', '1q2w3e4r5t6y7u8i9o0p', 'q1w2e3r4t5y6u7i8o9p0',
        'zaq1xsw2cde3vfr4bgt5nhy6mju7', '!@#$%^&*()_+'
    ],
);

my $SEQUENCE_MIN = 4;
my $MAX_BLOCK    = 16;

# 每组序列（含反向）所有长度为 4 的片段
my @SEQUENCE_KEYS = map {
    my %keys;
    for my $base (@$_) {
        for my $seq ($base, scalar reverse $base) {
            $keys{ substr $seq, $_, $SEQUENCE_MIN } = 1 for 0 .. length($seq) - $SEQUENCE_MIN;
        }
    }
    \%keys;
} @SEQUENCES;

my @DAYS_IN_MONTH = (31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);

use constant { NUM => 0, SMALL => 1, CAPITAL => 2, INTL => 3, OTHER => 4 };

# 转成 UTF-16 码元数组；全角 ASCII 还原为半角
sub _utf16_units {
    my ($str) = @_;
    my @units;
    for my $cp (map {ord} split //, $str) {
        if ($cp > 0xFFFF) {
            $cp -= 0x10000;
            push @units, 0xD800 + ($cp >> 10), 0xDC00 + ($cp & 0x3FF);
        }
        elsif ($cp >= 0xFF01 && $cp <= 0xFF5E) {
            push @units, $cp - 0xFEE0;
        }
        else {
            push @units, $cp;
        }
    }
    return \@units;
}

sub _char_type {
    my ($c) = @_;
    return NUM     if $c >= 48 && $c <= 57;
    return SMALL   if $c >= 97 && $c <= 122;
    return CAPITAL if $c >= 65 && $c <= 90;
    return INTL    if $c >= 128;
    return OTHER;
}

sub _is_pair {
    my ($s, $i, $end) = @_;
    return $i + 1 < $end && $s->[$i] >= 0xD800 && $s->[$i] <= 0xDBFF && $s->[ $i + 1 ] >= 0xDC00
        && $s->[ $i + 1 ] <= 0xDFFF;
}

sub _region_equals {
    my ($s, $a, $b, $count) = @_;
    for my $k (0 .. $count - 1) {
        return 0 if $s->[ $a + $k ] != $s->[ $b + $k ];
    }
    return 1;
}

sub _character_count {
    my ($s, $from, $to) = @_;
    my ($i, $count) = ($from, 0);
    while ($i < $to) {
        $i += _is_pair($s, $i, $to) ? 2 : 1;
        $count++;
    }
    return $count;
}

# 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
sub _effective_length {
    my ($s) = @_;
    my $n = @$s;
    my ($i, $len) = (0, 0);
    while ($i < $n) {
        my $block = 0;
        for my $m (1 .. $MAX_BLOCK) {
            last if $i + 2 * $m > $n;
            if ($s->[$i] == $s->[ $i + $m ] && _region_equals($s, $i, $i + $m, $m)) {
                $block = $m;
                last;
            }
        }
        if ($block) {
            my $r = 2;
            $r++ while $i + ($r + 1) * $block <= $n && _region_equals($s, $i, $i + $r * $block, $block);
            $len += $block == 1 ? 2 : _character_count($s, $i, $i + $block);
            $i   += $r * $block;
        }
        else {
            $i += _is_pair($s, $i, $n) ? 2 : 1;
            $len++;
        }
    }
    return $len;
}

sub _length_points {
    my ($len) = @_;
    return -2 if $len <= 4;
    return -1 if $len == 5;
    return 0  if $len < 8;
    my $p = int(($len - 8) / 4) + 1;
    return $p < 5 ? $p : 5;
}

sub _same_char {
    my ($s, $from, $to) = @_;
    for my $i ($from + 1 .. $to - 1) {
        return 0 if $s->[$i] != $s->[$from];
    }
    return 1;
}

sub _digits {
    my ($s, $start, $count) = @_;
    my $v = 0;
    $v = $v * 10 + ($s->[$_] - 48) for $start .. $start + $count - 1;
    return $v;
}

sub _is_year { my ($y) = @_; return $y >= 1950 && $y <= 2049 }

sub _is_valid_date {
    my ($year, $month, $day) = @_;
    return 0 if $month < 1 || $month > 12 || $day < 1;
    my $leap = $year % 4 == 0 && ($year % 100 != 0 || $year % 400 == 0);
    return $day <= ($month == 2 && $leap ? 29 : $DAYS_IN_MONTH[ $month - 1 ]);
}

sub _is_date8 {
    my ($s, $k) = @_;
    my $head = _digits($s, $k, 4);
    return 1 if _is_year($head) && _is_valid_date($head, _digits($s, $k + 4, 2), _digits($s, $k + 6, 2));
    my $tail = _digits($s, $k + 4, 4);
    return _is_year($tail)
        && (_is_valid_date($tail, _digits($s, $k + 2, 2), _digits($s, $k, 2))
        || _is_valid_date($tail, _digits($s, $k, 2), _digits($s, $k + 2, 2)));
}

# 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
sub _contains_date_or_year {
    my ($s) = @_;
    my $i = 0;
    while ($i < @$s) {
        if (_char_type($s->[$i]) != NUM) {
            $i++;
            next;
        }
        my $start = $i;
        $i++ while $i < @$s && _char_type($s->[$i]) == NUM;
        my $run = $i - $start;
        for (my $k = $start; $k + 8 <= $i; $k++) {
            return 1 if _is_date8($s, $k);
        }
        return 1
            if $run == 6
            && _is_valid_date(2000 + _digits($s, $start, 2), _digits($s, $start + 2, 2), _digits($s, $start + 4, 2));
        return 1 if $run == 4 && _is_year(_digits($s, $start, 4));
    }
    return 0;
}

# 计算密码强度分值，0 到 14；undef 或空串 croak
sub score {
    my ($password) = @_;
    croak 'password is undef' unless defined $password;
    croak 'password is empty' if $password eq '';

    my $s   = _utf16_units($password);
    my $n   = @$s;
    my $len = _effective_length($s);

    # 各类统计「不同字符」的个数
    my @sets = map { {} } 1 .. 5;
    my %all;
    for my $c (@$s) {
        $sets[ _char_type($c) ]{$c} = 1;
        $all{$c} = 1;
    }
    my ($num, $small, $capital, $intl, $other) = map { scalar keys %$_ } @sets;
    my $classes = grep { %$_ } @sets;

    my @gains = (
        $num > 0,
        $small > 0,
        $intl > 0,
        $len > 4 && $capital > 0,
        $len > 6 && $other > 0,
        $len > 4 && $classes >= 2,
        $len > 6 && $classes >= 3,
        $len > 8 && $classes >= 4,
        $len > 6 && ($num >= 3 && $small >= 3
            || $num >= 3 && $capital >= 3
            || $num >= 3 && $other >= 2
            || $small >= 3 && $capital >= 3
            || $small >= 3 && $other >= 2
            || $capital >= 3 && $other >= 2),
        $len > 8 && ($num >= 2 && $small >= 2 && $capital >= 2
            || $num >= 2 && $small >= 2 && $other >= 2
            || $num >= 2 && $capital >= 2 && $other >= 2
            || $small >= 2 && $capital >= 2 && $other >= 2),
        $len > 10 && $num >= 2 && $small >= 2 && $capital >= 2 && $other >= 2,
        $other >= 3,
        $other >= 6,
    );
    my $level = (grep {$_} @gains) + _length_points($len);

    # 减分；只把 A-Z 转小写。码元逐个转回字符拼成串，便于用 index 和 substr 匹配（码元都不超过 0xFFFF）
    my $lower = join '', map { chr($_ >= 65 && $_ <= 90 ? $_ + 32 : $_) } @$s;
    my @covered = (0) x $n;
    for my $keys (@SEQUENCE_KEYS) {
        my $hit = 0;
        for my $i (0 .. $n - $SEQUENCE_MIN) {
            next unless $keys->{ substr $lower, $i, $SEQUENCE_MIN };
            $hit = 1;
            @covered[ $i .. $i + $SEQUENCE_MIN - 1 ] = (1) x $SEQUENCE_MIN;
        }
        $level-- if $hit;
    }
    my $covered_count = grep {$_} @covered;
    $level-- if 2 * $covered_count >= $n;
    $level-- if $len < 6 && $classes == 1 && ($num > 0 || $small > 0 || $capital > 0);
    $level-- if $n % 2 == 0 && _same_char($s, 0, $n / 2) && _same_char($s, $n / 2, $n);
    $level-- if _contains_date_or_year($s);
    my $max_distinct = int($len / 3) > 4 ? int($len / 3) : 4;
    $level-- if $len >= 8 && keys(%all) <= $max_distinct;
    (my $leet = $lower) =~ tr/@401!3$57/aaoiiesst/;
    $level -= 2 if grep { index($lower, $_) >= 0 || index($_, $lower) >= 0 || index($leet, $_) >= 0 } @DICTIONARY;
    $level = 0 if $len <= 3 || _same_char($s, 0, $n);
    return $level < 0 ? 0 : $level > 14 ? 14 : $level;
}

# 计算密码强度等级：EASY、MEDIUM、STRONG、VERY_STRONG、EXTREMELY_STRONG；报错同 score
sub level {
    my $value = score(@_);
    return 'EASY'        if $value <= 3;
    return 'MEDIUM'      if $value <= 6;
    return 'STRONG'      if $value <= 9;
    return 'VERY_STRONG' if $value <= 12;
    return 'EXTREMELY_STRONG';
}

1;
