#pragma once

#include <cstdint>
#include <stdexcept>
#include <string>

namespace nexus {

class Cancelled final : public std::runtime_error {
public:
    Cancelled() : std::runtime_error("A geração local foi interrompida.") {}
};

// Handles are registry IDs, never raw pointers exposed to Java. An in-flight
// call owns the engine until completion even when another thread closes its ID.
std::uint64_t load(const std::string & path, int context_tokens, int threads);
std::string generate(std::uint64_t handle, const std::string & chat_json, int maximum_tokens);
void cancel(std::uint64_t handle) noexcept;
void close(std::uint64_t handle) noexcept;

} // namespace nexus
