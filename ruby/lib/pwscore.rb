# frozen_string_literal: true

require 'set'

# PwScore：密码强度评分，规则见仓库 spec/SPEC.md。按 UTF-16 码元处理。
#
# Licensed under the Apache License, Version 2.0
module PwScore
  VERSION = '3.1.0'

  # 强度等级，按分值从低到高
  LEVELS = %i[easy medium strong very_strong extremely_strong].freeze

  DICTIONARY = %w[
    password passwd admin iloveyou sunshine adobe123 abc123 123123 1314520 a1b2c3 123qwe aaa111 qweasd
    welcome letmein login master monkey dragon shadow princess football baseball soccer superman batman
    starwars pokemon freedom whatever trustno1 secret hello summer winter spring autumn flower angel lovely
    loveyou killer hunter cookie banana orange purple silver golden diamond chocolate computer internet
    google apple changeme default guest
    woaini iloveu zhang wang yang huang zhao xiaoming laopo laogong baobao nihao wodemima mima qq123
    5201314 520520 147258 159753 147852 789456 741852 963852 258369 123321 112233
  ].freeze

  # 序列分组，每组命中扣 1 分；每个序列的反向自动加入
  SEQUENCES = [
    %w[abcdefghijklmnopqrstuvwxyz acegikmoqsuwy bdfhjlnprtvxz],
    %w[01234567890 02468 13579],
    ['qwertyuiop', 'asdfghjkl', 'zxcvbnm', '1qaz2wsx3edc4rfv5tgb6yhn7ujm8ik,9ol.0p;/', 'qazwsxedcrfvtgbyhnujmikolp',
     '1q2w3e4r5t6y7u8i9o0p', 'q1w2e3r4t5y6u7i8o9p0', 'zaq1xsw2cde3vfr4bgt5nhy6mju7', '!@#$%^&*()_+']
  ].freeze

  SEQUENCE_MIN = 4
  MAX_BLOCK = 16

  # 每组序列（含反向）所有长度为 4 的片段，按码元数组存放
  SEQUENCE_KEYS = SEQUENCES.map do |group|
    group.flat_map { |seq| [seq, seq.reverse] }
         .flat_map { |seq| seq.bytes.each_cons(SEQUENCE_MIN).to_a }
         .to_h { |k| [k, true] }.freeze
  end.freeze

  LEET = '@401!3$57'.bytes.zip('aaoiiesst'.bytes).to_h.freeze

  DAYS_IN_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31].freeze

  NUM = 0
  SMALL = 1
  CAPITAL = 2
  INTL = 3
  OTHER = 4

  module_function

  # 计算密码强度分值，0 到 14。nil 或空串抛 ArgumentError
  def score(password)
    raise ArgumentError, 'password is nil' if password.nil?
    raise ArgumentError, 'password is empty' if password.empty?

    # 全角 ASCII 还原为半角
    s = utf16_units(password).map { |c| c >= 0xFF01 && c <= 0xFF5E ? c - 0xFEE0 : c }
    n = s.length
    len = effective_length(s)

    # 各类统计「不同字符」的个数
    sets = Array.new(5) { Set.new }
    s.each { |c| sets[char_type(c)] << c }
    num, small, capital, intl, other = sets.map(&:size)
    classes = sets.count { |x| !x.empty? }

    gains = [
      num > 0,
      small > 0,
      intl > 0,
      len > 4 && capital > 0,
      len > 6 && other > 0,
      len > 4 && classes >= 2,
      len > 6 && classes >= 3,
      len > 8 && classes >= 4,
      len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2 ||
                  small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2),
      len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2 ||
                  num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2),
      len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2,
      other >= 3,
      other >= 6
    ]
    level = gains.count(true) + length_points(len)

    # 减分；只把 A-Z 转小写
    lower = s.map { |c| c >= 65 && c <= 90 ? c + 32 : c }
    covered = Array.new(n, false)
    SEQUENCE_KEYS.each do |keys|
      hit = false
      (0..n - SEQUENCE_MIN).each do |i|
        next unless keys.key?(lower[i, SEQUENCE_MIN])

        hit = true
        (i...i + SEQUENCE_MIN).each { |j| covered[j] = true }
      end
      level -= 1 if hit
    end
    level -= 1 if 2 * covered.count(true) >= n
    level -= 1 if len < 6 && classes == 1 && (num > 0 || small > 0 || capital > 0)
    level -= 1 if n.even? && same_char?(s, 0, n / 2) && same_char?(s, n / 2, n)
    level -= 1 if contains_date_or_year?(s)
    level -= 1 if len >= 8 && s.uniq.size <= [4, len / 3].max
    # 码元按码点打包成串，字典词都是 ASCII，用原生 include? 匹配
    lower_text = lower.pack('U*')
    leet_text = lower.map { |c| LEET.fetch(c, c) }.pack('U*')
    level -= 2 if DICTIONARY.any? { |w| lower_text.include?(w) || w.include?(lower_text) || leet_text.include?(w) }
    level = 0 if len <= 3 || same_char?(s, 0, n)
    level.clamp(0, 14)
  end

  # 计算密码强度等级（:easy、:medium、:strong、:very_strong、:extremely_strong），报错同 score
  def level(password)
    value = score(password)
    return :easy if value <= 3
    return :medium if value <= 6
    return :strong if value <= 9
    return :very_strong if value <= 12

    :extremely_strong
  end

  # 转成 UTF-16 码元数组；非法字节按 U+FFFD
  def utf16_units(password)
    str = password.to_s
    str = str.encode('UTF-8', invalid: :replace, undef: :replace, replace: "�") unless str.encoding == Encoding::UTF_8
    str = str.scrub("�") unless str.valid_encoding?
    str.encode('UTF-16LE').unpack('v*')
  end

  def char_type(c)
    return NUM if c >= 48 && c <= 57
    return SMALL if c >= 97 && c <= 122
    return CAPITAL if c >= 65 && c <= 90
    return INTL if c >= 128

    OTHER
  end

  def pair?(s, i, finish)
    i + 1 < finish && s[i] >= 0xD800 && s[i] <= 0xDBFF && s[i + 1] >= 0xDC00 && s[i + 1] <= 0xDFFF
  end

  def character_count(s, from, to)
    count = 0
    i = from
    while i < to
      i += pair?(s, i, to) ? 2 : 1
      count += 1
    end
    count
  end

  # 有效长度：同一字符连写只计 2，紧邻重复的片段（长 2-16）只计一次，代理对计 1
  def effective_length(s)
    n = s.length
    len = 0
    i = 0
    while i < n
      block = (1..MAX_BLOCK).find do |m|
        break nil if i + 2 * m > n

        s[i] == s[i + m] && s[i, m] == s[i + m, m]
      end
      if block
        r = 2
        r += 1 while i + (r + 1) * block <= n && s[i + r * block, block] == s[i, block]
        len += block == 1 ? 2 : character_count(s, i, i + block)
        i += r * block
      else
        i += pair?(s, i, n) ? 2 : 1
        len += 1
      end
    end
    len
  end

  def length_points(len)
    return -2 if len <= 4
    return -1 if len == 5
    return 0 if len < 8

    [(len - 8) / 4 + 1, 5].min
  end

  def same_char?(s, from, to)
    (from + 1...to).all? { |i| s[i] == s[from] }
  end

  def digits(s, start, count)
    s[start, count].reduce(0) { |v, c| v * 10 + (c - 48) }
  end

  def year?(year)
    year.between?(1950, 2049)
  end

  def valid_date?(year, month, day)
    return false unless month.between?(1, 12) && day >= 1

    leap = (year % 4).zero? && (year % 100 != 0 || (year % 400).zero?)
    day <= (month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1])
  end

  def date8?(s, k)
    head = digits(s, k, 4)
    return true if year?(head) && valid_date?(head, digits(s, k + 4, 2), digits(s, k + 6, 2))

    tail = digits(s, k + 4, 4)
    year?(tail) && (valid_date?(tail, digits(s, k + 2, 2), digits(s, k, 2)) ||
                    valid_date?(tail, digits(s, k, 2), digits(s, k + 2, 2)))
  end

  # 8 位连续数字为 yyyyMMdd、ddMMyyyy、MMddyyyy；恰好 6 位的数字串为 yyMMdd；恰好 4 位的数字串为年份
  def contains_date_or_year?(s)
    i = 0
    while i < s.length
      if char_type(s[i]) != NUM
        i += 1
        next
      end
      start = i
      i += 1 while i < s.length && char_type(s[i]) == NUM
      run = i - start
      return true if (start..i - 8).any? { |k| date8?(s, k) }
      return true if run == 6 && valid_date?(2000 + digits(s, start, 2), digits(s, start + 2, 2), digits(s, start + 4, 2))
      return true if run == 4 && year?(digits(s, start, 4))
    end
    false
  end

  private_class_method :utf16_units, :char_type, :pair?, :character_count, :effective_length, :length_points,
                       :same_char?, :digits, :year?, :valid_date?, :date8?, :contains_date_or_year?
  private_constant :DICTIONARY, :SEQUENCES, :SEQUENCE_MIN, :MAX_BLOCK, :SEQUENCE_KEYS, :LEET,
                   :DAYS_IN_MONTH, :NUM, :SMALL, :CAPITAL, :INTL, :OTHER
end
