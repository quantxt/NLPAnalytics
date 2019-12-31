package com.quantxt.doc.helper;

import com.quantxt.helper.types.ExtIntervalSimple;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * Created by matin on 1/28/18.
 */
public class ESDocumentHelperTest {

    private static ESDocumentHelper helper;

    @BeforeClass
    public static void setup() {
        if (helper != null) return;
        helper = new ESDocumentHelper();
        helper.loadNERModel();
    }

    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "Rebelión empresarial en EU vs. Trump por TLCAN Cúpulas " +
                "empresariales de Estados Unidos se han agrupado para cabildear " +
                "en Washington contra la postura de la Casa Blanca y buscar mantener " +
                "el acuerdo comercial.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Rebelión empresarial");
        Assert.assertEquals(str.substring(tagged.get(9).getStart(), tagged.get(9).getEnd()),
                "buscar mantener");
        Assert.assertEquals(str.substring(tagged.get(10).getStart(), tagged.get(10).getEnd()),
                "acuerdo comercial");
    }

    @Test
    @Ignore
    public void testEntityExtract2() {
        // GIVEN
        String str = "“Las órdenes ejecutivas que Trump firmó el 25 de enero señalan " +
                "que ser indocumentado es un crimen, y que los criminales serán " +
                "deportados”, dijo a Univision Noticias Maru Mora Villapando, " +
                "directora de comunicaciones de la organización Latino Advocacy, " +
                "en Seattle, Washington.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "“Las órdenes ejecutivas que Trump");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "señalan que ser indocumentado es");
    }

    @Test
    public void testEntityExtract3() {
        // GIVEN
        String str = "itcoin Gold: adiós a los ASICs para minar, hola a las GPUs Bitcoin Gold ," +
                "y con una diferencia destacada: será una versión de bitcoin \"resistente a ASICs\", " +
                "o lo que es lo mismo, que no se podrá minar como tradicionalmente se está minando " +
                "bitcoin.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "itcoin Gold");
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "adiós");
        Assert.assertEquals(str.substring(tagged.get(12).getStart(), tagged.get(12).getEnd()),
                "está minando");
    }

    @Test
    public void testEntityExtract4() {
        // GIVEN
        String str = "Los Golden State Warriors y los Houston Rockets definirán hoy en el " +
                "Juego 7 de las Finales de la Conferencia Oeste, quién será el oponente  " +
                "de los Cleveland Cavaliers en las Finales de la NBA.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Golden State Warriors");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "Cleveland Cavaliers");
        Assert.assertEquals(str.substring(tagged.get(10).getStart(), tagged.get(10).getEnd()),
                "NBA");
    }

    @Test
    public void testEntityExtract5() {
        // GIVEN
        String str = "Los grupos de rescate aún buscan a una persona desaparecida en la "+
                "segunda localidad que apenas se está recuperando de una inundación similar ocurrida en 2016.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "grupos de rescate");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "está recuperando");
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "inundación similar ocurrida");
    }

    @Test
    public void testEntityExtract6() {
        // GIVEN
        String str = "De la imagen podemos extraer más información, como que ambos " +
                "modelos contarían con una doble cámara para selfies y altavoz frontal " +
                "(posiblemente estéreo).";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "podemos extraer");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "información");
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "cámara para selfies");
    }

    @Test
    public void testEntityExtract7() {
        // GIVEN
        String str = "Las jornadas del sábado y el domingo han sido especialmente " +
                "difíciles para los residentes en varias zonas de la provincia Villa Clara, " +
                "Cienfuegos y Sancti Spíritus donde la crecida de ríos y el aliviado de " +
                "presas provocó inundaciones localmente graves, que han ido cediendo con las " +
                "primeras horas de la tarde de hoy.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "Sancti Spíritus");
        Assert.assertEquals(str.substring(tagged.get(7).getStart(), tagged.get(7).getEnd()),
                "crecida de ríos");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "aliviado de presas");
    }

    @Test
    public void testEntityExtract8() {
        // GIVEN
        String str = "Jimmy Carter avaló el fraude, no sé si por ingenuidad, porque lo engañaron, " +
                "por interés o por evitar un enfrentamiento armado.";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Jimmy Carter");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "no sé");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "enfrentamiento armado");
    }

    @Test
    public void testEntityExtract9() {
        // GIVEN
        String str = "Las autoridades cambiaron su versión del incidente con lo que abonaron " +
                "a la controversia en un momento en el que la Casa Blanca ha reprimido la " +
                "inmigración ilegal.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtIntervalSimple> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "autoridades");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "versión del incidente");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "inmigración ilegal");
    }

}
