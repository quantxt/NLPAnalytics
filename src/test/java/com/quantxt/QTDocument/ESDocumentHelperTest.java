package com.quantxt.QTDocument;

import com.quantxt.doc.helper.ESDocumentHelper;
import com.quantxt.helper.types.ExtInterval;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

/**
 * Created by matin on 1/28/18.
 */
public class ESDocumentHelperTest {

    private static ESDocumentHelper helper = new ESDocumentHelper();

    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "Rebelión empresarial en EU vs. Trump por TLCAN Cúpulas " +
                "empresariales de Estados Unidos se han agrupado para cabildear " +
                "en Washington contra la postura de la Casa Blanca y buscar mantener " +
                "el acuerdo comercial.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "han agrupado para cabildear");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "y buscar mantener");
        Assert.assertEquals(str.substring(tagged.get(4).getStart(), tagged.get(4).getEnd()),
                "el acuerdo comercial");
    }

    @Test
    public void testEntityExtract2() {
        // GIVEN
        String str = "“Las órdenes ejecutivas que Trump firmó el 25 de enero señalan " +
                "que ser indocumentado es un crimen, y que los criminales serán " +
                "deportados”, dijo a Univision Noticias Maru Mora Villapando, " +
                "directora de comunicaciones de la organización Latino Advocacy, " +
                "en Seattle, Washington.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

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
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "itcoin Gold:");
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "adiós");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "a los ASICs para minar, hola a las GPUs Bitcoin Gold");
    }
}
