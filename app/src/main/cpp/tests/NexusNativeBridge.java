package br.com.nexus.assistant.data.local;

import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Host JNI verification only; this Java class is outside Android source sets. */
public final class NexusNativeBridge {
    native long nativeLoad(byte[] pathUtf8, int contextTokens, int threads);
    native byte[] nativeGenerate(long handle, byte[] chatJsonUtf8, int maxTokens);
    native byte[] nativeGenerateResult(long handle, byte[] chatJsonUtf8, int maxTokens);
    native void nativeCancel(long handle);
    native void nativeClose(long handle);

    private static String quote(String text) {
        StringBuilder result = new StringBuilder("\"");
        for (int index = 0; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '"' || value == '\\') result.append('\\').append(value);
            else if (value == '\n') result.append("\\n");
            else if (value == '\r') result.append("\\r");
            else if (value == '\t') result.append("\\t");
            else if (value < 32) result.append(String.format("\\u%04x", (int) value));
            else result.append(value);
        }
        return result.append('"').toString();
    }

    private static byte[] messages(String... rolesAndContents) {
        StringBuilder json = new StringBuilder("[");
        for (int index = 0; index < rolesAndContents.length; index += 2) {
            if (index > 0) json.append(',');
            json.append("{\"role\":").append(quote(rolesAndContents[index]));
            json.append(",\"content\":").append(quote(rolesAndContents[index + 1])).append('}');
        }
        return json.append(']').toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String decode(byte[] output) throws Exception {
        String result = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(output)).toString();
        if (result.trim().isEmpty()) throw new AssertionError("O modelo devolveu resposta vazia.");
        return result;
    }

    private static String run(NexusNativeBridge bridge, long handle, String label, byte[] history, int maximum) throws Exception {
        long start = System.nanoTime();
        String answer = decode(bridge.nativeGenerate(handle, history, maximum));
        System.out.println(label + "_answer=" + quote(answer));
        System.out.println(label + "_seconds=" + ((System.nanoTime() - start) / 1_000_000_000.0));
        return answer;
    }

    private static int metadataInteger(String json, String field) {
        Matcher match = Pattern.compile("\"" + field + "\":(\\d+)").matcher(json);
        if (!match.find()) throw new AssertionError("Metadado ausente: " + field);
        return Integer.parseInt(match.group(1));
    }

    private static void prefixCacheRegression(NexusNativeBridge bridge, long handle) throws Exception {
        // Synthetic requests only. This checks cache state and exact retries,
        // not whether the small language model is semantically competent.
        byte[] request = messages("system", "Reply only with a number.", "user", "What is 17 plus 25?");
        String cold = decode(bridge.nativeGenerate(handle, request, 24));
        String warm = decode(bridge.nativeGenerate(handle, request, 24));
        if (!cold.equals(warm)) throw new AssertionError("Cache alterou a resposta do retry determinístico.");
        String result = decode(bridge.nativeGenerateResult(handle, request, 24));
        int cached = metadataInteger(result, "cachedPromptTokens");
        int evaluated = metadataInteger(result, "evaluatedPromptTokens");
        if (cached <= 0 || evaluated < 1 || cached + evaluated != metadataInteger(result, "promptTokens")) {
            throw new AssertionError("Contadores do cache de prefixo inconsistentes.");
        }
        bridge.nativeCancel(handle);
        String afterCancel = decode(bridge.nativeGenerateResult(handle, request, 24));
        if (metadataInteger(afterCancel, "cachedPromptTokens") != 0) {
            throw new AssertionError("Cancelamento ocioso manteve o cache anterior.");
        }
        System.out.println("prefix_cache_retry_byte_equal=true");
        System.out.println("prefix_cache_reused_tokens=" + cached);
        System.out.println("prefix_cache_evaluated_tokens=" + evaluated);
        System.out.println("prefix_cache_idle_cancel_resets=true");
    }

    private static void quality(NexusNativeBridge bridge, long handle) throws Exception {
        // Exact default LocalChatContext.build system with pt-BR, Amigável,
        // Equilibrado, empty userName and no authorized memories (23/10/2026).
        String system = "Você é NEXUS. Responda em pt-BR, estilo Amigável, detalhe Equilibrado. "
            + "Admita limites. Sem visão, internet ou execução de ações. Contexto curto; não invente o passado. "
            + "Para sugerir abrir configurações, opcionalmente responda JSON: {\"nexus_action\":{\"name\":\"open_settings\",\"arguments\":{}},\"message\":\"Deseja abrir as configurações?\"}. Ação exige confirmação.";
        System.out.println("quality_system=" + quote(system));
        System.out.println("quality_context_tokens=1024");
        System.out.println("quality_threads=2");
        System.out.println("quality_max_tokens=192");
        String photosynthesis = "Explique fotossíntese em duas frases.";
        System.out.println("quality_fotossintese_prompt=" + quote(photosynthesis));
        run(bridge, handle, "quality_fotossintese", messages("system", system, "user", photosynthesis), 192);
        String kotlin = "Dê exemplo de função Kotlin que soma dois inteiros.";
        System.out.println("quality_kotlin_prompt=" + quote(kotlin));
        run(bridge, handle, "quality_kotlin", messages("system", system, "user", kotlin), 192);
        String context = "Meu objeto preferido é um cubo ciano. Lembre apenas nesta conversa.";
        System.out.println("quality_contexto_registro_prompt=" + quote(context));
        String response = run(bridge, handle, "quality_contexto_registro", messages("system", system, "user", context), 192);
        String followUp = "Qual é a cor do meu objeto preferido? Responda em uma palavra.";
        System.out.println("quality_contexto_pergunta_prompt=" + quote(followUp));
        run(bridge, handle, "quality_contexto_pergunta", messages("system", system, "user", context,
            "assistant", response, "user", followUp), 192);
        System.out.println("quality_result=observations_only_no_semantic_assertions");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3 || (args.length == 3 && !args[2].equals("--quality"))) {
            throw new IllegalArgumentException("Arguments: /absolute/libnexus_llama.so /absolute/model.gguf [--quality]");
        }
        System.load(args[0]);
        NexusNativeBridge bridge = new NexusNativeBridge();
        long handle = bridge.nativeLoad(args[1].getBytes(StandardCharsets.UTF_8), 1024, 2);
        if (handle == 0) throw new AssertionError("Handle não criado.");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            if (args.length == 3) { quality(bridge, handle); return; }
            prefixCacheRegression(bridge, handle);
            String system = "Você é NEXUS. Responda em português brasileiro e seja breve.";
            run(bridge, handle, "calculo", messages("system", system, "user", "Quanto é 17 mais 25? Responda somente com o número."), 64);
            String context = "Meu objeto preferido é um cubo ciano. Lembre apenas nesta conversa.";
            String acknowledgment = run(bridge, handle, "contexto_registro", messages("system", system, "user", context), 64);
            run(bridge, handle, "contexto_pergunta", messages("system", system, "user", context,
                "assistant", acknowledgment, "user", "Qual é a cor do meu objeto preferido? Responda em uma palavra."), 32);
            run(bridge, handle, "unicode", messages("system", system, "user", "Repita exatamente: Olá, coração! 🚀"), 48);
            try {
                bridge.nativeGenerate(handle, "[{]".getBytes(StandardCharsets.UTF_8), 64);
                throw new AssertionError("JSON inválido foi aceito.");
            } catch (IllegalArgumentException expected) {
                System.out.println("invalid_json_rejected=true");
            }
            try {
                bridge.nativeGenerate(handle, messages("system", "S", "user", "Olá"), 9000);
                throw new AssertionError("Limite de geração inválido foi aceito.");
            } catch (IllegalArgumentException expected) {
                System.out.println("invalid_token_limit_rejected=true");
            }
            CountDownLatch entered = new CountDownLatch(1);
            final long activeHandle = handle;
            Future<?> generation = executor.submit(() -> {
                entered.countDown();
                bridge.nativeGenerate(activeHandle, messages("system", system,
                    "user", "Escreva os números de 1 a 300 separados por espaços, sem explicações."), 512);
            });
            if (!entered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker não iniciou.");
            Thread.sleep(100);
            long cancellationStart = System.nanoTime();
            bridge.nativeCancel(handle);
            try {
                generation.get(15, TimeUnit.SECONDS);
                System.out.println("cancel_result=completed_before_cancel");
            } catch (ExecutionException expected) {
                if (!(expected.getCause() instanceof CancellationException)) throw expected;
                System.out.println("cancel_result=CancellationException");
            }
            System.out.println("cancel_seconds=" + ((System.nanoTime() - cancellationStart) / 1_000_000_000.0));
            run(bridge, handle, "apos_cancelamento", messages("system", system, "user", "Diga apenas olá."), 32);
            CountDownLatch closingEntered = new CountDownLatch(1);
            Future<?> closingGeneration = executor.submit(() -> {
                closingEntered.countDown();
                bridge.nativeGenerate(activeHandle, messages("system", system,
                    "user", "Conte de 1 a 300 em ordem sem pular nenhum número."), 512);
            });
            if (!closingEntered.await(5, TimeUnit.SECONDS)) throw new AssertionError("Worker de encerramento não iniciou.");
            Thread.sleep(100);
            long closeStart = System.nanoTime();
            bridge.nativeClose(handle);
            try {
                closingGeneration.get(15, TimeUnit.SECONDS);
                System.out.println("concurrent_close_result=completed_before_close");
            } catch (ExecutionException expected) {
                if (!(expected.getCause() instanceof CancellationException)
                        && !(expected.getCause() instanceof IllegalStateException)) throw expected;
                System.out.println("concurrent_close_result=" + expected.getCause().getClass().getSimpleName());
            }
            System.out.println("concurrent_close_seconds=" + ((System.nanoTime() - closeStart) / 1_000_000_000.0));
            // Closed IDs are never dangling pointers and close is idempotent.
            bridge.nativeClose(handle);
            try {
                bridge.nativeGenerate(handle, messages("user", "Olá"), 32);
                throw new AssertionError("Handle fechado foi aceito.");
            } catch (IllegalStateException expected) {
                System.out.println("closed_handle_rejected=true");
            }
            handle = 0;
            System.out.println("jni_real_model_smoke=passed");
        } finally {
            executor.shutdownNow();
            if (handle != 0) bridge.nativeClose(handle);
        }
    }
}
