#pragma once

#include <stdexcept>
#include <string>

namespace nexus {

// llama_chat_apply_template is a legacy format recognizer, not a Jinja engine.
// For the text-only qwen35 path, restore the official enable_thinking=false
// generation prefix missing from its ChatML expansion. This changes the input
// before inference; generated words/reasoning are never deleted or substituted.
// Reference: Qwen/Qwen3.5-2B, revision 15852e8c16360a2fea060d615a32b45270f8a8fc,
// chat_template.jinja, final add_generation_prompt / enable_thinking branch.
inline std::string finish_generation_prompt(
    const std::string & architecture, const std::string & model_template,
    std::string prompt
) {
    if (architecture != "qwen35") return prompt;
    const std::string assistant_prefix = "<|im_start|>assistant\n";
    if (model_template.find("enable_thinking") == std::string::npos ||
        model_template.find("<think>") == std::string::npos ||
        model_template.find("</think>") == std::string::npos ||
        prompt.size() < assistant_prefix.size() ||
        prompt.compare(prompt.size() - assistant_prefix.size(), assistant_prefix.size(), assistant_prefix) != 0) {
        throw std::runtime_error("O template deste modelo Qwen3.5 não é compatível com o modo local sem raciocínio interno.");
    }
    prompt.append("<think>\n\n</think>\n\n");
    return prompt;
}

} // namespace nexus
