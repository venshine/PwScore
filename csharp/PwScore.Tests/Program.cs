// 跨语言一致性测试（无第三方依赖）：逐行比对 spec/score-snapshot.tsv，并覆盖异常与线性耗时
using System;
using System.Diagnostics;
using System.IO;
using System.Text;
using Venshine;

int failures = 0;
void Check(bool condition, string name)
{
    if (!condition)
    {
        failures++;
        Console.WriteLine("FAIL " + name);
    }
}

string path = args.Length > 0 ? args[0] : Path.Combine("..", "spec", "score-snapshot.tsv");
int checkedCount = 0;
foreach (string line in File.ReadAllText(path, Encoding.UTF8).Split('\n'))
{
    if (line.Length == 0 || line.StartsWith("#", StringComparison.Ordinal)) continue;
    string[] cols = line.Split(new[] { '\t' }, 3);
    string expected = cols[0] + "\t" + cols[1];
    string actual = PwScore.Score(cols[2]) + "\t" + PwScore.Name(PwScore.Level(cols[2]));
    Check(expected == actual, $"snapshot [{cols[2]}]: expected {expected} but was {actual}");
    checkedCount++;
}
Check(checkedCount > 0, "snapshot has no cases");

try { PwScore.Score(null!); Check(false, "null did not throw"); } catch (ArgumentNullException) { }
try { PwScore.Score(""); Check(false, "empty did not throw"); } catch (ArgumentException e) when (e is not ArgumentNullException) { }
Check(PwScore.Level("2hAj5#mne-ix.86H") == PwLevel.ExtremelyStrong, "level");

var watch = Stopwatch.StartNew();
PwScore.Score(new string('a', 200000) + "b");
Check(watch.Elapsed < TimeSpan.FromSeconds(2), "linear time");

if (failures > 0)
{
    Console.WriteLine($"{failures} test(s) failed");
    return 1;
}
Console.WriteLine("csharp: all tests passed");
return 0;
