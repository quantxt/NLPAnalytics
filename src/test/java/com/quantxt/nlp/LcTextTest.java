package com.quantxt.nlp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.junit.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;
import com.quantxt.types.BaseNameAlts;

public class LcTextTest {

    private static final String EXPOSURE_TYPE_JSON = "[{\"name\":\"Light Poles\",\"alts\":[\"light poles\",\"lighting\"],\"data\":\"Light Poles\"},{\"name\":\"Swimming Pool\",\"alts\":[\"pool\",\"pools\",\"indoor pool\",\"kiddie pool\",\"pool & equipement\",\"pool/spa\",\"spa & equipement\",\"swimming\"],\"data\":\"Swimming Pool\"},{\"name\":\"Canopies\",\"alts\":[\"canopies\",\"canopy\"],\"data\":\"Canopies\"},{\"name\":\"Signs (Not Attached)\",\"alts\":[\"sign\"],\"data\":\"Signs (Not Attached)\"},{\"name\":\"Tennis, Basketball, Suffleboard Courts\",\"alts\":[\"tennis\",\"courts\",\"court\",\"shuffleboard\",\"basketball\"],\"data\":\"Tennis, Basketball, Suffleboard Courts\"},{\"name\":\"Machinery & Equipment in the Open\",\"alts\":[\"solar\",\"solar panel\"],\"data\":\"Machinery & Equipment in the Open\"},{\"name\":\"Gazeboes, Cabanas, Bars\",\"alts\":[\"cabanas\",\"cabana\",\"bar\",\"bars\",\"gazeboe\",\"gazeboes\"],\"data\":\"Gazeboes, Cabanas, Bars\"},{\"name\":\"Golf Carts\",\"alts\":[\"golf\"],\"data\":\"Golf Carts\"},{\"name\":\"Carports\",\"alts\":[\"carport\",\"carports\",\"car port\",\"garage\"],\"data\":\"Carports\"},{\"name\":\"Other Buildings and Structures\",\"alts\":[\"aluminum fencing\",\"bathhouse\",\"boat slip\",\"cemetary\",\"chickee hut\",\"fountain\",\"gate house\",\"maintenance\",\"other structures\",\"outdoor furniture\",\"pavillion\",\"pump house\",\"spa\",\"spa shelter\",\"storage\",\"tiki bar & grill\",\"tiki hut\",\"utility\",\"wtr treatment & equip\"],\"data\":\"Other Buildings and Structures\"},{\"name\":\"Greenhouses (and Similar)\",\"alts\":[\"greenhouse\",\"greenhouses\"],\"data\":\"Greenhouses (and Similar)\"},{\"name\":\"Property Line Walls, Gates, Latticework, Trellises\",\"alts\":[\"fence\",\"fencing\",\"perimeter wall\"],\"data\":\"Property Line Walls, Gates, Latticework, Trellises\"}]";

    @Test
    public void testAdditioanlProperties() {
        // GIVEN
        LcText<String> additionalProp = new LcText<>(String.class);
        additionalProp.setOrSearch(true);
        JsonParser parser = new JsonParser();
        JsonArray bnas = (JsonArray) parser.parse(EXPOSURE_TYPE_JSON);

        java.lang.reflect.Type ct = new TypeToken<BaseNameAlts<String>[]>(){}.getType();
        InputStream is = new ByteArrayInputStream(bnas.toString().getBytes());
        additionalProp.loadCategorical(is, ct, false);

        // WHEN
        int sizeBuilding = additionalProp.extract("Building").getExtractions().size();
        int sizePool = additionalProp.extract("Pool").getExtractions().size();

        // THEN
        assertTrue(sizeBuilding == 0);
        assertTrue(sizePool > 0);
    }

}
