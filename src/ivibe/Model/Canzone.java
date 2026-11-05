package ivibe.Model;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;


public class Canzone {
    private StringProperty titolo;
    private StringProperty url;
    private DoubleProperty durata;
    private StringProperty artista;
    private IntegerProperty n;
    
    public Canzone(String titolo, String artista, double durata, String url, int n){
        this.titolo = new SimpleStringProperty(titolo);
        this.url = new SimpleStringProperty(url);
        this.durata = new SimpleDoubleProperty(durata);
        this.artista = new SimpleStringProperty(artista);
        this.n = new SimpleIntegerProperty(n);
    }
    
    public String getTitolo() {
        return titolo.get();
    }

    public void setTitolo(String titolo) {
        this.titolo.set(titolo);
    }

    public StringProperty titoloProperty() {
        return titolo;
    }

    public String getUrl() {
        return url.get();
    }

    public void setUrl(String url) {
        this.url.set(url);
    }

    public StringProperty urlProperty() {
        return url;
    }

    public double getDurata() {
        return durata.get();
    }

    public void setDurata(double durata) {
        this.durata.set(durata);
    }

    public DoubleProperty durataProperty() {
        return durata;
    }

    public String getArtista() {
        return artista.get();
    }

    public void setArtista(String artista) {
        this.artista.set(artista);
    }

    public StringProperty artistaProperty() {
        return artista;
    }

    public int getN() {
        return n.get();
    }

    public void setN(int n) {
        this.n.set(n);
    }

    public IntegerProperty nProperty() {
        return n;
    }
}
