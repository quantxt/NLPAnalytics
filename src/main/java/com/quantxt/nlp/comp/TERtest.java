package com.quantxt.nlp.comp;

import java.io.*;
import java.util.regex.*;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;

public class TERtest {

    private static Logger logger = LoggerFactory.getLogger(TERtest.class);

    public static void main(String[] args) {
        // 1. process arguments
        String [] argss = {"-h" , "/Users/matin/Downloads/tercom-0.7.25/sample-data/hyp.txt" , "-r",
                "/Users/matin/Downloads/tercom-0.7.25/sample-data/ref.txt"};
        HashMap paras = TERpara.getOpts(argss);

        String ref_fn = (String) paras.get(TERpara.OPTIONS.REF);
        String hyp_fn = (String) paras.get(TERpara.OPTIONS.HYP);

//        String ref_fn = (String) paras.get("/Users/matin/Downloads/tercom-0.7.25/sample-data/ref.txt");
//        String hyp_fn = (String) paras.get("/Users/matin/Downloads/tercom-0.7.25/sample-data/hyp.txt");

        Object val = paras.get(TERpara.OPTIONS.NORMALIZE);
        boolean normalized = (Boolean) val;
        val = paras.get(TERpara.OPTIONS.CASEON);
        boolean caseon = (Boolean) val;
        val = paras.get(TERpara.OPTIONS.NOPUNCTUATION);
        boolean nopunct = (Boolean) val;
        val = paras.get(TERpara.OPTIONS.OUTPFX);
        String out_pfx;
        if (val != null)
            out_pfx = (String) val;
        else
            out_pfx = "";
        val = paras.get(TERpara.OPTIONS.FORMATS);

        ArrayList formats = new ArrayList();
        if (val != null)
            formats = (ArrayList) val;
        val = paras.get(TERpara.OPTIONS.BEAMWIDTH);
        int beam_width = (Integer) val;
        val = paras.get(TERpara.OPTIONS.REFLEN);
        String reflen_fn = (String) val;
        val = paras.get(TERpara.OPTIONS.TRANSSPAN);
        String span_pfx = (String) val;
        val = paras.get(TERpara.OPTIONS.SHIFTDIST);
        int shift_dist = (Integer) val;

        TERcost costfunc = new TERcost();
        costfunc._delete_cost = (Double) paras.get(TERpara.OPTIONS.DELETE_COST);
        costfunc._insert_cost = (Double) paras.get(TERpara.OPTIONS.INSERT_COST);
        costfunc._shift_cost = (Double) paras.get(TERpara.OPTIONS.SHIFT_COST);
        costfunc._match_cost = (Double) paras.get(TERpara.OPTIONS.MATCH_COST);
        costfunc._substitute_cost = (Double) paras.get(TERpara.OPTIONS.SUBSTITUTE_COST);

        // 2. init variables
        int in_ref_format;
        int in_hyp_format;
        double TOTAL_EDITS = 0.0;
        double TOTAL_WORDS = 0.0;

        BufferedReader hypstream;
        BufferedReader refstream;
        BufferedReader reflenstream = null;

        LinkedHashMap hypsegs;
        LinkedHashMap refsegs;
        LinkedHashMap reflensegs = null;
        HashMap refsids = null;
        HashMap reflenids = null;
        HashMap refspans = null;
        HashMap hypspans = null;
        TERsgml hypsgm = new TERsgml();
        TERsgml refsgm = new TERsgml();
        TERsgml reflensgm = null;
        Document hypdoc = hypsgm.parse(hyp_fn);
        Document refdoc = refsgm.parse(ref_fn);
        Document reflendoc = null;

        // 3. load inputs
        if (hypdoc == null) {
            hypsegs = load_segs(hyp_fn);
            logger.info("\"" + hyp_fn + "\" was successfully parsed as Trans text");
            in_hyp_format = 1;
        } else {
            hypsegs = new LinkedHashMap();
            if (hypdoc == null) logger.info("hyp doc is null");
            TERsgml.loadSegs(hypdoc, hypsegs);
            in_hyp_format = 2;
        }

        if (refdoc == null) {
            refsegs = load_segs(ref_fn);
            logger.info("\"" + ref_fn + "\" was successfully parsed as Trans text");
            in_ref_format = 1;
        } else {
            refsegs = new LinkedHashMap();
            refsids = new HashMap();
            if (refdoc == null) logger.info("ref doc is null");
            TERsgml.loadSegs(refdoc, refsegs, refsids);
            in_ref_format = 2;
        }

        if (reflen_fn != "") {
            reflensgm = new TERsgml();
            reflendoc = reflensgm.parse(reflen_fn);

            if (reflendoc == null) {
                reflensegs = load_segs(reflen_fn);
                logger.info("\"" + reflen_fn + "\" was successfully parsed as Trans text");
            } else {
                reflensegs = new LinkedHashMap();
                reflenids = new HashMap();
                TERsgml.loadSegs(reflendoc, reflensegs, reflenids);
            }
        }

        if (span_pfx != "") {
            has_span = true;
            refspans = load_trans_span(span_pfx + refspan_ext);
            hypspans = load_trans_span(span_pfx + hypspan_ext);
        }

        // 4. verify input formats
        if (!verifyFormats(in_ref_format, in_hyp_format, formats)) System.exit(1);

        // set options to compute TER
        TERcalc tcalc = new TERcalc();
        tcalc.setNormalize(normalized);
        tcalc.setCase(caseon);
        tcalc.setPunct(nopunct);
        tcalc.setBeamWidth(beam_width);
        tcalc.setShiftDist(shift_dist);

        // 5. prepare output streams, xml, pra, and ter
        BufferedWriter xml_out = openFile(formats, "xml", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);
        BufferedWriter pra_out = openFile(formats, "pra", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);
        BufferedWriter prm_out = openFile(formats, "pra_more", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);
        BufferedWriter ter_out = openFile(formats, "ter", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);
        BufferedWriter sum_out = openFile(formats, "sum", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);
        BufferedWriter nbt_out = openFile(formats, "sum_nbest", out_pfx, hyp_fn, ref_fn, reflen_fn, caseon);

        // 6. compute TER
        Pattern id_rank = Pattern.compile("^\\s*(.*):([^ ]*)\\s*$", Pattern.CASE_INSENSITIVE);
        // For each id
        // 	Map sorted_map = new TreeMap(hypsegs);
        // 	Iterator hypids = (sorted_map.keySet()).iterator();
        Iterator hypids = hypsegs.keySet().iterator();
        while (hypids.hasNext()) {
            String id_nrank = "";
            String rank = "";
            String id = (String) hypids.next();
            ArrayList hyps = (ArrayList) hypsegs.get(id);
            String hypspan = "";
            if (has_span) hypspan = (String) hypspans.get(id);
            Matcher id_rank_m = id_rank.matcher(id);

            if (id_rank_m.matches()) {
                id_nrank = id_rank_m.group(1);
                rank = id_rank_m.group(2);
            } else {
                id_nrank = id;
                id = id + ":1";
            }

	    /* Find set of refs */
            if (refsegs.containsKey(id_nrank)) {
                logger.info("Processing " + id);

                ArrayList refids;
                if (refsids != null && refsids.containsKey(id_nrank))
                    refids = (ArrayList) refsids.get(id_nrank);
                else
                    refids = new ArrayList(1);

                ArrayList reflenseglist = null;
                if (reflensegs != null) {
                    reflenseglist = (ArrayList) reflensegs.get(id_nrank);
                    if (reflenseglist == null)
                        System.out.println("Warning: NO reference length can be found for hyp: " + refids);
                }

                String refspan = "";
                if (has_span) refspan = (String) refspans.get(id_nrank);

                TERalignment result = score_all_refs((String) hyps.get(0),
                        (ArrayList) refsegs.get(id_nrank),
                        reflenseglist,
                        refids,
                        refspan,
                        hypspan,
                        costfunc);

                TOTAL_EDITS += result.numEdits;
                TOTAL_WORDS += result.numWords;
                // 6.1 write output files
                try {
                    if (ter_out != null)
                        ter_out.write(id + " " + result.numEdits + " " + result.numWords + " " + result.score() + "\n");
                    if (xml_out != null)
                        TERsgml.writeXMLAlignment(xml_out, result, id, (in_ref_format == 1));
                    if (pra_out != null)
                        pra_out.write("Sentence ID: " + id + "\n" + result.toString() + "\n\n");
                    if (prm_out != null)
                        prm_out.write("Sentence ID: " + id + "\n" + result.toMoreString() + "\n\n");
                    if (sum_out != null)
                        writeSummary(sum_out, result, id);
                    if (nbt_out != null)
                        writeNbestSum(nbt_out, result, id);
                } catch (IOException ioe) {
                    System.out.println(ioe);
                    return;
                }
            } else {
                System.out.println("***ERROR*** No reference for segment " + id_nrank);
                System.exit(1);
            }
        }

        closeFile(xml_out, "xml");
        closeFile(pra_out, "pra");
        closeFile(prm_out, "pra_more");
        closeFile(ter_out, "ter");
        closeFile(sum_out, "sum");
        closeFile(nbt_out, "sum_nbest");

        System.out.println("Total TER: " + (TOTAL_EDITS / TOTAL_WORDS) + " (" +
                TOTAL_EDITS + "/" + TOTAL_WORDS + ")");
        System.out.println("Number of calls to beam search: " +
                TERcalc.numBeamCalls());
        System.out.println("Number of segments scored: " +
                TERcalc.numSegsScored());
        System.out.println("Number of shifts tried: " +
                TERcalc.numShiftsTried());
    }

    public static BufferedWriter openFile(ArrayList formats,
                                          String type,
                                          String out_pfx,
                                          String hyp_fn,
                                          String ref_fn,
                                          String reflen_fn,
                                          boolean caseon) {
        BufferedWriter bw = null;
        if (out_pfx != "" && formats != null && formats.contains(type)) {
            try {
                bw = new BufferedWriter(new FileWriter(out_pfx + "." + type));
                if (type.equals("xml"))
                    TERsgml.writeXMLHeader(bw, hyp_fn, ref_fn, caseon);
                else if (type.equals("sum")) {
                    bw.write("Hypothesis File: " + hyp_fn + "\nReference File: " + ref_fn + "\n" +
                            "Ave-Reference File: " + ((reflen_fn == "") ? ref_fn : reflen_fn) + "\n");
                    bw.write(String.format("%1$-19s | %2$-4s | %3$-4s | %4$-4s | %5$-4s | %6$-4s | %7$-6s | %8$-8s | %9$-8s\n",
                            "Sent Id", "Ins", "Del", "Sub", "Shft", "WdSh", "NumEr", "NumWd", "TER"));
                    bw.write("-------------------------------------------------------------------------------------\n");
                } else if (type.equals("ter"))
                    bw.write("Hypothesis File: " + hyp_fn + "\nReference File: " + ref_fn + "\n");
            } catch (IOException ioe) {
                System.out.println(ioe);
            }
        }

        return bw;
    }

    public static void closeFile(BufferedWriter bw,
                                 String type) {

        if (bw != null) {
            try {
                if (type.equals("xml"))
                    TERsgml.writeXMLFooter(bw);
                else if (type.equals("sum")) {
                    bw.write("-------------------------------------------------------------------------------------\n");
                    bw.write(String.format("%1$-19s | %2$-4d | %3$-4d | %4$-4d | %5$-4d | %6$-4d | %7$-6.1f | %8$-8.3f | %9$-8.3f\n",
                            "TOTAL", tot_ins, tot_del, tot_sub, tot_sft, tot_wsf, tot_err, tot_wds, tot_err * 100.0 / tot_wds));
                }
                bw.close();
            } catch (IOException ioe) {
                System.out.println(ioe);
                return;
            }
        }
    }

    public static void writeSummary(BufferedWriter sum,
                                    TERalignment result,
                                    String id) {
        try {
            result.scoreDetails();

            sum.write(String.format("%1$-19s | %2$4d | %3$4d | %4$4d | %5$4d | %6$4d | %7$6.1f | %8$8.3f | %9$8.3f\n",
                    id, result.numIns, result.numDel, result.numSub, result.numSft, result.numWsf, result.numEdits, result.numWords, result.score() * 100.0));
            tot_ins += result.numIns;
            tot_del += result.numDel;
            tot_sub += result.numSub;
            tot_sft += result.numSft;
            tot_wsf += result.numWsf;
            tot_err += result.numEdits;
            tot_wds += result.numWords;

        } catch (IOException ioe) {
            System.out.println(ioe);
            return;
        }
    }

    public static void writeNbestSum(BufferedWriter nbt,
                                     TERalignment result,
                                     String id) {
        try {
            result.scoreDetails();

            nbt.write(String.format("%1$-19s %2$4d %3$4d %4$4d %5$4d %6$4d %7$6.1f %8$8.3f %9$8.3f\n",
                    id, result.numIns, result.numDel, result.numSub, result.numSft, result.numWsf, result.numEdits, result.numWords, result.score() * 100.0));

        } catch (IOException ioe) {
            System.out.println(ioe);
            return;
        }
    }

    // it will be more flexible to verify the input formats later.
    public static boolean verifyFormats(int in_ref_format,
                                        int in_hyp_format,
                                        ArrayList out_formats) {
        if (in_ref_format != in_hyp_format) {
            System.out.println("** Error: Both hypothesis and reference have to be in the SAME format");
            return false;
        } else if (in_ref_format == 1 && out_formats.indexOf("xml") > -1) {
            System.out.println("** Warning: XML ouput may not have correct doc id for Trans format inputs");
            return true;
        } else
            return true;
    }

    public static TERalignment score_all_refs(String hyp,
                                              List refs,
                                              List reflens,
                                              List refids,
                                              String refspan,
                                              String hypspan,
                                              TERcost costfunc) {
        double totwords = 0;
        String ref;
        String refid = "";
        String bestref = "";
        String reflen = "";

        TERalignment bestresult = null;

        if (has_span && refs.size() > 1) {
            System.out.println("Error, translation spans should only be used with SINGLE reference");
            System.exit(1);
        }

        TERcalc tcalc = new TERcalc();

        tcalc.setRefLen(reflens);
    /* For each reference, compute the TER */
        for (int i = 0; i < refs.size(); ++i) {
            ref = (String) refs.get(i);
            if (!refids.isEmpty())
                refid = (String) refids.get(i);

            if (has_span) {
                tcalc.setRefSpan(refspan);
                tcalc.setHypSpan(hypspan);
            }

            TERalignment result = tcalc.TER(hyp, ref, costfunc);

            if ((bestresult == null) || (bestresult.numEdits > result.numEdits)) {
                bestresult = result;
                if (!refids.isEmpty()) bestref = refid;
            }

            totwords += result.numWords;
        }
        bestresult.numWords = ((double) totwords) / ((double) refs.size());
        if (!refids.isEmpty()) bestresult.bestRef = bestref;
        return bestresult;
    }


    public static LinkedHashMap load_segs(String fn) {
        Pattern p = Pattern.compile("^\\s*(.*?)\\s*\\(([^()]+)\\)\\s*$");
        BufferedReader stream;
        LinkedHashMap segs = new LinkedHashMap();

        try {
            stream = new BufferedReader(new FileReader(fn));
        } catch (IOException ioe) {
            System.out.println(ioe);
            return null;
        }

        try {
            String line;
            while ((line = stream.readLine()) != null) {
                if (line.matches("^\\s*$")) {
                    continue;
                }
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String text = m.group(1);
                    String id = m.group(2);
                    Object val = segs.get(id);
                    ArrayList al;
                    if (val == null) {
                        al = new ArrayList(6);
                        segs.put(id, al);
                    } else {
                        al = (ArrayList) val;
                    }

                    al.add(text.trim());
                } else {
                    System.out.println("Warning, Invalid line: " + line);
                }
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
            return null;
        }
        return segs;
    }

    private static HashMap load_trans_span(String fn) {
        Pattern p = Pattern.compile("^\\s*(.*?)\\s*\\(([^()]+)\\)\\s*$");
        BufferedReader stream;
        HashMap spans = new HashMap();

        try {
            stream = new BufferedReader(new FileReader(fn));

            String line;
            while ((line = stream.readLine()) != null) {
                if (line.matches("^\\s*$")) {
                    continue;
                }
                Matcher m = p.matcher(line);
                if (m.matches()) {
                    String text = m.group(1);
                    String id = m.group(2);
                    Object val = spans.get(id);
                    if (val == null) {
                        spans.put(id, text);
                    } else {
                        System.out.println("Error, translation spans should only be used with SINGLE reference");
                        System.exit(1);
                    }
                } else {
                    System.out.println("Warning, Invalid line: " + line);
                }
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
            return null;
        }

        return spans;
    }

    private static int tot_ins = 0;
    private static int tot_del = 0;
    private static int tot_sub = 0;
    private static int tot_sft = 0;
    private static int tot_wsf = 0;
    private static float tot_err = 0;
    private static float tot_wds = 0;
    private static String refspan_ext = ".ref";
    private static String hypspan_ext = ".hyp";
    private static boolean has_span = false;
}
