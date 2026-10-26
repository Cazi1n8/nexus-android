#include "nexus_runtime.hpp"
#include "nlohmann/json.hpp"
#include <chrono>
#include <fstream>
#include <future>
#include <iostream>
#include <thread>
using Json = nlohmann::json;
using Clock = std::chrono::steady_clock;
Json msg(const char * role, const std::string & content) {return {{"role",role},{"content",content}};}
int main(int argc,char **argv) {
 if(argc != 3) return 2;
 Json report={{"scope","Synthetic host C++ prototype only; not Android or semantic quality validation"},{"cases",Json::array()}};
 auto handle = nexus::load(argv[1],1024,4);
 std::string system="You are a precise assistant. Reply in Portuguese with only one short word or number. The following are synthetic test facts: ";
 for(int i=0;i<30;++i) system += "Item "+std::to_string(i)+" is blue. ";
 auto run=[&](const std::string & name,const Json & chat,int budget=24) {
   const auto start=Clock::now();
   Json result=Json::parse(nexus::generate_result(handle,chat.dump(),budget));
   result["name"]=name;
   result["elapsedMs"]=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-start).count();
   report["cases"].push_back(result);
   std::ofstream(argv[2]) << report.dump(2);
   std::cerr << name << " " << result["elapsedMs"] << "ms reused=" << result.value("reusedPromptTokens",0) << "\n";
   return result["text"].get<std::string>();
 };
 Json a=Json::array({msg("system",system),msg("user","Qual é a cor do item 5?")});
 auto first=run("cold",a);
 auto second=run("same_query_warm",a);
 report["repeatByteEqual"]=(first==second);
 Json b=a; b.push_back(msg("assistant",first)); b.push_back(msg("user","E a cor do item 12?"));
 auto banswer=run("new_turn",b);
 Json changed=b; changed.back()=msg("user","Quanto é 7 + 3? Responda só o número.");
 run("changed_last_user",changed);
 Json other=Json::array({msg("system","You answer with only a number."),msg("user","What is 9 plus 9?")});
 run("changed_system",other);
 Json longchat=Json::array({msg("system","Answer briefly in Portuguese. This is a synthetic context trimming test.")});
 for(int i=0;i<30;++i) {longchat.push_back(msg("user","O item fictício "+std::to_string(i)+" é azul e tem um nome sintético usado neste teste de contexto."));longchat.push_back(msg("assistant","Informação sintética recebida para o teste de contexto, sem dados pessoais."));}
 longchat.push_back(msg("user","Quanto é 2 mais 2?"));
 run("pruned_history",longchat);
 run("pruned_history_repeat",longchat);
 bool invalid=false;
 try {nexus::generate_result(handle,"[{]",24);} catch(const std::invalid_argument &){invalid=true;}
 report["invalidJsonRejected"]=invalid;
 run("after_invalid_json",a);
 nexus::cancel(handle);
 run("after_idle_cancel",a);
 std::promise<void> entered;
 auto worker=std::async(std::launch::async,[&] {
  entered.set_value();
  try {nexus::generate_result(handle,Json::array({msg("system",system),msg("user","Escreva todos os números de 1 a 300 sem abreviar.")}).dump(),256);return std::string("completed_before_cancel");}
  catch(const nexus::Cancelled &){return std::string("cancelled");}
 });
 entered.get_future().wait(); std::this_thread::sleep_for(std::chrono::milliseconds(50));
 const auto cancel_start=Clock::now(); nexus::cancel(handle);
 report["activeCancelResult"]=worker.get();
 report["cancelElapsedMs"]=std::chrono::duration_cast<std::chrono::milliseconds>(Clock::now()-cancel_start).count();
 run("after_active_cancel",a);
 nexus::close(handle);
 bool closed=false;try{nexus::generate_result(handle,a.dump(),24);}catch(const std::runtime_error &){closed=true;}
 report["closedHandleRejected"]=closed;
 std::ofstream(argv[2]) << report.dump(2);
 return 0;
}
