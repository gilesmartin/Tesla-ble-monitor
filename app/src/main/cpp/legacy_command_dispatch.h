#pragma once
#include <string>
#include <vector>
#include <utility>
#include <cmath>
#include <cstdlib>
#include <cctype>
namespace TeslaAndroid {
inline bool legacy_integer(const std::string& text,int minimum,int maximum,int& value) {
 if(text.empty()||text.size()>7)return false;
 for(unsigned char c:text)if(c<'0'||c>'9')return false;
 long parsed=std::strtol(text.c_str(),nullptr,10);
 if(parsed<minimum||parsed>maximum)return false;
 value=static_cast<int>(parsed);return true;
}
inline bool legacy_decimal(const std::string& text,float minimum,float maximum,float& value) {
 if(text.empty()||text.size()>32)return false;
 bool digit=false,dot=false;
 for(unsigned char c:text){if(c>='0'&&c<='9'){digit=true;continue;}if(c=='.'&&!dot){dot=true;continue;}return false;}
 if(!digit)return false;
 char* end=nullptr;float parsed=std::strtof(text.c_str(),&end);
 if(!end||*end||!std::isfinite(parsed)||parsed<minimum||parsed>maximum)return false;
 value=parsed;return true;
}
// Call only on the existing BLE owner's Looper. No shell or second client.
// true means a request was queued; ONLY the supplied callback carries the
// vehicle acknowledgement. Rejected/invalid input cannot reach a vehicle API.
template<class V,class Callback>
bool dispatch_legacy_command(V& vehicle,const std::string& operation,
 const std::vector<std::string>& args,Callback callback) {
 if(operation.size()>64||args.size()>1)return false;
 if(args.empty()) {
  if(operation=="wake")vehicle.wake(std::move(callback));
  else if(operation=="flash-lights")vehicle.flash_lights(std::move(callback));
  else if(operation=="honk")vehicle.honk_horn(std::move(callback));
  else if(operation=="lock")vehicle.lock(std::move(callback));
  else if(operation=="unlock")vehicle.unlock(std::move(callback));
  else if(operation=="frunk-open")vehicle.open_frunk(std::move(callback));
  else if(operation=="media-toggle-playback")vehicle.media_toggle_playback(std::move(callback));
  else if(operation=="software-update-cancel")vehicle.cancel_software_update(std::move(callback));
  else return false;
  return true;
 }
 const std::string& value=args[0];if(value.size()>32)return false;
 if(operation=="climate"||operation=="sentry-mode"||operation=="steering-wheel-heater") {
  if(value!="on"&&value!="off")return false;
  const bool enabled=value=="on";
  if(operation=="climate")vehicle.set_climate(enabled,std::move(callback));
  else if(operation=="sentry-mode")vehicle.set_sentry_mode(enabled,std::move(callback));
  else vehicle.set_steering_wheel_heat(enabled,std::move(callback));
  return true;
 }
 if(operation=="charging") {
  if(value!="start"&&value!="stop")return false;
  vehicle.set_charging_state(value=="start",std::move(callback));return true;
 }
 if(operation=="charge-port") {
  if(value=="open")vehicle.open_charge_port(std::move(callback));
  else if(value=="close")vehicle.close_charge_port(std::move(callback));
  else if(value=="unlock")vehicle.unlock_charge_port(std::move(callback));
  else return false;
  return true;
 }
 if(operation=="trunk") {
  if(value=="open")vehicle.open_trunk(std::move(callback));
  else if(value=="close")vehicle.close_trunk(std::move(callback));
  else return false;
  return true;
 }
 if(operation=="windows") {
  if(value=="vent"||value=="open")vehicle.vent_windows(std::move(callback));
  else if(value=="close")vehicle.close_windows(std::move(callback));
  else return false;
  return true;
 }
 if(operation=="charging-set-amps"||operation=="charging-set-amps-override") {
  int amps;if(!legacy_integer(value,0,32,amps))return false;
  // Legacy <=4A workaround: a second idempotent set is deliberately sent only
  // after the first is acknowledged. The override topic performs one set.
  if(amps<=4&&operation=="charging-set-amps") {
   vehicle.set_charging_amps(amps,[&vehicle,amps,callback=std::move(callback)](auto result) mutable {
    if(!result.is_success()){callback(std::move(result));return;}
    vehicle.set_charging_amps(amps,std::move(callback));
   });
  } else vehicle.set_charging_amps(amps,std::move(callback));
  return true;
 }
 if(operation=="charging-set-limit") {
  int limit;if(!legacy_integer(value,0,100,limit))return false;
  vehicle.set_charging_limit(limit,std::move(callback));return true;
 }
 if(operation=="climate-set-temp") {
  float temperature;if(!legacy_decimal(value,15.0f,30.0f,temperature))return false;
  vehicle.set_climate_temp(temperature,std::move(callback));return true;
 }
 if(operation=="media-set-volume") {
  float volume;if(!legacy_decimal(value,0.0f,10.0f,volume))return false;
  vehicle.set_media_volume(volume,std::move(callback));return true;
 }
 if(operation=="software-update-start") {
  // Never invent a zero-delay default for the legacy command missing its argument.
  int delay;if(!legacy_integer(value,0,604800,delay))return false;
  vehicle.schedule_software_update(delay,std::move(callback));return true;
 }
 if(operation=="heater-seat-front-left"||operation=="heater-seat-front-right"
    ||operation=="heater-seat-rear-left"||operation=="heater-seat-rear-right") {
  int level;if(value=="off")level=0;else if(value=="low")level=1;
  else if(value=="med")level=2;else if(value=="high")level=3;else return false;
  auto seat=operation=="heater-seat-front-left"?TeslaBLE::SeatPosition::FRONT_LEFT
    :operation=="heater-seat-front-right"?TeslaBLE::SeatPosition::FRONT_RIGHT
    :operation=="heater-seat-rear-left"?TeslaBLE::SeatPosition::REAR_LEFT:TeslaBLE::SeatPosition::REAR_RIGHT;
  vehicle.set_seat_heater(seat,level,std::move(callback));return true;
 }
 return false;
}
}
