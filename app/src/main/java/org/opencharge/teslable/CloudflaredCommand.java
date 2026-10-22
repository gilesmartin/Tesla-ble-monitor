package org.opencharge.teslable;
import java.io.File;
import java.util.Arrays;
import java.util.List;
/** Only these fixed flags are used; credentials never appear in argv or environment. */
public final class CloudflaredCommand {
 private CloudflaredCommand(){}
 public static List<String> args(File binary,File tokenFile){
  if(binary==null||tokenFile==null||!binary.isAbsolute()||!tokenFile.isAbsolute())throw new IllegalArgumentException();
  return Arrays.asList(binary.getAbsolutePath(),"--no-autoupdate","tunnel","--metrics","127.0.0.1:27841","--protocol","http2","--edge-ip-version","4","run","--token-file",tokenFile.getAbsolutePath());
 }
}
