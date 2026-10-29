#include "nexus_runtime.hpp"
#include "ggml-backend.h"
#include "nlohmann/json.hpp"

#include <chrono>
#include <fstream>
#include <iostream>
#include <stdexcept>
#include <string>
#include <sys/resource.h>

// Developer-only host evaluator. It links the unchanged production engine;
// fixtures contain prompts, never expected/canned assistant responses.
using Json = nlohmann::json;

static void save(const std::string & path, const Json & report) {
    std::ofstream out(path, std::ios::trunc);
    if (!out) throw std::runtime_error("Cannot write evaluation report");
    out << report.dump(2) << '\n';
}

static long peak_resident_kib() {
    rusage usage{};
    return getrusage(RUSAGE_SELF, &usage) == 0 ? usage.ru_maxrss : -1;
}

int main(int argc, char ** argv) {
    if (argc != 5) return 2;
    Json report = {{"schemaVersion", 1}, {"executionStatus", "starting"},
        {"scope", "Actual unchanged NEXUS C++ engine on host CPU, synthetic prompts only; not Android, A03 or a semantic quality certification."},
        {"cases", Json::array()}};
    std::uint64_t handle = 0;
    try {
        std::ifstream in(argv[2]);
        if (!in) throw std::runtime_error("Cannot read evaluation fixture");
        const auto fixture = Json::parse(in);
        const auto context = fixture.value("contextTokens", 2048);
        const auto threads = fixture.value("threads", 2);
        const auto maximum = fixture.value("maxOutputTokens", 384);
        report["contextTokens"] = context;
        report["threads"] = threads;
        report["maxOutputTokens"] = maximum;
        report["fixture"] = fixture;
        report["executionStatus"] = "running";
        save(argv[3], report);
        ggml_backend_load_all_from_path(argv[4]);
        for (const auto & variant : fixture.at("variants")) {
            handle = nexus::load(argv[1], context, threads);
            for (const auto & item : fixture.at("cases")) {
                // A fresh history per case. Prefix reuse is the same mechanism
                // as the app, and its actual counters remain in each result.
                Json chat = Json::array({{{"role", "system"}, {"content", variant.at("system")}}});
                int turn = 0;
                for (const auto & question : item.at("turns")) {
                    const std::string query = question.is_string() ? question.get<std::string>() : question.at("query").get<std::string>();
                    if (question.is_object() && question.contains("system")) {
                        chat[0]["content"] = question.at("system");
                    }
                    chat.push_back({{"role", "user"}, {"content", query}});
                    const auto started = std::chrono::steady_clock::now();
                    auto actual = Json::parse(nexus::generate_result(handle, chat.dump(), maximum));
                    actual["variant"] = variant.at("id");
                    actual["caseId"] = item.at("id");
                    actual["turn"] = ++turn;
                    actual["query"] = query;
                    actual["milliseconds"] = std::chrono::duration_cast<std::chrono::milliseconds>(
                        std::chrono::steady_clock::now() - started).count();
                    actual["hostPeakResidentKiB"] = peak_resident_kib();
                    chat.push_back({{"role", "assistant"}, {"content", actual.at("text")}});
                    report["cases"].push_back(actual);
                    save(argv[3], report);
                    std::cout << variant.at("id") << " " << item.at("id") << " " << turn
                              << " " << actual.at("finishReason") << " " << actual.at("milliseconds") << "ms\n" << std::flush;
                }
            }
            nexus::close(handle);
            handle = 0;
        }
        report["executionStatus"] = "completed";
        report["hostPeakResidentKiB"] = peak_resident_kib();
        save(argv[3], report);
        return 0;
    } catch (const std::exception & error) {
        if (handle != 0) nexus::close(handle);
        report["executionStatus"] = "failed";
        report["failure"] = error.what();
        try { save(argv[3], report); } catch (...) {}
        std::cerr << "Host evaluation failed; see its synthetic report.\n";
        return 1;
    }
}
