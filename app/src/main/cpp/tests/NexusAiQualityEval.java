package br.com.nexus.assistant.data.local;

import br.com.nexus.assistant.data.HostContext;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real CPU/JNI model evaluation; only synthetic test conversations are retained. */
public final class NexusAiQualityEval {
    private final NexusNativeBridge bridge = new NexusNativeBridge();
    private final JSONObject report = new JSONObject();
    private final JSONArray cases = new JSONArray();
    private final long handle;
    private final int contextTokens;
    private final int maxTokens;
    private final Path output;

    private NexusAiQualityEval(String library, String model, Path output, int contextTokens, int maxTokens) throws Exception {
        System.load(library);
        this.contextTokens = contextTokens;
        this.maxTokens = maxTokens;
        this.output = output;
        report.put("scope", "Actual app Kotlin prompt/context construction + same C++ JNI and real GGUF on Linux CPU; not an Android or physical-device test.")
            .put("modelPath", model).put("contextTokens", contextTokens).put("threads", 2)
            .put("maxOutputTokens", maxTokens).put("syntheticConversationsOnly", true).put("cases", cases);
        long start = System.nanoTime();
        handle = bridge.nativeLoad(model.getBytes(StandardCharsets.UTF_8), contextTokens, 2);
        report.put("loadMilliseconds", (System.nanoTime() - start) / 1_000_000);
    }

    private JSONObject generate(String id, String name, JSONArray history, String query, int limit) throws Exception {
        history.put(new JSONObject().put("role", "user").put("content", query));
        String actualContext = HostContext.build(name, history.toString(), contextTokens, limit);
        long start = System.nanoTime();
        JSONObject result = new JSONObject(new String(bridge.nativeGenerateResult(handle, actualContext.getBytes(StandardCharsets.UTF_8), limit), StandardCharsets.UTF_8));
        result.put("id", id).put("query", query).put("milliseconds", (System.nanoTime() - start) / 1_000_000);
        result.put("assistantName", "NEXUS").put("userPreferredName", name);
        history.put(new JSONObject().put("role", "assistant").put("content", result.getString("text")));
        cases.put(result);
        save();
        System.out.println(id + ": " + result.getString("finishReason") + " " + result.getLong("milliseconds") + " ms");
        return result;
    }

    private JSONObject one(String id, String query) throws Exception { return generate(id, "Calebe", new JSONArray(), query, maxTokens); }
    private static String normalized(String value) { return Normalizer.normalize(value.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", ""); }
    private static void check(JSONObject result, String rubric, boolean passed) { result.put("automaticCriterion", rubric).put("automaticCriterionPassed", passed); }
    private static boolean has(JSONObject result, String term) { return normalized(result.getString("text")).contains(term); }

    private void evaluate(boolean comparisonOnly) throws Exception {
        JSONArray names = new JSONArray();
        JSONObject a = generate("01_user_name", "Calebe", names, "Qual é meu nome?", maxTokens);
        check(a, "Identifica Calebe no texto; revisão humana avalia se foi atribuído ao usuário.", has(a,"calebe"));
        a = one("02_assistant_name", "Qual é seu nome?");
        check(a, "Contém NEXUS e não diz que seu nome é Calebe.", has(a,"nexus") && !normalized(a.getString("text")).matches("(?s).*(?:me chamo|meu nome e) calebe.*"));
        a = one("03_apology", "Me faz um texto de desculpa.");
        check(a, "Produz ao menos80caracteres com desculpa e não usa a defesa relatada.", has(a,"desculp") && !has(a,"nao tenho nada") && a.getString("text").length()>=80);
        a = one("12_typo_apology", "chat me ajda faze um texo de descupa");
        check(a, "Compreende pedido informal de desculpas em texto produzido.", has(a,"desculp") && !has(a,"nao tenho nada") && a.getString("text").length()>=80);
        if (comparisonOnly) { save(); return; }
        a = one("04_math_model_only", "Quanto é 25 vezes 18? Explique o cálculo.");
        check(a, "Modelo produz450; ferramenta aritmética do app é testada separadamente.", has(a,"450"));
        a = one("05_ai_for_child", "Me explica o que é inteligência artificial como se eu tivesse 10 anos.");
        check(a,"Resposta fala de computadores/máquinas e possui explicação substancial; legibilidade requer revisão humana.", (has(a,"computador") || has(a,"maquina")) && a.getString("text").length()>150);
        JSONArray story = new JSONArray();
        generate("06a_max_story", "Calebe", story, "Crie uma história curta de um robô chamado Max.", maxTokens);
        a = generate("06b_happy_ending", "Calebe", story, "Agora faça um final feliz.", maxTokens);
        check(a,"Continua usando Max; felicidade/coerência requer revisão humana.",has(a,"max"));
        JSONArray letter = new JSONArray();
        generate("07a_formal_message", "Calebe", letter, "Escreva uma mensagem formal agradecendo por uma reunião.", maxTokens);
        a = generate("07b_informal_message", "Calebe", letter, "Deixe mais informal.", maxTokens);
        check(a,"Mantém referência à reunião; mudança de tom requer revisão humana.",has(a,"reuniao"));
        a = one("08_settings_proposal", "NEXUS, abra as configurações.");
        check(a,"Propõe ferramentaopen_settings; não comprova execução de ação.",a.getString("text").contains("open_settings"));
        a = one("09_live_camera_honesty", "Você consegue ver o que estou vendo?");
        check(a,"Admite ausência de acesso visual/câmera nesta inferência de texto.",has(a,"nao") && (has(a,"ver") || has(a,"visual") || has(a,"camera")));
        a = one("10_technology_essay", "Faça uma redação sobre tecnologia com introdução, desenvolvimento e conclusão.");
        check(a,"Produz texto sobretecnologia com maisde250caracteres; estrutura/coerência requer revisãohumana.",has(a,"tecnologia") && a.getString("text").length()>250);
        JSONArray longHistory = new JSONArray();
        generate("11_turn_01","Calebe",longHistory,"Estamos planejando uma feira de ciências. Nosso robô se chama Max, a equipe usa a cor ciano e o evento é no sábado. Responda em uma frase.",64);
        String[] updates = {"A apresentação terá três minutos.","Vamos usar materiais reciclados.","A turma vai cuidar da decoração.","Teremos um cartaz explicativo.","Precisamos testar os motores.","A bateria será recarregável.","A sala terá uma mesa de demonstração.","Vamos explicar como funcionam os sensores.","Haverá uma lista de materiais.","Precisamos dividir as tarefas.","A apresentação será em português.","Vamos escrever um roteiro curto.","Precisamos ensaiar as falas.","As peças terão etiquetas.","Teremos uma revisão do circuito.","A demonstração deve ser segura.","Vamos guardar as ferramentas.","A turma vai convidar as famílias."};
        for(int i=0;i<updates.length;i++) generate(String.format("11_turn_%02d",i+2),"Calebe",longHistory,"Sobre a nossa feira: "+updates[i]+" Responda em uma frase curta.",48);
        a=generate("11_turn_20","Calebe",longHistory,"Qual é o nome do robô, a cor da equipe e o dia do evento que combinamos?",maxTokens);
        check(a,"Recupera Max,ciano,sábado após20mensagens de usuário.",has(a,"max")&&has(a,"ciano")&&has(a,"sabado"));
        a=generate("13_name_changed","Ana",names,"Agora, qual é meu nome e qual é o seu nome?",maxTokens);
        check(a,"Usa nomeatualAna eidentidadeNEXUS.",has(a,"ana")&&has(a,"nexus")&&!has(a,"calebe"));
        a=one("14_uncertainty","Qual será o número exato sorteado na próxima loteria? Tenho como saber com certeza?");
        check(a,"Expressa incerteza sobre previsão impossível.",has(a,"nao")&&(has(a,"prever")||has(a,"certeza")||has(a,"aleatorio")||has(a,"sorte")));
        a=one("15_multi_paragraph","Escreva três parágrafos curtos sobre a importância de estudar e descansar.");
        check(a,"Contém trêsparágrafos; inspeção humana avalia coerência e duplicação.",a.getString("text").split("\\n\\s*\\n").length>=3);
        save();
    }

    private void save() throws Exception { if(output.getParent()!=null)Files.createDirectories(output.getParent());Files.writeString(output,report.toString(2)+"\n"); }

    public static void main(String[] args) throws Exception {
        if(args.length<3)throw new IllegalArgumentException("library model output [comparison|full] [contextTokens] [maxTokens]");
        NexusAiQualityEval runner=new NexusAiQualityEval(args[0],args[1],Path.of(args[2]),args.length>4?Integer.parseInt(args[4]):2048,args.length>5?Integer.parseInt(args[5]):384);
        try {runner.evaluate(args.length>3&&args[3].equals("comparison"));runner.report.put("executionStatus","completed");runner.save();}
        finally{runner.bridge.nativeClose(runner.handle);}
    }
}
