/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ivibe.Model;

import java.util.Base64;

public class PasswordEncoder {
    
    public static String encode(String password) {

        String salt = "IVIBE67"; 

        String combined = salt + password + salt;

        return Base64.getEncoder().encodeToString(combined.getBytes());
    }
    public static String decode(String encoded) {

        try {
            byte[] decoded = Base64.getDecoder().decode(encoded);
            String password = new String(decoded);

            return password.replace("IVIBE67", "");
        } catch (Exception e) {
            return null;
        }
    }
    
    public static boolean verify(String inputPassword, String Salvata) {

        return encode(inputPassword).equals(Salvata);
    }
}
