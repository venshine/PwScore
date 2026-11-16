// 跨语言一致性测试：逐行比对 spec/score-snapshot.tsv，并覆盖异常。参数为快照路径
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>

#include "pwscore.hpp"

int main(int argc, char **argv) {
    if (argc < 2) {
        std::cerr << "usage: " << argv[0] << " <score-snapshot.tsv>\n";
        return 2;
    }
    std::ifstream in(argv[1], std::ios::binary);
    std::string line;
    int failures = 0, checked = 0;
    while (std::getline(in, line)) {
        if (!line.empty() && line.back() == '\r') line.pop_back();
        if (line.empty() || line[0] == '#') continue;
        auto tab1 = line.find('\t');
        auto tab2 = line.find('\t', tab1 + 1);
        std::string password = line.substr(tab2 + 1);
        std::string expected = line.substr(0, tab2);
        std::string actual = std::to_string(pwscore::score(password)) + "\t" +
                             std::string(pwscore::name(pwscore::level(password)));
        if (expected != actual) {
            failures++;
            std::cout << "FAIL snapshot [" << password << "]: expected " << expected << " but was " << actual << "\n";
        }
        checked++;
    }
    if (checked == 0) failures++;

    try {
        pwscore::score("");
        failures++;
        std::cout << "FAIL empty password did not throw\n";
    } catch (const std::invalid_argument &) {
    }
    if (pwscore::level("2hAj5#mne-ix.86H") != pwscore::Level::ExtremelyStrong) failures++;

    if (failures > 0) {
        std::cout << failures << " test(s) failed\n";
        return 1;
    }
    std::cout << "cpp: all tests passed\n";
    return 0;
}
