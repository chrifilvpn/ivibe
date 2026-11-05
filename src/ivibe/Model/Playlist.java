package ivibe.Model;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.ArrayList;
import java.util.List;

public class Playlist {

    private int id;
    private StringProperty nome;
    private List<Canzone> canzoni;

    public Playlist(int id, String nome) {
        this(nome);
        this.id = id;
    }

    public Playlist(String nome) {

        this.nome = new SimpleStringProperty(nome);
        this.canzoni = new ArrayList<Canzone>();
    }

    public Playlist(String nome, List<Canzone> canzoni) {

        this.nome = new SimpleStringProperty(nome);

        this.canzoni = new ArrayList<Canzone>();

        if (canzoni != null) {

            for (Canzone c : canzoni) {

                if (c != null) {
                    this.canzoni.add(c);
                }
            }
        }
    }

    /** Id della playlist nel database (0 se non ancora salvata). */
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public StringProperty nomeProperty() {
        return nome;
    }

    public String getNome() {

        if (nome == null) {
            return "";
        }

        return nome.get();
    }

    public void setNome(String nome) {

        if (this.nome == null) {
            this.nome = new SimpleStringProperty("");
        }

        this.nome.set(nome);
    }

    public List<Canzone> getCanzoni() {

        if (canzoni == null) {
            canzoni = new ArrayList<Canzone>();
        }

        return canzoni;
    }

    public void setCanzoni(List<Canzone> canzoni) {

        this.canzoni = new ArrayList<Canzone>();

        if (canzoni != null) {

            for (Canzone c : canzoni) {

                if (c != null) {
                    this.canzoni.add(c);
                }
            }
        }
    }

    public void addCanzone(Canzone c) {

        if (c == null) {
            return;
        }

        if (canzoni == null) {
            canzoni = new ArrayList<Canzone>();
        }

        if (!canzoni.contains(c)) {
            canzoni.add(c);
        }
    }

    public void removeCanzone(Canzone c) {

        if (c == null) {
            return;
        }

        if (canzoni == null) {
            return;
        }

        canzoni.remove(c);
    }

    public int getNumeroCanzoni() {

        if (canzoni == null) {
            return 0;
        }

        return canzoni.size();
    }

    public void clearPlaylist() {

        if (canzoni == null) {
            return;
        }

        canzoni.clear();
    }

    public boolean containsCanzone(Canzone c) {

        if (c == null) {
            return false;
        }

        if (canzoni == null) {
            return false;
        }

        if (canzoni.contains(c)) {
            return true;
        }

        return false;
    }

    @Override
    public String toString() {

        String nomePlaylist = getNome();

        int numero = getNumeroCanzoni();

        return nomePlaylist + " (" + numero + " canzoni)";
    }
}