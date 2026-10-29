#include "nexus_runtime.hpp"
#include "nexus_chat_template.hpp"

#include "llama.h"
#include "nlohmann/json.hpp"

#include <algorithm>
#include <atomic>
#include <climits>
#include <chrono>
#include <fstream>
#include <memory>
#include <mutex>
#include <unordered_map>
#include <utility>
#include <vector>

namespace nexus {
namespace {

constexpr std::size_t maximum_json_bytes = 1024 * 1024;
constexpr std::size_t maximum_answer_bytes = 128 * 1024;

struct Engine {
    llama_model * model = nullptr;
    llama_context * context = nullptr;
    const llama_vocab * vocabulary = nullptr;
    std::mutex generation_mutex;
    std::atomic<bool> cancel_requested{false};
    std::atomic<bool> closed{false};
    std::atomic<std::uint64_t> cancellation_epoch{0};
    int context_tokens = 1024;
    std::string architecture;
    std::vector<llama_token> cached_tokens;
    std::uint64_t cache_epoch = 0;

    ~Engine() {
        if (context != nullptr) llama_free(context);
        if (model != nullptr) llama_model_free(model);
    }
};

std::mutex registry_mutex;
std::unordered_map<std::uint64_t, std::shared_ptr<Engine>> registry;
std::atomic<std::uint64_t> next_handle{1};
std::once_flag backend_initialized;

bool abort_generation(void * user_data) {
    const auto * engine = static_cast<const Engine *>(user_data);
    return engine->cancel_requested.load(std::memory_order_relaxed) || engine->closed.load(std::memory_order_relaxed);
}

void check_cancelled(const Engine & engine) {
    if (abort_generation(const_cast<Engine *>(&engine))) throw Cancelled();
}

std::shared_ptr<Engine> lookup(std::uint64_t handle) {
    std::lock_guard<std::mutex> lock(registry_mutex);
    auto found = registry.find(handle);
    return found == registry.end() ? nullptr : found->second;
}

struct Message {
    std::string role;
    std::string content;
};

std::vector<Message> parse_messages(const std::string & payload) {
    if (payload.empty() || payload.size() > maximum_json_bytes) {
        throw std::invalid_argument("O histórico ultrapassa o limite de entrada da IA local.");
    }
    nlohmann::json document;
    try {
        document = nlohmann::json::parse(payload);
    } catch (const nlohmann::json::exception &) {
        // JSON parser messages can contain user text: never forward them.
        throw std::invalid_argument("O histórico local está em um formato inválido.");
    }
    if (!document.is_array() || document.empty() || document.size() > 256) {
        throw std::invalid_argument("Envie entre uma e 256 mensagens para a IA local.");
    }
    std::vector<Message> messages;
    messages.reserve(document.size());
    bool has_user = false;
    for (const auto & item : document) {
        if (!item.is_object() || !item.contains("role") || !item["role"].is_string() ||
            !item.contains("content") || !item["content"].is_string()) {
            throw std::invalid_argument("A IA local aceita somente mensagens de texto com papel e conteúdo.");
        }
        auto role = item["role"].get<std::string>();
        auto content = item["content"].get<std::string>();
        if (role != "system" && role != "user" && role != "assistant") {
            throw std::invalid_argument("O histórico contém um papel não suportado pela IA local.");
        }
        if (content.find('\0') != std::string::npos) {
            throw std::invalid_argument("O histórico contém um caractere de controle inválido.");
        }
        if (role == "system" && !messages.empty()) {
            throw std::invalid_argument("A instrução de sistema deve aparecer somente no início da conversa.");
        }
        has_user = has_user || role == "user";
        messages.push_back({std::move(role), std::move(content)});
    }
    if (!has_user || messages.back().role != "user") {
        throw std::invalid_argument("A conversa local deve terminar com uma mensagem do usuário.");
    }
    return messages;
}

std::string format_prompt(const Engine & engine, const std::vector<Message> & messages) {
    const char * chat_template = llama_model_chat_template(engine.model, nullptr);
    if (chat_template == nullptr) {
        throw std::runtime_error("O modelo local não possui um template de conversa compatível.");
    }
    std::vector<llama_chat_message> chat;
    chat.reserve(messages.size());
    for (const auto & message : messages) chat.push_back({message.role.c_str(), message.content.c_str()});
    std::vector<char> output(4096);
    auto required = llama_chat_apply_template(chat_template, chat.data(), chat.size(), true,
                                              output.data(), static_cast<int32_t>(output.size()));
    if (required < 0 || required > static_cast<int32_t>(maximum_json_bytes * 2)) {
        throw std::runtime_error("O formato de conversa deste modelo local não é suportado.");
    }
    if (required > static_cast<int32_t>(output.size())) {
        output.resize(static_cast<std::size_t>(required));
        auto written = llama_chat_apply_template(chat_template, chat.data(), chat.size(), true,
                                                 output.data(), static_cast<int32_t>(output.size()));
        if (written < 0 || written > static_cast<int32_t>(output.size())) {
            throw std::runtime_error("Não foi possível preparar o contexto da IA local.");
        }
        required = written;
    }
    return finish_generation_prompt(engine.architecture, chat_template,
        std::string(output.data(), static_cast<std::size_t>(required)));
}

std::vector<llama_token> tokenize(const Engine & engine, const std::string & prompt) {
    if (prompt.size() > static_cast<std::size_t>(INT32_MAX)) throw std::invalid_argument("Texto local longo demais.");
    int32_t required = llama_tokenize(engine.vocabulary, prompt.data(), static_cast<int32_t>(prompt.size()),
                                      nullptr, 0, true, true);
    if (required == INT32_MIN) throw std::invalid_argument("Texto local longo demais.");
    if (required >= 0) throw std::runtime_error("O modelo não conseguiu preparar a mensagem.");
    std::vector<llama_token> tokens(static_cast<std::size_t>(-required));
    const auto written = llama_tokenize(engine.vocabulary, prompt.data(), static_cast<int32_t>(prompt.size()),
                                        tokens.data(), static_cast<int32_t>(tokens.size()), true, true);
    if (written <= 0) throw std::runtime_error("O modelo não conseguiu preparar a mensagem.");
    tokens.resize(static_cast<std::size_t>(written));
    return tokens;
}

struct PromptContext {
    std::vector<llama_token> tokens;
    int input_messages = 0;
    int used_messages = 0;
};

PromptContext fit_context(const Engine & engine, std::vector<Message> messages, int output_tokens) {
    const auto input_messages = static_cast<int>(messages.size());
    const auto maximum_prompt = static_cast<std::size_t>(engine.context_tokens - output_tokens);
    for (;;) {
        check_cancelled(engine);
        auto tokens = tokenize(engine, format_prompt(engine, messages));
        if (tokens.size() <= maximum_prompt) return {std::move(tokens), input_messages, static_cast<int>(messages.size())};
        const std::size_t first_turn = messages.front().role == "system" ? 1 : 0;
        if (messages.size() <= first_turn + 1) {
            throw std::invalid_argument("A mensagem e as memórias excedem o contexto da IA local. Divida o texto ou reduza as memórias.");
        }
        // Keep the system and most recent user request. Drop complete old turns
        // rather than silently removing bytes from the current user message.
        auto end = first_turn + 1;
        if (end < messages.size() - 1 && messages[end].role == "assistant") ++end;
        messages.erase(messages.begin() + static_cast<std::ptrdiff_t>(first_turn),
                       messages.begin() + static_cast<std::ptrdiff_t>(end));
    }
}

void decode(Engine & engine, std::vector<llama_token> & tokens, std::size_t start, std::size_t count) {
    check_cancelled(engine);
    auto batch = llama_batch_get_one(tokens.data() + start, static_cast<int32_t>(count));
    const auto status = llama_decode(engine.context, batch);
    check_cancelled(engine);
    if (status != 0) throw std::runtime_error("A IA local não conseguiu processar o contexto. Feche outros aplicativos e tente novamente.");
}

std::string token_piece(const Engine & engine, llama_token token) {
    std::vector<char> output(256);
    auto count = llama_token_to_piece(engine.vocabulary, token, output.data(), static_cast<int32_t>(output.size()), 0, false);
    if (count < 0 && count != INT32_MIN && -count <= 32768) {
        output.resize(static_cast<std::size_t>(-count));
        count = llama_token_to_piece(engine.vocabulary, token, output.data(), static_cast<int32_t>(output.size()), 0, false);
    }
    if (count < 0) throw std::runtime_error("A IA local não conseguiu converter a resposta em texto.");
    return std::string(output.data(), static_cast<std::size_t>(count));
}

struct ClearContextOnFailure {
    Engine & engine;
    bool completed = false;
    ~ClearContextOnFailure() {
        if (!completed) {
            llama_memory_clear(llama_get_memory(engine.context), true);
            engine.cached_tokens.clear();
        }
    }
};

void trim_incomplete_utf8_tail(std::string & text) {
    if (text.empty()) return;
    std::size_t lead = text.size() - 1;
    while (lead > 0 && (static_cast<unsigned char>(text[lead]) & 0xc0U) == 0x80U) --lead;
    const auto byte = static_cast<unsigned char>(text[lead]);
    const std::size_t width = byte < 0x80U ? 1 : byte < 0xe0U ? 2 : byte < 0xf0U ? 3 : 4;
    if (text.size() - lead < width) text.resize(lead);
}

} // namespace

std::uint64_t load(const std::string & path, int context_tokens, int threads) {
    if (path.empty() || path.find('\0') != std::string::npos) throw std::invalid_argument("O caminho do modelo local é inválido.");
    if (context_tokens < 512 || context_tokens > 4096 || threads < 1 || threads > 4) {
        throw std::invalid_argument("Use contexto entre 512 e 4096 tokens e uma a quatro threads.");
    }
    std::ifstream file(path, std::ios::binary);
    char magic[4]{};
    if (!file.read(magic, sizeof(magic)) || std::string(magic, sizeof(magic)) != "GGUF") {
        throw std::invalid_argument("O arquivo local não é um modelo GGUF válido. Baixe o modelo novamente.");
    }
    std::call_once(backend_initialized, [] {
        // Model internals and file paths are not sent to Android logcat. User
        // prompts and output are never logged by this bridge.
        llama_log_set([](ggml_log_level, const char *, void *) {}, nullptr);
        llama_backend_init();
    });
    auto engine = std::make_shared<Engine>();
    auto model_parameters = llama_model_default_params();
    model_parameters.n_gpu_layers = 0;
    model_parameters.load_mode = LLAMA_LOAD_MODE_MMAP;
    engine->model = llama_model_load_from_file(path.c_str(), model_parameters);
    if (engine->model == nullptr) throw std::runtime_error("Não foi possível carregar a IA local. Verifique o modelo e a memória livre do aparelho.");
    char architecture[128]{};
    const auto architecture_size = llama_model_meta_val_str(engine->model, "general.architecture", architecture, sizeof(architecture));
    if (architecture_size > 0 && architecture_size < static_cast<int32_t>(sizeof(architecture))) {
        engine->architecture.assign(architecture, static_cast<std::size_t>(architecture_size));
    }
    if (!llama_model_has_decoder(engine->model) || llama_model_has_encoder(engine->model)) {
        throw std::invalid_argument("Esta versão local aceita modelos GGUF de conversa com decoder de texto.");
    }
    engine->vocabulary = llama_model_get_vocab(engine->model);
    if (engine->vocabulary == nullptr) throw std::runtime_error("O modelo não possui vocabulário compatível.");
    auto parameters = llama_context_default_params();
    parameters.n_ctx = static_cast<std::uint32_t>(context_tokens);
    parameters.n_batch = 128;
    parameters.n_ubatch = 64;
    // Only the final token's logits are requested for one conversation. Avoid
    // reserving a vocabulary-sized output buffer for every prompt batch token.
    parameters.n_outputs_max = 1;
    parameters.n_outputs_max_per_seq = 1;
    parameters.n_threads = threads;
    parameters.n_threads_batch = threads;
    parameters.n_seq_max = 1;
    parameters.offload_kqv = false;
    parameters.op_offload = false;
    parameters.no_perf = true;
    parameters.abort_callback = abort_generation;
    parameters.abort_callback_data = engine.get();
    engine->context = llama_init_from_model(engine->model, parameters);
    if (engine->context == nullptr) throw std::runtime_error("Não foi possível reservar memória para a IA local. Feche outros aplicativos e tente novamente.");
    if (llama_get_memory(engine->context) == nullptr) throw std::runtime_error("O modelo local não possui contexto de texto compatível.");
    engine->context_tokens = static_cast<int>(llama_n_ctx(engine->context));
    const auto id = next_handle.fetch_add(1, std::memory_order_relaxed);
    std::lock_guard<std::mutex> lock(registry_mutex);
    registry.emplace(id, engine);
    return id;
}

namespace {

struct Generation {
    std::string text;
    std::string finish_reason = "token_limit";
    int prompt_tokens = 0;
    int generated_tokens = 0;
    int input_messages = 0;
    int used_messages = 0;
    int context_tokens = 0;
    int maximum_tokens = 0;
    int reused_prompt_tokens = 0;
    std::int64_t first_token_ms = -1;
    std::int64_t total_ms = 0;
};

Generation generate_internal(std::uint64_t handle, const std::string & chat_json, int maximum_tokens) {
    const auto started = std::chrono::steady_clock::now();
    auto engine = lookup(handle);
    if (!engine) throw std::runtime_error("A sessão da IA local foi encerrada. Inicie uma nova conversa.");
    if (maximum_tokens < 1 || maximum_tokens > 1024 || maximum_tokens >= engine->context_tokens - 64) {
        throw std::invalid_argument("O limite de resposta local deve ficar entre um e 1024 tokens e caber no contexto.");
    }
    const auto epoch = engine->cancellation_epoch.load(std::memory_order_relaxed);
    std::unique_lock<std::mutex> lock(engine->generation_mutex);
    engine->cancel_requested.store(false, std::memory_order_relaxed);
    if (engine->closed.load(std::memory_order_relaxed) || engine->cancellation_epoch.load(std::memory_order_relaxed) != epoch) throw Cancelled();
    ClearContextOnFailure cleanup{*engine};
    auto prompt = fit_context(*engine, parse_messages(chat_json), maximum_tokens);
    auto & tokens = prompt.tokens;
    auto memory = llama_get_memory(engine->context);
    // Partial-prefix reuse requires a complete contiguous causal prefix. SWA,
    // recurrent and other memory implementations may not retain it; fall back
    // to a cold prefill instead of trusting only our token bookkeeping.
    const auto position_min = llama_memory_seq_pos_min(memory, 0);
    const auto position_max = llama_memory_seq_pos_max(memory, 0);
    const bool cache_matches_memory = engine->cached_tokens.empty()
        ? position_max == -1
        : position_min == 0 && position_max >= 0 &&
          static_cast<std::size_t>(position_max) + 1 == engine->cached_tokens.size();
    if (engine->cache_epoch != epoch || !cache_matches_memory) {
        llama_memory_clear(memory, true);
        engine->cached_tokens.clear();
        engine->cache_epoch = epoch;
    }
    std::size_t reused = 0;
    // Re-decode at least the final input token: a sequence removal does not
    // restore logits for the prefix. Compare actual tokens, not message count.
    const auto maximum_reuse = std::min(engine->cached_tokens.size(), tokens.size() - 1);
    while (reused < maximum_reuse && engine->cached_tokens[reused] == tokens[reused]) ++reused;
    if (reused == 0 || !llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(reused), -1)) {
        llama_memory_clear(memory, true);
        reused = 0;
    }
    engine->cached_tokens.resize(reused);
    for (std::size_t start = reused; start < tokens.size(); start += 128) {
        auto count = std::min<std::size_t>(128, tokens.size() - start);
        decode(*engine, tokens, start, count);
        engine->cached_tokens.insert(engine->cached_tokens.end(), tokens.begin() + start, tokens.begin() + start + count);
    }
    using Sampler = std::unique_ptr<llama_sampler, decltype(&llama_sampler_free)>;
    auto sampling_parameters = llama_sampler_chain_default_params();
    sampling_parameters.no_perf = true;
    Sampler sampler(llama_sampler_chain_init(sampling_parameters), llama_sampler_free);
    if (!sampler) throw std::runtime_error("Não foi possível iniciar a geração local.");
    // A small candidate set bounds the cost of penalties over Qwen's large
    // vocabulary. Penalties observe the generated tokens accepted by sample().
    // Never penalize the user's instructions or all history as repeated output.
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_k(40));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_penalties(
        llama_vocab_n_tokens(engine->vocabulary), 128, 1.08f, 0.0f, 0.0f));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_top_p(0.9f, 1));
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_temp(0.65f));
    // A recorded fixed seed makes regression comparisons reproducible. Sampling
    // still depends on the actual context and is not a table of ready responses.
    llama_sampler_chain_add(sampler.get(), llama_sampler_init_dist(42));
    Generation result;
    result.prompt_tokens = static_cast<int>(tokens.size());
    result.reused_prompt_tokens = static_cast<int>(reused);
    result.input_messages = prompt.input_messages;
    result.used_messages = prompt.used_messages;
    result.context_tokens = engine->context_tokens;
    result.maximum_tokens = maximum_tokens;
    for (int generated = 0; generated < maximum_tokens; ++generated) {
        check_cancelled(*engine);
        const auto token = llama_sampler_sample(sampler.get(), engine->context, -1);
        if (llama_vocab_is_eog(engine->vocabulary, token)) {
            result.finish_reason = "end_of_turn";
            break;
        }
        result.text.append(token_piece(*engine, token));
        if (result.first_token_ms < 0) {
            result.first_token_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                std::chrono::steady_clock::now() - started).count();
        }
        ++result.generated_tokens;
        if (result.text.size() > maximum_answer_bytes) throw std::runtime_error("A resposta local ultrapassou o limite de segurança.");
        if (generated + 1 < maximum_tokens) {
            std::vector<llama_token> next{token};
            decode(*engine, next, 0, 1);
            engine->cached_tokens.push_back(token);
        }
    }
    check_cancelled(*engine);
    // Repair only an incomplete final UTF-8 token at a real token limit. This
    // does not remove repeated words, paragraphs or change generated content.
    trim_incomplete_utf8_tail(result.text);
    if (result.text.empty()) throw std::runtime_error("O modelo local não produziu texto. Tente uma pergunta mais curta.");
    result.total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(
        std::chrono::steady_clock::now() - started).count();
    cleanup.completed = true;
    return result;
}

} // namespace

std::string generate(std::uint64_t handle, const std::string & chat_json, int maximum_tokens) {
    return generate_internal(handle, chat_json, maximum_tokens).text;
}

std::string generate_result(std::uint64_t handle, const std::string & chat_json, int maximum_tokens) {
    const auto result = generate_internal(handle, chat_json, maximum_tokens);
    return nlohmann::json({
        {"text", result.text}, {"finishReason", result.finish_reason},
        {"promptTokens", result.prompt_tokens}, {"generatedTokens", result.generated_tokens},
        {"cachedPromptTokens", result.reused_prompt_tokens},
        {"evaluatedPromptTokens", result.prompt_tokens - result.reused_prompt_tokens},
        {"inputMessages", result.input_messages}, {"usedMessages", result.used_messages},
        {"droppedMessages", result.input_messages - result.used_messages},
        {"contextTokens", result.context_tokens}, {"maxOutputTokens", result.maximum_tokens},
        {"firstTokenMs", result.first_token_ms}, {"totalMs", result.total_ms},
        {"sampling", {{"temperature", 0.65}, {"topP", 0.9}, {"topK", 40},
                      {"repeatPenalty", 1.08}, {"repeatLastTokens", 128}, {"seed", 42}}}
    }).dump();
}

void cancel(std::uint64_t handle) noexcept {
    auto engine = lookup(handle);
    if (engine) {
        engine->cancellation_epoch.fetch_add(1, std::memory_order_relaxed);
        engine->cancel_requested.store(true, std::memory_order_relaxed);
    }
}

void close(std::uint64_t handle) noexcept {
    std::shared_ptr<Engine> engine;
    {
        std::lock_guard<std::mutex> lock(registry_mutex);
        auto found = registry.find(handle);
        if (found == registry.end()) return;
        engine = found->second;
        registry.erase(found);
    }
    engine->closed.store(true, std::memory_order_relaxed);
    engine->cancel_requested.store(true, std::memory_order_relaxed);
    engine->cancellation_epoch.fetch_add(1, std::memory_order_relaxed);
    // No generation mutex here: cancellation cannot wait behind inference.
    // The final shared_ptr frees context and model after active calls exit.
}

} // namespace nexus
