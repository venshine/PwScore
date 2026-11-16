package PwScore;

# PwScore：密码强度评分，规则见仓库 spec/SPEC.md。长度按 UTF-16 码元计。
# 参数应为已解码的字符串（Perl 字符串），而不是 UTF-8 字节串。
#
# Licensed under the Apache License, Version 2.0

use strict;
use warnings;
use Carp qw(croak);
use Exporter 'import';

our $VERSION   = '3.0.0';
our @EXPORT_OK = qw(score level);

my @DICTIONARY = qw(password abc123 iloveyou adobe123 123123 sunshine
    1314520 a1b2c3 123qwe aaa111 qweasd admin passwd);

my @SEQUENCES = (
    [qw(abcdefghijklmnopqrstuvwxyz zyxwvutsrqponmlkjihgfedcba)],
    [qw(01234567890 09876543210)],
    [qw(qwertyuiop poiuytrewq asdfghjkl lkjhgfdsa zxcvbnm mnbvcxz)],
);

my $SEQUENCE_MIN = 4;

# 每组序列所有长度为 4 的片段
my @SEQUENCE_KEYS = map {
    my %keys;
    for my $seq (@$_) {
        $keys{ substr $seq, $_, $SEQUENCE_MIN } = 1 for 0 .. length($seq) - $SEQUENCE_MIN;
    }
    \%keys;
} @SEQUENCES;

my @DAYS_IN_MONTH = (31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31);

use constant { NUM => 0, SMALL => 1, CAPITAL => 2, OTHER => 3 };

sub _utf16_units {
    my ($str) = @_;
    my @units;
    for my $cp (map { ord } split //, $str) {
        if ($cp > 0xFFFF) {
            $cp -= 0x10000;
            push @units, 0xD800 + ($cp >> 10), 0xDC00 + ($cp & 0x3FF);
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
    return OTHER;
}

sub _repeat_unit_length {
    my ($s) = @_;
    my $n = @$s;
    my @prefix = (0) x $n;
    for my $i (1 .. $n - 1) {
        my $k = $prefix[ $i - 1 ];
        $k = $prefix[ $k - 1 ] while $k > 0 && $s->[$i] != $s->[$k];
        $k++ if $s->[$i] == $s->[$k];
        $prefix[$i] = $k;
    }
    my $period = $n - $prefix[ $n - 1 ];
    return $n % $period == 0 ? $period : $n;
}

sub _same_char {
    my ($s, $from, $to) = @_;
    for my $i ($from + 1 .. $to - 1) {
        return 0 if $s->[$i] != $s->[$from];
    }
    return 1;
}

sub _days_in_month {
    my ($year, $month) = @_;
    my $leap = $year % 4 == 0 && ($year % 100 != 0 || $year % 400 == 0);
    return $month == 2 && $leap ? 29 : $DAYS_IN_MONTH[ $month - 1 ];
}

sub _contains_date {
    my ($s) = @_;
    my $run = 0;
    for my $i (0 .. $#$s) {
        $run = _char_type($s->[$i]) == NUM ? $run + 1 : 0;
        next if $run < 8;
        my @d     = map { $_ - 48 } @{$s}[ $i - 7 .. $i ];
        my $year  = $d[0] * 1000 + $d[1] * 100 + $d[2] * 10 + $d[3];
        my $month = $d[4] * 10 + $d[5];
        my $day   = $d[6] * 10 + $d[7];
        return 1
            if $year >= 1950 && $year <= 2049 && $month >= 1 && $month <= 12 && $day >= 1
            && $day <= _days_in_month($year, $month);
    }
    return 0;
}

# 计算密码强度分值，0 到 14；undef 或空串 croak
sub score {
    my ($password) = @_;
    croak 'password is undef' unless defined $password;
    croak 'password is empty' if $password eq '';

    my $units = _utf16_units($password);
    # 整串由更短片段重复组成时，按重复单元评分
    my $len = _repeat_unit_length($units);
    my @s   = @{$units}[ 0 .. $len - 1 ];

    my @count = (0, 0, 0, 0);
    $count[ _char_type($_) ]++ for @s;
    my ($num, $small, $capital, $other) = @count;
    my $classes = grep { $_ > 0 } @count;

    my @gains = (
        $num > 0,
        $small > 0,
        $len > 4 && $capital > 0,
        $len > 6 && $other > 0,
        $len > 4 && $classes >= 2,
        $len > 6 && $classes >= 3,
        $len > 8 && $classes == 4,
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
        $len > 12,
        $len >= 16,
    );
    my $level = grep { $_ } @gains;

    # 减分；只把 A-Z 转小写。码元逐个转回字符拼成串，便于用 index 和 substr 匹配（码元都不超过 0xFFFF）
    my $lower = join '', map { chr($_ >= 65 && $_ <= 90 ? $_ + 32 : $_) } @s;
    for my $keys (@SEQUENCE_KEYS) {
        for my $i (0 .. $len - $SEQUENCE_MIN) {
            if ($keys->{ substr $lower, $i, $SEQUENCE_MIN }) {
                $level--;
                last;
            }
        }
    }
    $level-- if $num == $len || $small == $len || $capital == $len;
    $level-- if $len % 2 == 0 && _same_char(\@s, 0, $len / 2) && _same_char(\@s, $len / 2, $len);
    $level-- if _contains_date(\@s);
    $level-- if grep { index($lower, $_) >= 0 || index($_, $lower) >= 0 } @DICTIONARY;
    $level-- if $len <= 6;
    $level-- if $len <= 4;
    $level = 0 if $len <= 3 || _same_char(\@s, 0, $len);
    return $level < 0 ? 0 : $level;
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
