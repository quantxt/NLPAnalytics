package com.quantxt.doc.helper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.quantxt.helper.types.ExtInterval;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;

/**
 * Created by dejani on 2/1/18.
 */
public class RUDocumentHelperTest {

    private static RUDocumentHelper helper = new RUDocumentHelper();

    @Test
    public void testEntityExtract1() {
        // GIVEN
        String str = "У себя в Twitter Джанелидзе написал, что признание – это «результат российских манипуляций». «Режим Асада, признав независимость двух исторических регионов Грузии, грубо нарушил нормы международного права», – считает он.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Джанелидзе");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "результат российских");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "исторических регионов Грузии");
    }

    @Test
    @Ignore
    public void testEntityExtract2() {
        // GIVEN
        String str = "Дополнительных комментариев «Газпром» пока не предоставил. «Нафтогазу» ничего не известно о новых претензиях «Газпрома» к решению Стокгольмского арбитража, сообщил «Ведомостям» представитель украинской компании.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "стоимости вариант");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "предложенного аванса");
    }

    @Test
    public void testEntityExtract3() {
        // GIVEN
        String str = "Несовершеннолетнего россиянина поместили в изолированную комнату центра содержания, предназначенную для взрослых. У него отобрали все личные вещи, в том числе телефон.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "поместили");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "изолированную комнату центра содержания");
        Assert.assertEquals(str.substring(tagged.get(7).getStart(), tagged.get(7).getEnd()),
                "числе телефон");
    }

    @Test
    public void testEntityExtract4() {
        // GIVEN
        String str = "\"Абхазия и Южная Осетия являются неотъемлемой частью суверенной территории Грузии. США рассматривают признание независимости этих регионов как нарушение территориальной целостности Грузии\", - сказала Руд журналистам во вторник после состоявшейся в грузинским МИДе встрече с аккредитованными в Грузии зарубежными дипломатами.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "Абхазия");
        Assert.assertEquals(str.substring(tagged.get(5).getStart(), tagged.get(5).getEnd()),
                "рассматривают");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "нарушение территориальной");
    }

    @Test
    public void testEntityExtract5() {
        // GIVEN
        String str = "Власти полуострова приняли решение пробурить 36 скважин и построить дополнительные водозаборы. Также реконструировали действующие каналы и трубопроводы, которые ранее теряли около четверти запаса имеющейся воды.";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "приняли");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "дополнительные водозаборы");
        Assert.assertEquals(str.substring(tagged.get(12).getStart(), tagged.get(12).getEnd()),
                "имеющейся");
    }

    @Test
    public void testEntityExtract6() {
        // GIVEN
        String str = "Hапример, самый минимальный по стоимости вариант выходит при оформлении Renault Kaptur на максимальный срок (12 месяцев) и уплатой максимально предложенного аванса (341066 рублей).";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "стоимости вариант");
        Assert.assertEquals(str.substring(tagged.get(6).getStart(), tagged.get(6).getEnd()),
                "предложенного аванса");
    }

    @Test
    public void testEntityExtract7() {
        // GIVEN
        String str = "У буксирной проушины, которая поставляется вместе с этими автомобилями, могут быть дефекты сварки, сообщила компания, и при буксировке она может оборваться.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "буксирной проушины");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "могут быть");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "может оборваться");
    }

    @Test
    public void testEntityExtract8() {
        // GIVEN
        String str = "Рассылка от имени «Лаборатории Касперского» направлялась на английском языке и содержала «жалобу» пользователю, что с его компьютера якобы зафиксирована незаконная активность. Рассылка, которая направлялась 28 мая, также была на английском языке.";

        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "Лаборатории Касперского");
        Assert.assertEquals(str.substring(tagged.get(12).getStart(), tagged.get(12).getEnd()),
                "английском языке");
        Assert.assertEquals(str.substring(tagged.get(9).getStart(), tagged.get(9).getEnd()),
                "направлялась");
    }

    @Test
    public void testEntityExtract9() {
        // GIVEN
        String str = "В последующие пять торговых сессий выход инвесторов продолжился: вплоть до 16 апреля нерезиденты продавали ОФЗ на сумму около 11 млрд рублей в день, а к 18 апреля вывели с рынка больше 100 млрд рублей.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(1).getStart(), tagged.get(1).getEnd()),
                "продолжился");
        Assert.assertEquals(str.substring(tagged.get(2).getStart(), tagged.get(2).getEnd()),
                "апреля нерезиденты");
        Assert.assertEquals(str.substring(tagged.get(8).getStart(), tagged.get(8).getEnd()),
                "вывели");
    }

    @Test
    public void testEntityExtract10() {
        // GIVEN
        String str = "«Пока вопрос по снижению акцизов на бензин и дизельное топливо прорабатывается правительством, рост цен на бензин продолжится.";
        List<String> parts = helper.tokenize(str);

        // WHEN
        List<ExtInterval> tagged = helper.getNounAndVerbPhrases(str, parts.toArray(new String[parts.size()]));

        // THEN
        Assert.assertEquals(str.substring(tagged.get(0).getStart(), tagged.get(0).getEnd()),
                "вопрос");
        Assert.assertEquals(str.substring(tagged.get(3).getStart(), tagged.get(3).getEnd()),
                "прорабатывается");
        Assert.assertEquals(str.substring(tagged.get(7).getStart(), tagged.get(7).getEnd()),
                "продолжится");
    }

    @Test
    public void testIsTag() {
        // GIVEN
        String tag1 = "C";
        String tag3 = "I";
        String tag2 = "S";

        String tag4 = "D";
        String tag5 = "CC";
        String tag6 = "CS";

        // WHEN
        boolean isTag1 = helper.isTagDC(tag1);
        boolean isTag2 = helper.isTagDC(tag2);
        boolean isTag3 = helper.isTagDC(tag3);

        boolean isTag4 = helper.isTagDC(tag4);
        boolean isTag5 = helper.isTagDC(tag5);
        boolean isTag6 = helper.isTagDC(tag6);

        // THEN
        assertTrue(isTag1);
        assertTrue(isTag2);
        assertTrue(isTag3);

        assertFalse(isTag4);
        assertFalse(isTag5);
        assertFalse(isTag6);
    }

}
