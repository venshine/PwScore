# frozen_string_literal: true

# PwScore：密码强度评分，规则见仓库 spec/SPEC.md。长度按 UTF-16 码元计。
#
# Licensed under the Apache License, Version 2.0
module PwScore
  VERSION = '3.0.0'

  # 强度等级，按分值从低到高
  LEVELS = %i[easy medium strong very_strong extremely_strong].freeze

  DICTIONARY = %w[password abc123 iloveyou adobe123 123123 sunshine
                  1314520 a1b2c3 123qwe aaa111 qweasd admin passwd].freeze

  SEQUENCES = [
    %w[abcdefghijklmnopqrstuvwxyz zyxwvutsrqponmlkjihgfedcba],
    %w[01234567890 09876543210],
    %w[qwertyuiop poiuytrewq asdfghjkl lkjhgfdsa zxcvbnm mnbvcxz]
  ].freeze

  SEQUENCE_MIN = 4

  # 每组序列所有长度为 4 的片段，按码元数组存放
  SEQUENCE_KEYS = SEQUENCES.map do |group|
    group.flat_map { |seq| seq.bytes.each_cons(SEQUENCE_MIN).to_a }.to_h { |k| [k, true] }.freeze
  end.freeze

  DICTIONARY_UNITS = DICTIONARY.map(&:bytes).freeze

  DAYS_IN_MONTH = [31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31].freeze

  NUM = 0
  SMALL = 1
  CAPITAL = 2
  OTHER = 3

  module_function

  # 计算密码强度分值，0 到 14。nil 或空串抛 ArgumentError
  def score(password)
    raise ArgumentError, 'password is nil' if password.nil?
    raise ArgumentError, 'password is empty' if password.empty?

    units = utf16_units(password)
    # 整串由更短片段重复组成时，按重复单元评分
    s = units[0, repeat_unit_length(units)]
    len = s.length

    count = [0, 0, 0, 0]
    s.each { |c| count[char_type(c)] += 1 }
    num, small, capital, other = count
    classes = count.count(&:positive?)

    level = 0
    # 加分
    gains = [
      num > 0,
      small > 0,
      len > 4 && capital > 0,
      len > 6 && other > 0,
      len > 4 && classes >= 2,
      len > 6 && classes >= 3,
      len > 8 && classes == 4,
      len > 6 && (num >= 3 && small >= 3 || num >= 3 && capital >= 3 || num >= 3 && other >= 2 ||
                  small >= 3 && capital >= 3 || small >= 3 && other >= 2 || capital >= 3 && other >= 2),
      len > 8 && (num >= 2 && small >= 2 && capital >= 2 || num >= 2 && small >= 2 && other >= 2 ||
                  num >= 2 && capital >= 2 && other >= 2 || small >= 2 && capital >= 2 && other >= 2),
      len > 10 && num >= 2 && small >= 2 && capital >= 2 && other >= 2,
      other >= 3,
      other >= 6,
      len > 12,
      len >= 16
    ]
    level += gains.count(true)

    # 减分；只把 A-Z 转小写
    lower = s.map { |c| c >= 65 && c <= 90 ? c + 32 : c }
    SEQUENCE_KEYS.each do |keys|
      level -= 1 if lower.each_cons(SEQUENCE_MIN).any? { |w| keys.key?(w) }
    end
    level -= 1 if num == len || small == len || capital == len
    level -= 1 if len.even? && same_char?(s, 0, len / 2) && same_char?(s, len / 2, len)
    level -= 1 if contains_date?(s)
    level -= 1 if DICTIONARY_UNITS.any? { |w| contains?(lower, w) || contains?(w, lower) }
    level -= 1 if len <= 6
    level -= 1 if len <= 4
    level = 0 if len <= 3 || same_char?(s, 0, len)
    [level, 0].max
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

    OTHER
  end

  def repeat_unit_length(s)
    n = s.length
    prefix = Array.new(n, 0)
    (1...n).each do |i|
      k = prefix[i - 1]
      k = prefix[k - 1] while k > 0 && s[i] != s[k]
      k += 1 if s[i] == s[k]
      prefix[i] = k
    end
    period = n - prefix[n - 1]
    (n % period).zero? ? period : n
  end

  def same_char?(s, from, to)
    (from + 1...to).all? { |i| s[i] == s[from] }
  end

  def contains?(haystack, needle)
    return false if needle.length > haystack.length

    (0..haystack.length - needle.length).any? { |i| haystack[i, needle.length] == needle }
  end

  def contains_date?(s)
    run = 0
    s.each_index do |i|
      run = char_type(s[i]) == NUM ? run + 1 : 0
      next if run < 8

      d = s[i - 7, 8].map { |c| c - 48 }
      year = d[0] * 1000 + d[1] * 100 + d[2] * 10 + d[3]
      month = d[4] * 10 + d[5]
      day = d[6] * 10 + d[7]
      return true if year.between?(1950, 2049) && month.between?(1, 12) && day >= 1 && day <= days_in_month(year, month)
    end
    false
  end

  def days_in_month(year, month)
    leap = (year % 4).zero? && (year % 100 != 0 || (year % 400).zero?)
    month == 2 && leap ? 29 : DAYS_IN_MONTH[month - 1]
  end

  private_class_method :utf16_units, :char_type, :repeat_unit_length, :same_char?, :contains?,
                       :contains_date?, :days_in_month
  private_constant :DICTIONARY, :SEQUENCES, :SEQUENCE_MIN, :SEQUENCE_KEYS, :DICTIONARY_UNITS, :DAYS_IN_MONTH,
                   :NUM, :SMALL, :CAPITAL, :OTHER
end
