#!/usr/bin/env python3
"""Evaluate real GGUF output through the app's Kotlin prompt and native C++.

Uses already-prepared compiler jars, pinned llama.cpp sources/libs and a local
model. Does not download dependencies, contact an AI API or invent test answers.
Recorded questions and replies are the synthetic evaluation suite only.
"""
from __future__ import annotations
import argparse
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--model', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--suite', choices=('comparison', 'full'), default='comparison')
    parser.add_argument('--context', type=int, default=2048)
    parser.add_argument('--max-tokens', type=int, default=384)
    parser.add_argument('--build', type=Path, default=ROOT / 'build/native-quality-host')
    parser.add_argument('--sources', type=Path, default=Path('/workspace/tooling/llama.cpp'))
    parser.add_argument('--libraries', type=Path, default=Path('/workspace/tooling/ai-smoke/runtime/llama-b11838'))
    args = parser.parse_args()
    sdk = Path(os.environ.get('JAVA_HOME', ''))
    if not (sdk/'bin/java').is_file() or not args.model.is_file():
        parser.error('Prepare JAVA_HOME e o modelo GGUF local antes desta avaliação.')
    if args.context not in range(512,4097) or args.max_tokens not in range(1,min(1025,args.context-64)):
        parser.error('Contexto/saída fora dos limites do runtime.')
    caches = Path(os.environ.get('GRADLE_USER_HOME',str(ROOT/'.gradle-dist/cache')))/'caches/modules-2/files-2.1'
    def jar(group,name,version='*'):
        paths=sorted((caches/group/name).glob(f'{version}/*/*.jar'))
        if not paths: raise FileNotFoundError(f'Dependência local ausente: {group}:{name}. Prepare o projeto com Gradle primeiro.')
        return str(paths[-1])
    stdlib=jar('org.jetbrains.kotlin','kotlin-stdlib','2.1.20')
    json_jar=jar('org.json','json','20240303')
    annotation=jar('org.jetbrains','annotations')
    compiler=[jar('org.jetbrains.kotlin','kotlin-compiler-embeddable','2.1.20'),stdlib,
              jar('org.jetbrains.kotlin','kotlin-script-runtime'),jar('org.jetbrains.kotlin','kotlin-reflect'),
              jar('org.jetbrains.intellij.deps','trove4j'),jar('org.jetbrains.kotlinx','kotlinx-coroutines-core-jvm'),annotation]
    build=args.build.resolve(); build.mkdir(parents=True,exist_ok=True)
    classes=build/'classes';classes.mkdir(exist_ok=True)
    (build/'HostAiTypes.kt').write_text('package br.com.nexus.assistant.data\ndata class AiMessage(val role:String,val content:String,val imagePath:String?=null)\nclass AiException(message:String):Exception(message)\n')
    (build/'HostContext.kt').write_text('''package br.com.nexus.assistant.data
import br.com.nexus.assistant.data.local.LocalChatContext
import org.json.JSONArray
object HostContext {
 @JvmStatic fun build(name:String,history:String,context:Int,output:Int):String {
  val data=JSONArray(history)
  val messages=(0 until data.length()).map { val m=data.getJSONObject(it); AiMessage(m.getString("role"),m.getString("content")) }
  return LocalChatContext.build(NexusSettings(userPreferredName=name),messages,emptyList(),context,output)
 }
}
''')
    source=ROOT/'app/src/main/java/br/com/nexus/assistant'
    kotlin=[source/'data/NexusSettings.kt',source/'data/AssistantIdentity.kt',source/'data/local/LocalModelCatalog.kt',
            source/'data/local/LocalChatContext.kt',source/'data/local/ExtractiveHistoryCapsule.kt',build/'HostAiTypes.kt',build/'HostContext.kt']
    subprocess.run([str(sdk/'bin/java'),'-Xmx768m','-cp',os.pathsep.join(compiler),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib','-no-reflect','-jvm-target','17','-classpath',os.pathsep.join([stdlib,json_jar,annotation]),'-d',str(classes),
                    *map(str,kotlin)],check=True)
    cpp=ROOT/'app/src/main/cpp'
    library=build/'libnexus_llama.so'
    subprocess.run(['g++','-std=c++17','-O3','-fPIC','-shared','-pthread',f'-I{sdk}/include',f'-I{sdk}/include/linux',
                    f'-I{args.sources}/include',f'-I{args.sources}/ggml/include',f'-I{args.sources}/vendor',
                    str(cpp/'nexus_jni.cpp'),str(cpp/'nexus_runtime.cpp'),f'-L{args.libraries}',f'-Wl,-rpath,{args.libraries}',
                    '-lllama','-o',str(library)],check=True)
    subprocess.run([str(sdk/'bin/javac'),'-encoding','UTF-8','-cp',os.pathsep.join([str(classes),stdlib,json_jar]),'-d',str(classes),
                    str(cpp/'tests/NexusNativeBridge.java'),str(cpp/'tests/NexusAiQualityEval.java')],check=True)
    environment=dict(os.environ)
    environment['LD_LIBRARY_PATH']=str(args.libraries)+os.pathsep+environment.get('LD_LIBRARY_PATH','')
    subprocess.run([str(sdk/'bin/java'),'-cp',os.pathsep.join([str(classes),stdlib,json_jar]),
                    'br.com.nexus.assistant.data.local.NexusAiQualityEval',str(library),str(args.model.resolve()),str(args.output.resolve()),
                    args.suite,str(args.context),str(args.max_tokens)],cwd=args.libraries,env=environment,check=True)
    return 0


if __name__=='__main__':
    try: raise SystemExit(main())
    except (OSError,subprocess.CalledProcessError) as failure:
        print('Avaliação real interrompida: '+str(failure),file=sys.stderr);raise SystemExit(1)
