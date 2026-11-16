# 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
use strict;
use warnings;
use FindBin qw($Bin);
use lib "$Bin/../lib";
use Test::More;
use Time::HiRes qw(time);
use PwScore qw(score level);

open my $fh, '<:encoding(UTF-8)', "$Bin/../../spec/score-snapshot.tsv" or die $!;
my $checked = 0;
while (my $line = <$fh>) {
    chomp $line;
    next if $line eq '' || $line =~ /^#/;
    my ($score, $level, $password) = split /\t/, $line, 3;
    is(score($password) . "\t" . level($password), "$score\t$level", 'snapshot') or diag explain $password;
    $checked++;
}
close $fh;
ok($checked > 0, 'snapshot has cases');

ok(!eval { score(undef); 1 }, 'undef croaks');
ok(!eval { score(''); 1 },    'empty croaks');
is(level('2hAj5#mne-ix.86H'), 'EXTREMELY_STRONG', 'level');

my $start = time;
score('a' x 200000 . 'b');
ok(time - $start < 2, 'linear time');

done_testing();
