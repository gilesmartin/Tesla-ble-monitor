package org.opencharge.teslable;
import java.util.*;
public final class LegacyMqttDiscoveryCatalogHarness {
 public static void main(String[] args){String vin=args.length>0?args[0]:"TESTTESTTESTTESTT";
  Set<String> identities=new HashSet<>(),topics=new HashSet<>();
  for(LegacyMqttDiscoveryCatalog.Definition d:LegacyMqttDiscoveryCatalog.create(vin)){
   if(!identities.add(d.getDiscoveryTopic().split("/")[1]+"|"+d.getUniqueId())||!topics.add(d.getDiscoveryTopic()))throw new AssertionError("duplicate scoped identity");
   if(!d.getAvailabilityTopic().startsWith("tesla_ble/"+vin+"/availability/"))throw new AssertionError("unresolved availability root");
   if("deploy-key".equals(d.getObjectId())||"regenerate-keys".equals(d.getObjectId())){
    if(!d.isActuating()||d.requiresConfirmation()||!d.isSupported())throw new AssertionError("explicit key action should be available only through controls opt-in");
   }
   System.out.println(d.toJson());
  }
 }
}
