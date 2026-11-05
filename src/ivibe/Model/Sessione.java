package ivibe.Model;

import ivibe.Model.Utente;

public class Sessione {

    private static Utente utenteCorrente;

    public static void setUtenteCorrente(Utente u) {
        utenteCorrente = u;
    }

    public static Utente getUtenteCorrente() {
        return utenteCorrente;
    }

    public static boolean isLogged() {
        return utenteCorrente != null;
    }

    public static void logout() {
        utenteCorrente = null;
    }
}