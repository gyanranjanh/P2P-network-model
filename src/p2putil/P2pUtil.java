package p2putil;

import java.io.*;
import java.io.File;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.*;
import java.util.*;

public class P2pUtil {

    public P2pUtil() {}
    
    public static void p2p_log(String s) {
        String[] tokens = s.split("\n");

        for(int i = 0; i < tokens.length; i++) {
            System.out.print(tokens[i] + '\n');
        }

        if(tokens.length == 0) {
            System.out.print(s + '\n');
        }
    }

    public static boolean createDir(String dirPath) {
        File dir = new File(dirPath);

        if(!dir.exists()) {
            if(!dir.mkdir()) {
                p2p_log("<createDir> Can't create dirPath");
                return false;
            }
        }
        else {
            p2p_log("<createDir> directory " + dirPath + " already exists");
        }
        return true;
    }

    public static void sendMessage(ObjectOutputStream out, String msg) {
        try {
            //stream write the message
            out.writeObject(msg);
            out.flush();
            if(!((msg.contains("=true")) || (msg.contains("=false")))) {
                p2p_log("<sendMessage: > Send message: " + msg);
            }
        } catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static void sendByteStream(ObjectOutputStream out, byte[] msg) {
        try  {
            DataOutputStream dos = new DataOutputStream(out);
            dos.writeInt(msg.length);
            dos.write(msg);
            out.flush();
            dos.flush();
            P2pUtil.p2p_log("<sendByteStream: byte[] > message sent to Client ");
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
    }

    public static void readWrite(RandomAccessFile raf, 
                        BufferedOutputStream bOf, int noOfBytes) 
            throws IOException {
        byte[] buf = new byte[noOfBytes];
        
        int ret = raf.read(buf);
        if(ret != -1) {
            bOf.write(buf);
        }
    }

    public static void writeToOutputStream(byte[] buf, BufferedOutputStream bOf)
            throws IOException {
            bOf.write(buf);
    }

    public static boolean joinMiniFiles(String joinFilePath,
                                        String chunkFilePath,
                                        String filePrefix,
                                        String fileName,
                                        String type, 
                                        int nChunks) 
                        throws IOException {
        File targetFile = new File(joinFilePath + "/" + fileName + "." + type);
        BufferedOutputStream bOs = new BufferedOutputStream(
                                        new FileOutputStream(targetFile));
        int ret;
        
        for(int fIdx = 1; fIdx <= nChunks; fIdx++) {
            File test = new File(chunkFilePath + "/" 
                                + filePrefix + "_" + fIdx + "." + type);
            if(test.exists()) {
                RandomAccessFile raf = new RandomAccessFile(test.toString(), "r");
                BufferedInputStream bIs = new BufferedInputStream(
                                                new FileInputStream(test));

                byte[] miniBuf = new byte[(int)raf.length()];

                ret = bIs.read(miniBuf);
                if(ret == -1) {
                    P2pUtil.p2p_log("<joinMiniFiles: > Mini chunkfile read failed");
                    raf.close();
                    bIs.close();
                    bOs.close();
                    return false;
                }
                else {
                    // Write to bOs
                    bOs.write(miniBuf);
                }
                raf.close();
                bIs.close();
            }
            else {
                P2pUtil.p2p_log("<joinMiniFiles: > Missing mini chunkfiles" + test.toString());
                bOs.close();
                return false;
            }
        }
        bOs.close();
        return true;
    }

    public static String mapToString(Map<Integer, Boolean> map) {
        StringBuilder stringBuilder = new StringBuilder();
        for (Integer key : map.keySet()) {
                if (stringBuilder.length() > 0) {
                stringBuilder.append("&");
            }
            String value = map.get(key).toString();
            try {
                stringBuilder.append((key != null ?
                URLEncoder.encode(key.toString(), "UTF-8") : ""));
                stringBuilder.append("=");
                stringBuilder.append(value != null ?
                URLEncoder.encode(value, "UTF-8") : "");
            }
            catch (UnsupportedEncodingException e) {
                throw new RuntimeException(
                "This method requires UTF-8 encoding support", e);
            }
        }

        return stringBuilder.toString();
    }

    public static Map<Integer, Boolean> stringToMap(String input) {
        Map<Integer, Boolean> map = new HashMap<Integer, Boolean>();
        String newKey           = "";
        String newValue         = "";

        if(input.length() != 0) {
            String[] nameValuePairs = input.split("&");
            for (String nameValuePair : nameValuePairs) {
                String[] nameValue = nameValuePair.split("=");
                try {
                    newKey = URLDecoder.decode(nameValue[0], "UTF-8");

                    if(nameValue.length > 1) {
                        newValue = URLDecoder.decode(nameValue[1], "UTF-8");
                    }

                    Integer intKey     = Integer.parseInt(newKey);
                    Boolean booleanVal = Boolean.valueOf(newValue);
                    map.put(intKey, booleanVal);
                }
                catch (UnsupportedEncodingException e) {
                    throw new RuntimeException("This method requires UTF-8 encoding support", e);
                }
            }
        }

        return map;
    }

    public static int[] convertIntegers(List<Integer> integers) {
        int[] ret = new int[integers.size()];
        Iterator<Integer> iterator = integers.iterator();
        int i = 0;

        while(iterator.hasNext()) {
            ret[i++] = iterator.next().intValue();
        }
        return ret;
    }

    public static int[] parseChunkRequest(String reqString) {
        String chunkString = reqString.substring(reqString.indexOf("chunks"));
        String[] tokens = chunkString.split("chunks:");
        int[] rChunks = null;

        if(tokens.length == 2) {
            String[] str = tokens[1].split(",");
            if(str.length != 0) {
                ArrayList<String> chnks = new ArrayList<String>();
                ArrayList<Integer> numChunks = new ArrayList<Integer>();

                for(int i = 0; i < str.length; i++) {
                    chnks.add(str[i]);
                }

                for (String chunk : chnks) { 
                    numChunks.add(Integer.parseInt(chunk)); 
                }

                rChunks = convertIntegers(numChunks);
            }
        }
        
        return rChunks;  
    }

    public static boolean isAllChunksRecvd(Map<Integer, Boolean> chunkList) {
        boolean flag = true;

        for(Boolean value : chunkList.values()) {
            if(value == false) {
                flag = false;
                break;
            }
        }

        return flag;
    }
}
