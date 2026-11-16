// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖报错与线性耗时
use std::time::{Duration, Instant};

#[test]
fn matches_snapshot() {
    let path = concat!(env!("CARGO_MANIFEST_DIR"), "/../spec/score-snapshot.tsv");
    let data = std::fs::read_to_string(path).unwrap();
    let mut checked = 0;
    for line in data.split('\n') {
        if line.is_empty() || line.starts_with('#') {
            continue;
        }
        let cols: Vec<&str> = line.splitn(3, '\t').collect();
        let got = format!(
            "{}\t{}",
            pwscore::score(cols[2]).unwrap(),
            pwscore::level(cols[2]).unwrap()
        );
        assert_eq!(got, format!("{}\t{}", cols[0], cols[1]), "[{}]", cols[2]);
        checked += 1;
    }
    assert!(checked > 0);
}

#[test]
fn rejects_empty_password() {
    assert_eq!(pwscore::score(""), Err(pwscore::EmptyPassword));
    assert_eq!(
        pwscore::level("2hAj5#mne-ix.86H"),
        Ok(pwscore::Level::ExtremelyStrong)
    );
}

#[test]
fn linear_time() {
    let input = "a".repeat(200_000) + "b";
    let start = Instant::now();
    pwscore::score(&input).unwrap();
    assert!(start.elapsed() < Duration::from_secs(2));
}
