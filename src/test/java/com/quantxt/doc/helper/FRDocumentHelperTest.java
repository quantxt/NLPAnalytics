package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtInterval;
import com.quantxt.helper.types.ExtIntervalSimple;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by matin on 5/28/18.
 */
public class FRDocumentHelperTest {

    private static FRDocumentHelper helper = new FRDocumentHelper();

    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "Alors qu’il arrivait de la gare du Nord, ce jeune sans-papiers " +
                "tombe sur un attroupement au 49 rue Marx-Dormoy et des enfants qui crient.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "arrivait");

        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "gare du Nord");

        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "attroupement");

        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "crient");
    }

    @Test
    public void testEntityExtract2() {
        // GIVEN
        String str = "Les Français Richard Gasquet, Gilles Simon, Pauline Parmentier et Benoit Paire sont " +
                "qualifiés pour le deuxième tour, tout comme Novak Djokovic et Dominic Thiem.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Français Richard Gasquet");

        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "Gilles Simon");

        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "Benoit Paire");

        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "sont");

        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "deuxième tour");
    }

    @Test
    public void testEntityExtract3() {
        // GIVEN
        String str = "Les Français Richard Gasquet, Gilles Simon, Pauline Parmentier et Benoit Paire sont " +
                "qualifiés pour le deuxième tour, tout comme Novak Djokovic et Dominic Thiem.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Français Richard Gasquet");

        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "Gilles Simon");

        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "Benoit Paire");

        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "sont");

        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "deuxième tour");
    }

    @Test
    public void testEntityExtract4() {
        // GIVEN
        String str = "Le chef d'entreprise et ancien sénateur LR Serge Dassault est mort ce lundi à l'âge de 93 ans.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "chef d'entreprise");

        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "ancien sénateur LR Serge Dassault");
    }

    @Test
    public void testEntityExtract5() {
        // GIVEN
        String str = "L'industriel a succombé à une \"défaillance cardiaque\" dans son bureau des Champs-Elysées à Paris a précisé sa famille au journal Le Figaro, dont il était le patron depuis 2002.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "industriel");

        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "défaillance cardiaque");

        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "a précisé");

        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "était");

    }

    @Test
    public void testEntityExtract6() {
        // GIVEN
        String str = "Ezequiel Pereira a déposé des demandes auprès de plusieurs programmes de bourses pour étudier l'informatique aux Etats-Unis, mais aucune n'a accepté sa candidature.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Ezequiel Pereira");

        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "a déposé");

        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "demandes");

        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "programmes de bourses");

        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "étudier");
    }
}
