package org.opencharge.teslable;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
/** Only the explicit default-off opt-in restores this gateway; never sends a wake. */
public final class PublicWakeBootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context context,Intent intent){
  if(intent==null||(!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())&&!Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())))return;
  PublicWakeConfigStore config=new PublicWakeConfigStore(context);
  if(!config.enabled()||!config.configured())return;
  Intent start=new Intent(context,TeslaBleService.class).setAction(TeslaBleService.ACTION_PUBLIC_WAKE_RECONFIGURE);
  try{if(Build.VERSION.SDK_INT>=26)context.startForegroundService(start);else context.startService(start);}
  catch(IllegalStateException|SecurityException ignored){/* user can reopen app; no unauthenticated fallback */}
 }
}
