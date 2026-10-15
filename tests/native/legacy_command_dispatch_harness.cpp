#include <functional>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>
#include <deque>
namespace TeslaBLE { enum class SeatPosition { FRONT_LEFT=7, FRONT_RIGHT=8, REAR_LEFT=9, REAR_RIGHT=12 }; }
#include "legacy_command_dispatch.h"
struct Ack {bool is_success()const{return true;}};
struct FakeVehicle {
 using Callback=std::function<void(Ack)>;
 std::string called;std::deque<Callback> pending;int calls=0;
 void record(std::string name,Callback callback){called=std::move(name);calls++;pending.push_back(std::move(callback));}
 template<class T>void record(std::string name,T value,Callback callback){std::ostringstream s;s<<name<<":"<<value;record(s.str(),std::move(callback));}
#define ZERO(method) void method(Callback cb){record(#method,std::move(cb));}
#define ONE(method,type) void method(type value,Callback cb){record(#method,value,std::move(cb));}
 ZERO(wake) ZERO(flash_lights) ZERO(honk_horn) ZERO(lock) ZERO(unlock)
 ZERO(open_frunk) ZERO(open_trunk) ZERO(close_trunk) ZERO(open_charge_port)
 ZERO(close_charge_port) ZERO(unlock_charge_port) ZERO(vent_windows) ZERO(close_windows)
 ZERO(media_toggle_playback) ZERO(cancel_software_update)
 ONE(set_charging_state,bool) ONE(set_charging_amps,int) ONE(set_charging_limit,int)
 ONE(set_climate,bool) ONE(set_climate_temp,float) ONE(set_steering_wheel_heat,bool)
 ONE(set_sentry_mode,bool) ONE(set_media_volume,float) ONE(schedule_software_update,int)
 void set_seat_heater(TeslaBLE::SeatPosition seat,int level,Callback cb){record("set_seat_heater:"+std::to_string(static_cast<int>(seat))+":"+std::to_string(level),std::move(cb));}
#undef ZERO
#undef ONE
};
int main(int argc,char** argv){
 if(argc<2)return 2;
 FakeVehicle v;int ack=0;std::vector<std::string> args;
 for(int i=2;i<argc;i++)args.push_back(argv[i]);
 bool accepted=TeslaAndroid::dispatch_legacy_command(v,argv[1],args,[&](Ack){ack++;});
 if(ack!=0){std::cerr<<"Callback before transport acknowledgement";return 3;}
 int guard=0;while(!v.pending.empty()&&guard++<32){auto cb=std::move(v.pending.front());v.pending.pop_front();cb(Ack{});}
 if(!accepted)std::cout<<"REJECTED ack="<<ack<<" called="<<v.called;
 else std::cout<<v.called<<" ack="<<ack<<" calls="<<v.calls;
}
