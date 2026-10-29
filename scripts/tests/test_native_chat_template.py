"""Input-format boundaries for the restricted official Qwen3.5 text adapter.

These check prompt bytes, not language-model quality. Actual generation is
recorded separately by evaluate_local_ai_variants.py.
"""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


class NativeChatTemplateTest(unittest.TestCase):
    @unittest.skipUnless(shutil.which("g++"), "A C++17 host compiler is required")
    def test_official_nonthinking_prefix_and_other_architecture_boundaries(self):
        source = r'''
#include "nexus_chat_template.hpp"
#include <cassert>
int main() {
  const std::string model_template = "enable_thinking <think> </think>";
  const std::string history = "<|im_start|>system\nIdentidade NEXUS<|im_end|>\n"
    "<|im_start|>user\nCrie um texto.<|im_end|>\n<|im_start|>assistant\n";
  const auto result = nexus::finish_generation_prompt("qwen35", model_template, history);
  // Official Qwen/Qwen3.5-2B template enable_thinking=false branch.
  assert(result == history + "<think>\n\n</think>\n\n");
  assert(nexus::finish_generation_prompt("qwen2", model_template, history) == history);
  assert(nexus::finish_generation_prompt("unknown", "custom template", history) == history);
  bool rejected = false;
  try { nexus::finish_generation_prompt("qwen35", "different template", history); }
  catch (const std::runtime_error &) { rejected = true; }
  assert(rejected);
  rejected = false;
  try { nexus::finish_generation_prompt("qwen35", model_template, "not a generation prefix"); }
  catch (const std::runtime_error &) { rejected = true; }
  assert(rejected);
  // Quoted tokens inside prior content remain untouched; this is not a filter.
  const std::string quoted = "<|im_start|>user\nExplique <think>texto</think>.<|im_end|>\n<|im_start|>assistant\n";
  assert(nexus::finish_generation_prompt("qwen35", model_template, quoted) == quoted + "<think>\n\n</think>\n\n");
}
'''
        root = Path(__file__).resolve().parents[2]
        with tempfile.TemporaryDirectory(prefix="nexus-chat-prefix-test-") as directory:
            cpp = Path(directory) / "test.cpp"
            binary = Path(directory) / "test"
            cpp.write_text(source, encoding="utf-8")
            subprocess.run([shutil.which("g++"), "-std=c++17", "-Wall", "-Wextra", "-Werror",
                            "-I" + str(root / "app/src/main/cpp"), str(cpp), "-o", str(binary)], check=True)
            subprocess.run([str(binary)], check=True)


if __name__ == "__main__":
    unittest.main()
