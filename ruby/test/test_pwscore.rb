# frozen_string_literal: true

# 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
require 'minitest/autorun'
require_relative '../lib/pwscore'

class PwScoreTest < Minitest::Test
  SNAPSHOT = File.expand_path('../../spec/score-snapshot.tsv', __dir__)

  def test_snapshot
    checked = 0
    File.read(SNAPSHOT, encoding: 'UTF-8').split("\n").each do |line|
      next if line.empty? || line.start_with?('#')

      score, level, password = line.split("\t", 3)
      actual = "#{PwScore.score(password)}\t#{PwScore.level(password).to_s.upcase}"
      assert_equal "#{score}\t#{level}", actual, "[#{password}]"
      checked += 1
    end
    assert checked.positive?
  end

  def test_errors
    assert_raises(ArgumentError) { PwScore.score(nil) }
    assert_raises(ArgumentError) { PwScore.score('') }
    assert_equal :extremely_strong, PwScore.level('2hAj5#mne-ix.86H')
    # 非法 UTF-8 不报错
    PwScore.score("\xff\xfeAb1!xyz".dup.force_encoding('UTF-8'))
  end

  def test_linear_time
    start = Process.clock_gettime(Process::CLOCK_MONOTONIC)
    PwScore.score("#{'a' * 200_000}b")
    assert Process.clock_gettime(Process::CLOCK_MONOTONIC) - start < 2
  end
end
