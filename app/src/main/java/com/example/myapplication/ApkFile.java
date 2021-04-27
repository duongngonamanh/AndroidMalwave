package com.example.myapplication;

import android.os.Environment;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.ClassDef;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class ApkFile {
    private String path;
    public static String spaces = "                                             ";
    public static int endDocTag = 0x00100101;
    public static int startTag = 0x00100102;
    public static int endTag = 0x00100103;

    public ApkFile(String path) {
        this.path = path;
    }

    public HashSet<String> getApis() throws IOException {
        HashSet<String> output = new HashSet<>();
        File file = new File(path);
        DexBackedDexFile d = DexFileFactory.loadDexFile(file, null);
        for (ClassDef c : d.getClasses()) {
            String[] tmp = c.getMethods().toString().split(",");
            for (String s : tmp) {
                s = s.replaceAll("\\(.*$", "").replaceAll(";->", ".").replaceAll("\\$|<|>|\\[|]", "").replace("/", ".").trim().replaceAll("^L", "");
                output.add(s.trim());
            }

        }
        return output;
    }

    public HashSet<String> getPermissions() throws IOException {
        InputStream is = null;
        ZipFile zip = null;
        long fileSize = 0;
        if (path.endsWith(".apk") || path.endsWith(".zip")) {

            zip = new ZipFile(path);
            ZipEntry mft = zip.getEntry("AndroidManifest.xml");
            is = zip.getInputStream(mft);
            fileSize = mft.getSize();

        } else is = new FileInputStream(path);

        byte[] buf = new byte[(int) fileSize];
        int bytesRead = is.read(buf);

        is.close();
        if (zip != null) zip.close();

        String xml = decompressXML(buf);
        Document doc = convertStringToXMLDocument( xml );
        NodeList permissions = doc.getElementsByTagName("uses-permission");
        HashSet<String> output = new HashSet<>();
        for (int i = 0; i < permissions.getLength(); i++) {
            String per = permissions.item(i).getAttributes().getNamedItem("name").getNodeValue().toUpperCase();

            String[] tmp = per.split("\\.");
            per = tmp[tmp.length - 1];
            output.add(per);
            System.out.println(per);
        }
        return output;
    }

    private static Document convertStringToXMLDocument(String xmlString)
    {
        //Parser that produces DOM object trees from XML content
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

        //API to obtain DOM Document instance
        DocumentBuilder builder = null;
        try
        {
            //Create DocumentBuilder with default configuration
            builder = factory.newDocumentBuilder();

            //Parse the content to Document object
            Document doc = builder.parse(new InputSource(new StringReader(xmlString)));
            return doc;
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
        return null;
    }

    public static String decompressXML(byte[] xml) {
        StringBuilder finalXML = new StringBuilder();

        int numbStrings = LEW(xml, 4 * 4);

        int sitOff = 0x24; // Offset of start of StringIndexTable

        int stOff = sitOff + numbStrings * 4; // StringTable follows

        int xmlTagOff = LEW(xml, 3 * 4); // Start from the offset in the 3rd

        for (int ii = xmlTagOff; ii < xml.length - 4; ii += 4) {
            if (LEW(xml, ii) == startTag) {
                xmlTagOff = ii;
                break;
            }
        }

        int off = xmlTagOff;
        int indent = 0;
        int startTagLineNo = -2;
        while (off < xml.length) {
            int tag0 = LEW(xml, off);
            // int tag1 = LEW(xml, off+1*4);
            int lineNo = LEW(xml, off + 2 * 4);
            // int tag3 = LEW(xml, off+3*4);
            int nameNsSi = LEW(xml, off + 4 * 4);
            int nameSi = LEW(xml, off + 5 * 4);

            if (tag0 == startTag) { // XML START TAG
                int tag6 = LEW(xml, off + 6 * 4); // Expected to be 14001400
                int numbAttrs = LEW(xml, off + 7 * 4); // Number of Attributes
                // to follow
                // int tag8 = LEW(xml, off+8*4); // Expected to be 00000000
                off += 9 * 4; // Skip over 6+3 words of startTag data
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                // tr.addSelect(name, null);
                startTagLineNo = lineNo;

                // Look for the Attributes
                StringBuffer sb = new StringBuffer();
                for (int ii = 0; ii < numbAttrs; ii++) {
                    int attrNameNsSi = LEW(xml, off); // AttrName Namespace Str
                    // Ind, or FFFFFFFF
                    int attrNameSi = LEW(xml, off + 1 * 4); // AttrName String
                    // Index
                    int attrValueSi = LEW(xml, off + 2 * 4); // AttrValue Str
                    // Ind, or
                    // FFFFFFFF
                    int attrFlags = LEW(xml, off + 3 * 4);
                    int attrResId = LEW(xml, off + 4 * 4); // AttrValue
                    // ResourceId or dup
                    // AttrValue StrInd
                    off += 5 * 4; // Skip over the 5 words of an attribute

                    String attrName = compXmlString(xml, sitOff, stOff,
                            attrNameSi);
                    String attrValue = attrValueSi != -1 ? compXmlString(xml,
                            sitOff, stOff, attrValueSi) : "resourceID 0x"
                            + Integer.toHexString(attrResId);
                    sb.append(" " + attrName + "=\"" + attrValue + "\"");
                }
                finalXML.append("<" + name + sb + ">");
                prtIndent(indent, "<" + name + sb + ">");
                indent++;

            } else if (tag0 == endTag) { // XML END TAG
                indent--;
                off += 6 * 4; // Skip over 6 words of endTag data
                String name = compXmlString(xml, sitOff, stOff, nameSi);
                finalXML.append("</" + name + ">");
                prtIndent(indent, "</" + name + "> (line " + startTagLineNo
                        + "-" + lineNo + ")");
            } else if (tag0 == endDocTag) { // END OF XML DOC TAG
                break;

            } else {
                System.err.println("  Unrecognized tag code '" + Integer.toHexString(tag0)
                        + "' at offset " + off);
                break;
            }
        }
        return finalXML.toString();
    }

    public static String compXmlString(byte[] xml, int sitOff, int stOff, int strInd) {
        if (strInd < 0)
            return null;
        int strOff = stOff + LEW(xml, sitOff + strInd * 4);
        return compXmlStringAt(xml, strOff);
    }

    public static void prtIndent(int indent, String str) {
        System.err.println(spaces.substring(0, Math.min(indent * 2, spaces.length())) + str);
    }

    // compXmlStringAt -- Return the string stored in StringTable format at
    // offset strOff. This offset points to the 16 bit string length, which
    // is followed by that number of 16 bit (Unicode) chars.
    public static String compXmlStringAt(byte[] arr, int strOff) {
        int strLen = arr[strOff + 1] << 8 & 0xff00 | arr[strOff] & 0xff;
        byte[] chars = new byte[strLen];
        for (int ii = 0; ii < strLen; ii++) {
            chars[ii] = arr[strOff + 2 + ii * 2];
        }
        return new String(chars); // Hack, just use 8 byte chars
    } // end of compXmlStringAt

    // LEW -- Return value of a Little Endian 32 bit word from the byte array
    // at offset off.
    public static int LEW(byte[] arr, int off) {
        return arr[off + 3] << 24 & 0xff000000 | arr[off + 2] << 16 & 0xff0000
                | arr[off + 1] << 8 & 0xff00 | arr[off] & 0xFF;
    }
}
