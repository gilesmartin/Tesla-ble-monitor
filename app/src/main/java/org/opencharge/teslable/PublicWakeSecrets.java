package org.opencharge.teslable;
/** Validated, unambiguous pair inside the dedicated AES-GCM envelope. */
public final class PublicWakeSecrets {
 private PublicWakeSecrets(){}
 public static String encode(String bearer,String tunnel){
  if(!valid(bearer,32,512)||!valid(tunnel,100,8192))throw new IllegalArgumentException();
  return bearer+"\n"+tunnel;
 }
 public static String[] decode(String text){
  String[] pair=text.split("\n",-1);if(pair.length!=2)throw new IllegalArgumentException();encode(pair[0],pair[1]);return pair;
 }
 public static boolean valid(String value,int min,int max){
  if(value==null||value.length()<min||value.length()>max)return false;
  for(int i=0;i<value.length();i++)if(value.charAt(i)<0x21||value.charAt(i)>0x7e)return false;
  return true;
 }
}
