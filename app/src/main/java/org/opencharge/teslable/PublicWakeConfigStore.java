package org.opencharge.teslable;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

/** Default-off wake configuration; independent AES-GCM/Keystore alias. No vehicle/API/MQTT keys reused. */
public final class PublicWakeConfigStore {
    private static final String PREFS_NAME = "tesla_ble_public_wake_v1";
    private static final String PREF_TOKEN = "token_envelope";
    private static final String API_ALIAS = "tesla_ble_public_wake_secrets_v1";
    private static final Object LOCK = new Object();
    private final SharedPreferences preferences;

    public PublicWakeConfigStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean enabled(){return preferences.getBoolean("enabled",false);}
    public boolean setEnabled(boolean value){if(value&&!configured())return false;return preferences.edit().putBoolean("enabled",value).commit();}
    public boolean configured(){return bearer()!=null&&tunnelToken()!=null;}
    public String bearer(){return field("bearer");}
    public String tunnelToken(){return field("tunnel");}
    private String field(String name){
        synchronized(LOCK){
            try{
                String envelope=preferences.getString(PREF_TOKEN,null);if(envelope==null)return null;
                SecretKey key=getKey(false);if(key==null)return null;
                String[] value=PublicWakeSecrets.decode(ApiTokenCipher.decrypt(key,envelope));
                String token=value["tunnel".equals(name)?1:0];
                return valid(token,"tunnel".equals(name)?100:32,"tunnel".equals(name)?8192:512)?token:null;
            }catch(Exception ignored){return null;}
        }
    }
    public boolean save(String bearer,String tunnel){
        synchronized(LOCK){
            if(bearer==null||bearer.isEmpty())bearer=bearer();
            if(tunnel==null||tunnel.isEmpty())tunnel=tunnelToken();
            if(!valid(bearer,32,512)||!valid(tunnel,100,8192))return false;
            try{
                String encrypted=ApiTokenCipher.encrypt(getKey(true),PublicWakeSecrets.encode(bearer,tunnel));
                return preferences.edit().putString(PREF_TOKEN,encrypted).commit();
            }catch(Exception ignored){return false;}
        }
    }
    private static boolean valid(String text,int min,int max){
        if(text==null||text.length()<min||text.length()>max)return false;
        for(int i=0;i<text.length();i++){char c=text.charAt(i);if(c<0x21||c>0x7e)return false;}
        return true;
    }
    private static SecretKey getKey(boolean create) throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (!keyStore.containsAlias(API_ALIAS)) {
            if (!create) return null;
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(API_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build());
            generator.generateKey();
        }
        return (SecretKey) keyStore.getKey(API_ALIAS, null);
    }
}
