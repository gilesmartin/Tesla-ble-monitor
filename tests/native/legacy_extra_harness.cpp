#include <stdexcept>
#include <iostream>
#include "legacy_extra_command.h"
#include "message_builders.h"
void check(bool ok){if(!ok)throw std::runtime_error("Legacy extra command fixture failed");}
int main(){using namespace TeslaAndroid;std::string vin="5YJ3E1EBXJF096057";
 auto plan=plan_legacy_extra("charging-schedule",{"1439"},vin);check(plan.has_value());
 auto payload=scheduled_charging_payload(*plan);check(payload.enabled&&payload.charging_time==1439);
 CarServer_VehicleAction action=CarServer_VehicleAction_init_default;
 auto& builders=TeslaBLE::VehicleActionBuilder::get_builders();
 check(builders.at(CarServer_VehicleAction_scheduledChargingAction_tag)(action,&payload)==0);
 check(action.vehicle_action_msg.scheduledChargingAction.charging_time==1439);
 plan=plan_legacy_extra("charging-schedule-cancel",{},vin);check(plan.has_value());payload=scheduled_charging_payload(*plan);check(!payload.enabled);
 check(!plan_legacy_extra("charging-schedule",{"1440"},vin));check(!plan_legacy_extra("charging-schedule",{"-1"},vin));
 check(!plan_legacy_extra("autosecure-modelx",{},vin));check(plan_legacy_extra("autosecure-modelx",{},"5YJXCAE21GF000001").has_value());
 check(!plan_legacy_extra("tonneau",{"open"},vin));plan=plan_legacy_extra("tonneau",{"open"},"7G2CEHED0RA000001");check(plan.has_value());
 auto closure=tonneau_payload(*plan);check(closure.tonneau==VCSEC_ClosureMoveType_E_CLOSURE_MOVE_TYPE_OPEN);check(closure.frontDriverDoor==VCSEC_ClosureMoveType_E_CLOSURE_MOVE_TYPE_NONE);
 check(plan_legacy_extra("auto-seat-and-climate",{},vin)->enabled);check(!plan_legacy_extra("auto-seat-and-climate",{"off"},vin)->enabled);
 for(auto op:{"drive","list-keys","ping"}){check(plan_legacy_extra(op,{},vin).has_value());check(!plan_legacy_extra(op,{"junk"},vin));}
 check(!plan_legacy_extra("generate-keys",{},vin));check(!plan_legacy_extra("deploy-key",{},vin));check(!plan_legacy_extra("software-update-start",{},vin));
 std::cout<<"PASS legacy extra command plans, schedule builder, model gating, closure isolation, bounds and local-confirmation-only keys\n";
}
