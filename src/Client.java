import java.net.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.*;
import java.io.*;
import java.nio.charset.*;
import java.nio.ByteBuffer;
import p2putil.P2pUtil;
import p2putil.P2pUtilWaitNotify;

public class Client {
    Socket requestSocket      = null;                 //socket connect to the server
    ObjectOutputStream out    = null;                 //stream write to the socket
    ObjectInputStream in      = null;                 //stream read from the socket
    String message            = null;                 //message send to the server
    String serverAddr         = "127.0.0.1";          //Server address
    private static Integer memberId  = 0xffffffff;    //To be assigned by server during bootstraping
    String memberDirPath             = null;
    static String memberChunkDirPath = null;
    static String memberFinalDirPath = null;
    static String currentP2pFile     = null;
    static int noOfChunks            = 0;
    static int chunkId               = 0xffffffff;
    long fileSize                    = 0;
    static long lastChunkSize        = 0;
    private static final int maxNoOfNeigbor  = 5;
    final static long defaultChunkSize       = 100000;
    final static String defaultFilePath = "/home/gyan/Desktop/CNT/cnt-p2p/member_file_repo";
    private static HashMap<Integer, Boolean> chunkList   = new HashMap<Integer, Boolean>();
    private static boolean bootStrapDnldDone             = false;
    private static P2pUtilWaitNotify monitorDlNbr        = null;
    private static P2pUtilWaitNotify monitorChnkFromSvr  = null;
    public static String filePrefix  = ""; 
    public static String fileType    = "";

    public Client() {
        monitorDlNbr       = new P2pUtilWaitNotify();
        monitorChnkFromSvr = new P2pUtilWaitNotify();
    }

    private static class ClientAddress {
        String ip;
        Integer port;
        Integer memberId;

        public ClientAddress() {}
    }

    private static class ClientData {
        private static ArrayList<ClientAddress> cSocketList = new ArrayList<ClientAddress>();

        //public ClientData() {}

        public static synchronized void addClientSocket(ClientAddress cAddr) {
            synchronized(cSocketList) {
                cSocketList.add(cAddr);
                if(getNoOfMembers() == maxNoOfNeigbor) {
                    //notify all threads waiting on this event
                    monitorDlNbr.doNotifyAll();
                }
            }
        }

        public static synchronized ArrayList<ClientAddress> getNeighborTable() {
            ArrayList<ClientAddress> neighborTable = null;
            synchronized(cSocketList) {
                neighborTable = new ArrayList<ClientAddress>(cSocketList);
            }
            return neighborTable;
        }

        public static synchronized int getNoOfMembers() {
            int noOfMembers = 0;
            synchronized(cSocketList) {
                noOfMembers = cSocketList.size();
            }
            return noOfMembers;
        }

        @SuppressWarnings("unused")
        public static synchronized boolean removeClientSocket(ClientAddress cAddr){
            synchronized(cSocketList) {
                if(cSocketList.contains(cAddr)) {
                    cSocketList.remove(cAddr);
                    P2pUtil.p2p_log("<ClinetData: > Client removed from cSocketList");
                }
                else {
                    P2pUtil.p2p_log("<ClinetData: > Client not found in cSocketList");
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unused")
        public static synchronized byte[] serializeSocketList() {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ClientAddress> it = null;

            synchronized(cSocketList) {
                it = cSocketList.iterator();
                baos.write(cSocketList.size());
            }

            try {
                while(it.hasNext()) {
                    ClientAddress cAddr = (ClientAddress)it.next(); 
                    baos.write(cAddr.memberId & 0xff);
                    baos.write(cAddr.ip.getBytes());
                    baos.write((cAddr.port & 0x00ff));
                    baos.write((cAddr.port & 0xff00) >> 8);
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            //DEBUG
            /*for(int k = 0; k < baos.size(); k++) {
                System.out.format("baos[%d] 0x%x\n", k, baos.toByteArray()[k]);
            }*/
            return baos.toByteArray();
        }

        public static synchronized boolean deSerializeNeighborTable(byte[] ipS) {
            int i = 0, ip1 = 0, ip2 = 0, k = 0;
            byte x = (byte)0xff;

            //DEBUG
            /* for(int a = 0; a < ipS.length; a++) {
                System.out.format("ipS[%d] 0x%x\n", a, ipS[a]);
            } */

            synchronized(cSocketList) {
                cSocketList.clear();

                int noOfEntries = (int) ipS[i++];
                P2pUtil.p2p_log("<deSerializeNeighborTable: > No of entries " + noOfEntries);
                try {
                    while(k < noOfEntries) {
                        ClientAddress cAddr = new ClientAddress();
                        cAddr.memberId = (int)ipS[i++];

                        if(ipS[i] != x) {
                            P2pUtil.p2p_log("<deSerializeNeighborTable: > start ip stuff bits not found " + ipS[i] + " " + i);
                            return false;
                        }
                        else {
                            // skip stuff bits
                            i++;
                            // mark ip start bit
                            ip1 = i;
                        }

                        while((i < ipS.length) && (ipS[i] != x)) {
                            i++;
                        }

                        if(i == ipS.length) {
                            P2pUtil.p2p_log("<deSerializeNeighborTable: > end ip stuff bits not found");
                            return false;
                        }
                        else {
                            // mark ip end bits
                            ip2 = i;
                        }

                        //Copy ip from byte array to ClientAddress
                        byte[] ipBuf = new byte[ip2 - ip1];
                        System.arraycopy(ipS, ip1, ipBuf, 0, ip2 - ip1);
                        ByteBuffer ip = ByteBuffer.wrap(ipBuf);
                        cAddr.ip = new String(ip.array(), Charset.forName("US-ASCII"));
                        //increment i
                        i++;

                        //check for array out of bound
                        if(i == ipS.length || (i + 1) == ipS.length) {
                            P2pUtil.p2p_log("<deSerializeNeighborTable: > not enough bytes");
                            return false;
                        }

                        cAddr.port = (((int)ipS[i + 1] << 8) & 0x0000ff00) 
                                                | (((int)ipS[i]) & 0x000000ff);
                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.memberId " 
                                                                            + cAddr.memberId);
                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.ip " + cAddr.ip);
                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.port " + cAddr.port);

                        //update neigbor table
                        addClientSocket(cAddr);
                        //adjust i
                        i+=2;
                        //adjust k
                        k++;
                    }//end while
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                    return false;
                }
            }//synchronized(cSocketList)
            return true;
        } // deSerializeNeighborTable
    }//end ClinetData

    void run() {
        try {
            //create a socket to connect to the server
            requestSocket = new Socket(serverAddr, 8000);
            System.out.println("Connected to " + serverAddr + 
                " in port 8000" + " from port " + requestSocket.getLocalPort());
            //initialize inputStream and outputStream
            out = new ObjectOutputStream(requestSocket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(requestSocket.getInputStream());

            if(true) {
                P2pUtil.p2p_log("<Client.run: > Sending ip and port");

                //P2pUtil.sendMessage(out, "ip: 127.0.0.1 port:3001");
                P2pUtil.sendMessage(out, "ip: 127.0.0.1 port:" + 
                                            ServerT_.getServerAddress().port);

                //Wait for Member Id
                message = (String)in.readObject();
                P2pUtil.p2p_log("<Client.run: > Received message: " + message);

                if(message.toLowerCase().contains("member id")) {
                    //retrieve member id
                    parseStringForMemberId(message);
                    P2pUtil.p2p_log("<Client.run: > Server assigned member id " + memberId);

                    //Create member directory
                    memberDirPath      = new String(defaultFilePath + "_" + memberId);
                    memberChunkDirPath = new String(defaultFilePath + "_" + memberId
                                                    + "/chunks");
                    memberFinalDirPath = new String(defaultFilePath + "_" + memberId
                                                    + "/final");
                    P2pUtil.createDir(memberDirPath);
                    P2pUtil.createDir(memberChunkDirPath);
                    P2pUtil.createDir(memberFinalDirPath);

                    //Wait for neighbor update
                    waitForNeighborUpdate();

                    //Wait for chunks
                    waitForChunks();

                    //start download thread
                    while(ClientData.getNoOfMembers() < maxNoOfNeigbor) {
                        //wait for neighbor update
                        waitForNeighborUpdate();
                    }

                    //keep the thread alive
                    while(true);
                }
            }//end if
        }
        catch (ConnectException e) {
            System.err.println("<Client.run: > Connection refused. You need to initiate a server first.");
        } 
        catch (ClassNotFoundException e) {
            System.err.println("<Client.run: > Class not found");
        } 
        catch(UnknownHostException unknownHost) {
            System.err.println("<Client.run: > You are trying to connect to an unknown host!");
        }
        catch(IOException ioException) {
            ioException.printStackTrace();
        }
        finally {
            //Close connections
            try {
                in.close();
                out.close();
                requestSocket.close();
            }
            catch(IOException ioException) {
                ioException.printStackTrace();
            }
        }
    }

    //blocking wait for chunks
    public boolean waitForChunks() {
    boolean result = false;
        try {
            //wait for initiation from server
            String sFileName   = (String)in.readObject();
            P2pUtil.p2p_log("<waitForChunks: > Received message: " + sFileName);

            if(parseStringForFileName(sFileName)) {
                String sFileSize   = (String)in.readObject();
                P2pUtil.p2p_log("<waitForChunks: > Received message: " + sFileSize);

                if(parseStringForFileSize(sFileSize)) {
                    //Calculate no of chunks
                    noOfChunks = (int) (fileSize / defaultChunkSize);
                    if((fileSize % defaultChunkSize) != 0) {
                        noOfChunks++;
                        lastChunkSize = fileSize % defaultChunkSize;
                    }

                    synchronized(chunkList)
                    {
                        //initialize chunk-list
                        initChunkList();

                        updateFileInfo(currentP2pFile);

                        String chunk = (String)in.readObject();

                        while(!chunk.toLowerCase().contains("end")) {
                            P2pUtil.p2p_log("<waitForChunks: > Received message: " + chunk);
                            if(parseStringForChunkId(chunk)) {
                                P2pUtil.p2p_log("<waitForChunks: > Incoming Chunk Id " + chunkId);

                                byte[] buf = receiveByteStream(in);

                                try {
                                    if(buf.length != 0) {
                                        BufferedOutputStream bOs = new BufferedOutputStream(
                                        new FileOutputStream(memberChunkDirPath + "/" + 
                                                    filePrefix + "_" + chunkId + "." + fileType));
                                        P2pUtil.writeToOutputStream(buf, bOs);
                                        bOs.close();
                                    }
                                }
                                catch (Exception ex) {
                                    ex.printStackTrace();
                                }
                                chunkList.put(chunkId, true);
                                chunk = (String)in.readObject();
                            }//if(parseStringForChunkId()
                        }//while(!chunk.toLowerCase().contains("end"))
                    }//synchronized(chunkList)
                }//if(parseStringForFileSize()
            }//if(parseStringForFileName)
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        bootStrapDnldDone = true;
        monitorChnkFromSvr.doNotifyAll();
        result = true;
        return result;
    }

    //blocking wait for neigbor update
    public boolean waitForNeighborUpdate() {
        try {
            //wait for neighbor update
            message = (String)in.readObject();

            if(message.toLowerCase().contains("neighbor update")) {
                //Send acknowledgment to server
                P2pUtil.sendMessage(out, "go");

                P2pUtil.p2p_log("<waitForNeighborUpdate: > Waiting for neigbor update");
                byte[] neighborT = receiveByteStream(in);

                //DEBUG
                /*for(int k = 0; k < neighborT.length; k++) {
                    System.out.format("neighborT[%d] 0x%x\n", k, neighborT[k]);
                }*/

                if(!ClientData.deSerializeNeighborTable(neighborT)) {
                    P2pUtil.p2p_log("<waitForNeighborUpdate: > Could not deserilize neighbor table.");
                    return false;
                }
                return true;
            }
            else {
                P2pUtil.p2p_log("<waitForNeighborUpdate: > did not receive neighbor update. waiting");
                return false;
            }
        }
        catch(Exception ex) {
            //ex.printStackTrace();
            return false;
        }
    }

    public void parseStringForMemberId(String ip) {
        if(ip.toLowerCase().contains("member id")) {
            String tokens[] = ip.split("id:");
            memberId = Integer.parseInt(tokens[1].replaceAll("\\s+", ""));
        }
        else {
            memberId = 0xffffffff;
        }
    }

    public boolean parseStringForFileSize(String ip) {
        boolean result = false;

        if(ip.toLowerCase().contains("file size")) {
            String tokens[] = ip.split("size:");
            fileSize        = Integer.parseInt(tokens[1].replaceAll("\\s+", ""));
            result = true;
        }
        else {
            fileSize = 0x0;
        }

        return result;
    }

    public static boolean parseStringForChunkId(String ip) {
        boolean result = false;

        if(ip.toLowerCase().contains("chunk id")) {
            String tokens[] = ip.split("id:");
            chunkId         = Integer.parseInt(tokens[1].replaceAll("\\s+", ""));
            result = true;
        }

        return result;
    }

    public boolean parseStringForFileName(String ip) {
        boolean result = false;

        if(ip.toLowerCase().contains("file name")) {
            String tokens[] = ip.split("name:");
            currentP2pFile  = tokens[1].replaceAll("\\s+", "");
            result = true;
        }

        return result;
    }

    public void initChunkList() {
        //initialize chunk-list
        for(int k = 1; k <= noOfChunks; k++) {
            chunkList.put(k, false);
        }
    }

    public static byte[] receiveByteStream(ObjectInputStream inStream) {
        DataInputStream dis = new DataInputStream(inStream);
        byte[] data = null;
        try {
            int len = dis.readInt();
            data = new byte[len];
            if (len > 0) {
                dis.readFully(data);
            }
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }

        return data;
    }
    
    public static void updateFileInfo(String file) {
        //if the file ends with a file type then break
        String[] fTokens = currentP2pFile.split("\\.(?=[^\\.]+$)");
        String type = "_";

        if(fTokens.length == 1) {
            //file does not end with a type
            filePrefix = currentP2pFile;
        }
        else if(fTokens.length > 1){
            //take fileprefix from parsed string
            filePrefix = fTokens[0];
            //type found
            fileType = fTokens[fTokens.length - 1];
        }
    }

    private static class ServerT_ extends Thread {
        public static ServerSocket listener = null;
        public static boolean runSwitch = false;
        ObjectInputStream   inS;      //stream read from the socket
        ObjectOutputStream outS;      //stream write to the socket
        public static String rcvdReqFromUn;

        private static class ServerAddress {
            String ip;
            int port;
        }

        public ServerT_() {
            try {
                runSwitch = true;
                listener = new ServerSocket(0);
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }

        public void run() {
            Socket remoteC = null;
            int[] requestedChunks;
            String mapContent;

            ServerAddress s = getServerAddress();

            P2pUtil.p2p_log("<ServerT_.run: > Starting server in client "
                        + s.ip + " " + s.port);

            //listens to only one connections currently
            try {
                remoteC = listener.accept();

                P2pUtil.p2p_log("<ServerT_.run: > Connected to " 
                        + remoteC.getRemoteSocketAddress().toString());

                outS = new ObjectOutputStream(remoteC.getOutputStream());
                outS.flush();
                inS  = new ObjectInputStream(remoteC.getInputStream());

                while(runSwitch) {

                    //receive request from UN
                    rcvdReqFromUn = (String)inS.readObject();
                    System.out.format("<ServerT_.run: > UN sent \"%s\"\n", rcvdReqFromUn);

                    if(!rcvdReqFromUn.toLowerCase().contains("end session")) {
                        if(rcvdReqFromUn.toLowerCase().contains("requesting chunklist")) {
                            P2pUtil.p2p_log("<ServerT_.run: > UN sent chunk-list request");
                            synchronized(chunkList) {
                                //Send chunklist by converting hashmap to string
                                if(chunkList.size() != 0) {
                                    mapContent = P2pUtil.mapToString(chunkList);
                                }
                                else {
                                    mapContent = new String("uninitialized");
                                }
                                //send content back to the UN
                                P2pUtil.sendMessage(outS, mapContent);
                            }
                        }
                        else if(rcvdReqFromUn.toLowerCase().contains("requesting chunks")) {

                            P2pUtil.p2p_log("<ServerT_.run: > UN requesting for chunks");

                            requestedChunks = P2pUtil.parseChunkRequest(rcvdReqFromUn);

                            //DEBUG
                            /*for(int i = 0; i < requestedChunks.length; i++) {
                                P2pUtil.p2p_log(" " + requestedChunks[i]);
                            }*/

                            if(requestedChunks != null 
                                && requestedChunks.length != 0) {
                                //send chunks to Upload Neighbor
                                if(sendChunksToUN(requestedChunks)){
                                    P2pUtil.p2p_log("<ServerT_.run: > Sent requested" +
                                                        "chunks to UN");
                                }
                                else {
                                    P2pUtil.p2p_log("<ServerT_.run: > Requested chunks" +
                                                        "could not be sent to UN");
                                }
                            }
                            else {
                                P2pUtil.p2p_log("<ServerT_.run: > UN sent empty" + 
                                                                    "list of chunk req");
                            }
                        }
                    }//if(!rcvdReqChunkList.toLowercase().contains("end"))
                    else {
                        end();
                    }
                }//end while
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }
        }//end run

        public static ServerAddress getServerAddress() {
            ServerAddress sAddr = new ServerAddress();
            //TBD: we have a problem here
            sAddr.ip   = listener.getInetAddress().getHostAddress();
            //sAddr.ip   = listener.toString();
            sAddr.port = listener.getLocalPort(); 
            return sAddr;
        }

        public synchronized boolean sendChunksToUN(int[] requestedChunks) {
            int noOfChunksToSend = requestedChunks.length;
            int itr              = 0;
            byte[] buf           = null;
            RandomAccessFile raf = null;

            updateFileInfo(currentP2pFile);

            while(itr < noOfChunksToSend) {

                //open file and send content
                File fl = new File(memberChunkDirPath + 
                    "/" + filePrefix + "_" + requestedChunks[itr] + "." + fileType);

                if(!fl.exists()) {
                    P2pUtil.p2p_log("<sendChunksToUN: > Chunk file does not exist");
                    return false;
                }

                try {
                    synchronized(chunkList) {
                        raf = new RandomAccessFile(fl, "r");
                        if(requestedChunks[itr] < chunkList.size()) {
                            buf = new byte[(int)defaultChunkSize];
                        }
                        else if(requestedChunks[itr] == chunkList.size()){
                            buf = new byte[(int)lastChunkSize];
                        }
                        raf.read(buf);
                    }
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
                finally {
                    try {
                        if(raf != null) {
                            raf.close();
                        }
                    }
                    catch(IOException ioe)
                    {
                        ioe.printStackTrace();
                    }
                }

                P2pUtil.sendMessage(outS, "Chunk id: " + requestedChunks[itr]);
                P2pUtil.sendByteStream(outS, buf);
                itr++;
            }

            P2pUtil.sendMessage(outS, "end");

            return true;
        }

        public static void end() {
            P2pUtil.p2p_log("<ServerT_.end: > Exiting server thread");
            runSwitch = false;
        }
    }//end ServerT_

    private static class ClientT_ extends Thread {
        public static boolean runSwitch = false;

        public static String rcvChunkList;
        Socket reqSocket;                //socket connect to the server
        ObjectOutputStream outC;         //stream write to the socket
        ObjectInputStream inC;           //stream read from the socket
        Integer svrPort;
        List<Integer> missingChunks = new ArrayList<Integer>();

        public ClientT_() {
            runSwitch = true;
        }

        public void run() {

            try {
                //Wait till all 5 clients have joined or not
                monitorDlNbr.doWait();

                if(!bootStrapDnldDone) {
                    //Wait for all chunks from boot strap server
                    monitorChnkFromSvr.doWait();
                }
            }
            catch(Exception ex) {
                ex.printStackTrace();
            }

            //Connect to download neighbor
                //1. Choose appropriate download neighbor
                //2. Connect            

            P2pUtil.p2p_log("<ClientT_.run: > All the five clients joined");
            P2pUtil.p2p_log("<ClientT_.run: > Proceeding to connect to upload neigbor");

            //request and receive chunks
            try {
                ArrayList<ClientAddress> neighborTable = ClientData.getNeighborTable();

                for(ClientAddress neighbor : neighborTable) {
                    if(neighbor.memberId == (memberId % maxNoOfNeigbor) + 1) {
                        svrPort = neighbor.port;
                        break;
                    }
                }

                //create a socket to connect to the server
                P2pUtil.p2p_log("<ClientT_.run: > Connecting to download neighbour in port "
                                                            + svrPort);

                reqSocket = new Socket("127.0.0.1", svrPort);

                P2pUtil.p2p_log("<ClientT_.run: > Connected to download neighbour in port "
                                                            + svrPort);

                //initialize inputStream and outputStream
                outC = new ObjectOutputStream(reqSocket.getOutputStream());
                outC.flush();
                inC = new ObjectInputStream(reqSocket.getInputStream());

                while(runSwitch) {
                    //P2pUtil.p2p_log("Hello, please input a sentence: ");
                    //read a sentence from the standard input
                    //String check = buffrdRdr.readLine();
                    P2pUtil.p2p_log("<ClientT_.run: > Requesting chunk list from download neighbor "
                                            + ((memberId % maxNoOfNeigbor) + 1));
                    P2pUtil.sendMessage(outC, "Requesting chunklist");

                    //Receive the chunk list from DN
                    rcvChunkList = (String)inC.readObject();

                    //show the message to the user
                    P2pUtil.p2p_log("<ClinetT_.run: > Received chunk list from DN. ");
                    if(!rcvChunkList.toLowerCase().contains("uninitialized")) {
                        Map<Integer, Boolean> parsedMap = P2pUtil.stringToMap(rcvChunkList);

                        //DEBUG
                        /*Set<Integer> keysSet = chunkList.keySet();
                        for (Integer key : keysSet) {
                        P2pUtil.p2p_log("Key : " + key + " : Value : " + chunkList.get(key));
                        }*/

                        if(parsedMap.size() != 0)
                        {
                            synchronized(chunkList) {
                                missingChunks.clear();
                                for (Entry<Integer, Boolean> entry : chunkList.entrySet()) {
                                    Integer key = entry.getKey();
                                    if((entry.getValue() == false)
                                        && (parsedMap.get(key) == true)) {
                                        missingChunks.add(key);
                                    }
                                }

                                //DEBUG
                                /*int[] missingChunks = P2pUtil.convertIntegers(ints);
                                 * P2pUtil.p2p_log("<ClinetT_.run: > Chunks to be requested from download neighbor are:");
                                for(int i = 0; i < missingChunks.length; i++) {
                                    P2pUtil.p2p_log("Chunk " + missingChunks[i]);
                                }*/

                                if(missingChunks.size() != 0) {
                                    String listString = "Requesting chunks:";
                                    for (Integer s : missingChunks) {
                                        listString += s + ",";
                                    }

                                    //Send list of requested chunks to the download neigbor
                                    P2pUtil.sendMessage(outC, listString);
                                    //Wait for chunks from DN
                                    waitForChunksFromDN();

                                    //We need to stop the session if all chunks received
                                    if(P2pUtil.isAllChunksRecvd(chunkList)) {
                                        P2pUtil.p2p_log("<ClientT_.run: > Client has recieved all the chunks of "
                                        + "the file");
                                        P2pUtil.joinMiniFiles(memberFinalDirPath, memberChunkDirPath,
                                            filePrefix, filePrefix, fileType, noOfChunks);
                                        P2pUtil.p2p_log("<ClientT_.run: > File download successful.");
                                        //sending end string to DN
                                        P2pUtil.sendMessage(outC, "end session");
                                        end();
                                    }
                                }//if(ints.size() != 0)
                                else {
                                    P2pUtil.p2p_log("<ClientT_.run: > Duplicate chunklist");
                                }
                            }//end synchronized(chunkList)
                        }//if(parsedMap.size() != 0)
                    }//if(!rcvChunkList.equals(""))

                    //DEBUG
                    /*Set<Integer> keySet = chunkList.keySet();
                    for (Integer key : keySet) {
                        P2pUtil.p2p_log("Key : " + key + " : Value : " + 
                        chunkList.get(key));
                    }*/
                }//end while
            }
            catch(Exception exp) {
                exp.printStackTrace();
            }
        }

    public boolean waitForChunksFromDN() {
        boolean result = false;
        try {
            //wait for initiation from server
            updateFileInfo(currentP2pFile);

            String chunk = (String)inC.readObject();
            while(!chunk.toLowerCase().contains("end")) {
                P2pUtil.p2p_log("<waitForChunksFromDN: > Received message: " + chunk);
                if(parseStringForChunkId(chunk)) {
                    P2pUtil.p2p_log("<waitForChunksFromDN: > Incoming Chunk Id " + chunkId);

                    byte[] buf = receiveByteStream(inC);

                    try {
                        if(buf.length != 0) {
                            BufferedOutputStream bOs = new BufferedOutputStream(
                            new FileOutputStream(memberChunkDirPath + "/" + 
                                        filePrefix + "_" + chunkId + "." + fileType));
                            P2pUtil.writeToOutputStream(buf, bOs);
                            bOs.close();
                        }
                    }
                    catch (Exception ex) {
                        ex.printStackTrace();
                    }
                    chunkList.put(chunkId, true);
                    chunk = (String)inC.readObject();
                }
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        result = true;
        return result;
    }

        public static void end() {
            P2pUtil.p2p_log("<ClientT_.end > Exiting client thread");
            runSwitch = false;
        }
    }//end ClientT_

    //main method
    public static void main(String args[]) {
        Client client = new Client();
        ServerT_ srvr = new ServerT_();
        ClientT_ clnt = new ClientT_();

        srvr.start();
        clnt.start();
        client.run();
    }
}
