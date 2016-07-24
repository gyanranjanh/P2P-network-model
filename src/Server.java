import java.net.*;
import java.io.*;
import java.nio.*;
import java.nio.file.*;
import java.nio.charset.*;
import java.util.*;
import java.io.ByteArrayOutputStream;
import p2putil.P2pUtil;
import p2putil.P2pUtilWaitNotify;

public class Server {
    private static final int sPort           = 8000; //The server will be listening on this port number
    private static final int maxNoOfNeigbor  = 5;
    //private static final int memberId        = 1;
    final static String defaultFilePath      = 
                                "/home/gyan/Desktop/CNT/cnt-p2p/server_file_repo";
    final static String defaultChunkFilePath = 
                                "/home/gyan/Desktop/CNT/cnt-p2p/server_file_repo/chunkfiles";
    final static String defaultJoinFilePath  = 
                                "/home/gyan/Desktop/CNT/cnt-p2p/server_file_repo/joinFileDir";
    final static long defaultChunkSize       = 100000;
    static Integer clientNum                 = 1;
    static int noOfChunks                    = 0;
    static long fileSize                     = 0;
    static long lastChunkSize                = 0;
    static String currentP2pFile             = null;
    private static HashMap<Integer, Boolean> chunkList = new HashMap<Integer, Boolean>();
    private static P2pUtilWaitNotify monitor = null;

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
            }
            monitor.doNotifyAll();
        }//end addClientSocket

        public static synchronized boolean removeClientSocket(ClientAddress cAddr) {
            if(cSocketList.contains(cAddr)) {
                cSocketList.remove(cAddr);
                P2pUtil.p2p_log("<ClinetData: > Client removed from cSocketList");
                monitor.doNotifyAll();
            }
            else {
                P2pUtil.p2p_log("<ClinetData: > Client not found in cSocketList");
                return false;
            }
            return true;
        }//end removeClientSocket

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
                    //use stuff bits to delimit ip boundaries
                    baos.write(0xff);
                    baos.write(cAddr.ip.getBytes());
                    baos.write(0xff);
                    baos.write((cAddr.port & 0x00ff));
                    baos.write((cAddr.port & 0xff00) >> 8);
                }
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            //DEBUG
            /*
            for(int k = 0; k < baos.size(); k++) {
                System.out.format("baos[%d] 0x%x\n", k, baos.toByteArray()[k]);
            }*/

            return baos.toByteArray();
        }//end serializeSocketList

        @SuppressWarnings("unused")
        public static synchronized boolean deSerializeNeighborTable(byte[] ipS) {
            int i = 0, ip1 = 0, ip2 = 0, k = 0;
            byte x = (byte)0xff;

            synchronized(cSocketList) {
                cSocketList.clear();

                int noOfEntries = (int) ipS[i++];
                P2pUtil.p2p_log("<deSerializeNeighborTable: > No of entries " + noOfEntries);
                try {
                    while(k < noOfEntries) {
                        ClientAddress cAddr = new ClientAddress();
                        cAddr.memberId = (int)ipS[i++];

                        if(ipS[i] != x) {
                            P2pUtil.p2p_log("<deSerializeNeighborTable: > start ip"
                                        + "stuff bits not found " + ipS[i] + " " + i);
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

                        cAddr.port = ((int)(ipS[i + 1] << 8)) | (((int)ipS[i]) & 0x000000ff);

                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.memberId "
                                                                        + cAddr.memberId);
                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.ip " + cAddr.ip);
                        P2pUtil.p2p_log("<deSerializeNeighborTable: > cAddr.port " + cAddr.port);


                        cSocketList.add(cAddr);
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

    /**
    * A handler thread class.  Handlers are spawned from the listening
    * loop and are responsible for dealing with a single client's requests.
    */
    private static class Handler extends Thread {
        private String message;            //message received from the client
        private Socket connection;
        private ObjectInputStream in;      //stream read from the socket
        private ObjectOutputStream out;    //stream write to the socket
        private int no;                    //The index number of the client
        private ClientAddress cAddr;
        private boolean runSwitch = false;

        public Handler(Socket connection, int no) {
            this.connection = connection;
            this.no         = no;
            this.runSwitch  = true;
        }

        public void run() {
            try {
                //initialize Input and Output streams
                out = new ObjectOutputStream(connection.getOutputStream());
                out.flush();
                in = new ObjectInputStream(connection.getInputStream());
                // Send filename, filesize, 
                try {
                    // Wait for client's listen IP and port
                    message = (String)in.readObject();
                    // parse message 
                    if(message.toLowerCase().contains("ip:") 
                            && message.toLowerCase().contains("port:")) {
                        //parse and retieve ip and port
                        parseRemoteAddress(message.toLowerCase());

                        //send member id
                        P2pUtil.sendMessage(out, "member id: " + no);
                        P2pUtil.p2p_log("<Handler.run: > to client " + no);

                        //fill memberId and create entry in client-socket list
                        cAddr.memberId = no;
                        ClientData.addClientSocket(cAddr);

                        //Send neighbor table
                        P2pUtil.sendMessage(out, "Neighbor Update ");
                        P2pUtil.p2p_log("<Handler.run: > to client " + no);

                        //wait for go from client
                        message = (String)in.readObject();
                        if(message.toLowerCase().contains("go")) {
                            byte[] neighborT = ClientData.serializeSocketList();
                            if(neighborT.length > 0) {
                                P2pUtil.p2p_log("<Handler.run: > Sending neighbor table to client "
                                                                + no);
                                P2pUtil.sendByteStream(out, neighborT);
                            }
                        }

                        //Send chunks if left with chunks
                        if(sendChunks()) {
                            P2pUtil.p2p_log("<Handler.run: > Chunks sent to client " + no);
                        }
                    }
                    else {
                        //send message close conn
                        P2pUtil.sendMessage(out, 
                            "sever did not receive expected info. closing conn");
                        P2pUtil.p2p_log("<Handler.run: > to client " + no);
                        connection.close();
                        this.end();
                    }
                }
                catch(Exception ex) {
                    ex.printStackTrace();
                }

                try {
                    while(runSwitch) {
                        //wait till a new client connects/disconnects
                        monitor.doWait();

                        //Send neighbor table
                        P2pUtil.sendMessage(out, "Neighbor Update ");
                        P2pUtil.p2p_log("<Handler.run: > to client " + no);

                        //wait for go from client
                        message = (String)in.readObject();
                        if(message.toLowerCase().contains("go")) {
                            byte[] neighborT = ClientData.serializeSocketList();
                            if(neighborT.length > 0) {
                                P2pUtil.p2p_log("<Handler.run: > Sending neighbor table to client " + no);
                                P2pUtil.sendByteStream(out, neighborT);
                            }
                        }
                    }
                }
                catch(ClassNotFoundException classnot) {
                    P2pUtil.p2p_log("<Handler: > Data received in unknown format");
                }
            }
            catch(IOException ioException) {
                P2pUtil.p2p_log("<Handler: > Disconnect with Client " + no);
                synchronized(clientNum) {
                    clientNum--;
                }
                //remove client if already sent
                ClientData.removeClientSocket(cAddr);
            }
            finally {
                //Close connections
                try {
                    in.close();
                    out.close();
                    connection.close();
                }
                catch(IOException ioException) {
                    P2pUtil.p2p_log("<Handler: > Disconnect with Client " + no);
                }
            }
        } /* end run */

        public void end() {
            P2pUtil.p2p_log("<Handler.run: > Exiting server thread");
            runSwitch = false;
        } //end end

        public synchronized boolean sendChunks() {
            int noOfChunksToSend = noOfChunks/maxNoOfNeigbor;
            int itr = 1, count = 0;
            byte[] buf           = null;
            RandomAccessFile raf = null;

            P2pUtil.sendMessage(out, "File name: " + currentP2pFile);
            P2pUtil.sendMessage(out, "File size: " + fileSize);

            //if the file ends with a file type then break
            String[] fTokens = currentP2pFile.split("\\.(?=[^\\.]+$)");
            String type = "_";

            if(fTokens.length == 0) {
                //file does not end with a type
                fTokens[0] = currentP2pFile;
            }
            else {
                //type found
                type = fTokens[fTokens.length - 1];
            }

            while(itr <= chunkList.size()) {
                if(count == noOfChunksToSend && no != maxNoOfNeigbor) {
                    break;
                }

                if(!chunkList.get(itr)) {
                    //open file and send content
                    File fl = new File(defaultChunkFilePath + 
                        "/" + fTokens[0] + "_" + itr + "." + type);

                    if(!fl.exists()) {
                        P2pUtil.p2p_log("<sendChunks: > Chunk file does not exist");
                        return false;
                    }

                    try {
                        raf = new RandomAccessFile(fl, "r");
                        if(itr < chunkList.size()) {
                            buf = new byte[(int)defaultChunkSize];
                        }
                        else if(itr == chunkList.size()){
                            buf = new byte[(int)lastChunkSize];
                        }
                        raf.read(buf);
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

                    P2pUtil.sendMessage(out, "Chunk id: " + itr);
                    P2pUtil.sendByteStream(out, buf);
                    chunkList.put(itr, true);
                    count++;
                }
                itr++;
            }

            P2pUtil.sendMessage(out, "end");

            return true;
        }

        public void parseRemoteAddress(String ipString) {
            cAddr = new ClientAddress();

            String portString = ipString.substring(ipString.indexOf("port"));
            P2pUtil.p2p_log("<parseRemoteAddress: > Port string is " + portString);
            String[] tokens = portString.split("port:");
            P2pUtil.p2p_log("<parseRemoteAddress: > Port is " + tokens[1]);
            int portn = Integer.parseInt(tokens[1].replaceAll("\\s+", ""));
            P2pUtil.p2p_log("<parseRemoteAddress: > Port is " + portn);
            
            String ipsString = ipString.substring(
                                   ipString.indexOf("ip"), ipString.indexOf("port"));
            P2pUtil.p2p_log("<parseRemoteAddress: > Ip string is " + ipsString);
            tokens = ipsString.split("ip:");
            String ip = tokens[1].replaceAll("\\s+", "");
            P2pUtil.p2p_log("<parseRemoteAddress: > Ip is " + ip);

            cAddr.ip       = ip;
            cAddr.port     = portn;
            cAddr.memberId = 0xff;
        }
    } /* End Handler */

    public static boolean splitFile(File fl) {
        RandomAccessFile raf = null;

        try {
            P2pUtil.p2p_log("<splitFile: > File to Split " + fl.getParent().substring(0));

            String[] tokens = fl.toString().split("/");
            
            if(tokens.length == 0) {
                tokens = fl.toString().split("\\");
            }

            if(tokens.length == 0) {
                P2pUtil.p2p_log("<splitFile: > File name not in proper format");
                return false;
            }

            P2pUtil.p2p_log("<splitFile: > No of tokens " + tokens.length + '\n');

            //instantiate raf
            raf = new RandomAccessFile(fl, "r");
            fileSize = raf.length();
            P2pUtil.p2p_log("<splitFile: > File size is " + fileSize + '\n');

            noOfChunks = (int) (fileSize / defaultChunkSize);
            if((fileSize % defaultChunkSize) != 0) {
                noOfChunks++;
                lastChunkSize = fileSize % defaultChunkSize;
            }

            P2pUtil.p2p_log("<splitFile: > No of chunks " + noOfChunks + '\n');
            P2pUtil.p2p_log("<splitFile: > Last chunk size " + lastChunkSize);

            //if the file ends with a file type then break
            String[] fTokens = tokens[tokens.length - 1].split("\\.(?=[^\\.]+$)");
            String type = "_";

            if(fTokens.length == 1) {
                //file does not end with a type
                fTokens[0] = tokens[tokens.length - 1];
            }
            else if(fTokens.length > 1) {
                //type found
                type = fTokens[fTokens.length - 1];
            }

            P2pUtil.p2p_log("<splitFile: > Abs file " + fTokens[0] + "." + type);
            currentP2pFile = new String(fTokens[0] + "." + type);

            //Create dir to store chunk files
            P2pUtil.createDir(defaultChunkFilePath);

            // create mini files from chunks
            for(int fIdx = 1; fIdx <= noOfChunks; fIdx++) {
                BufferedOutputStream bOs = new BufferedOutputStream(
                            new FileOutputStream(defaultChunkFilePath + 
                            "/" + fTokens[0] + "_" + fIdx + "." + type));
                if(fIdx != noOfChunks) {
                    //write 100K to the mini files
                    P2pUtil.readWrite(raf, bOs, (int) defaultChunkSize);
                }
                else {
                    // write lastChunkSize to the last mini file
                    P2pUtil.readWrite(raf, bOs, (int) lastChunkSize);
                }
                bOs.close();
            }

            //Create dir to join file and test
            P2pUtil.createDir(defaultJoinFilePath);

            if(P2pUtil.joinMiniFiles(defaultJoinFilePath, defaultChunkFilePath,
                                     fTokens[0], "PNJSD", type, noOfChunks)) {

                File targetFile = new File(defaultJoinFilePath + "/" + "PNJSD" 
                                                                    + "."+ type);

                if(targetFile.exists()) {
                    P2pUtil.p2p_log("<splitFile: > File join test passed");
                    targetFile.delete();
                }
                else {
                    P2pUtil.p2p_log("<splitFile: > File join test failed");
                }
            }
            else {
                P2pUtil.p2p_log("<splitFile: > File join test failed");
            }
        }
        catch(Exception ex) {
            ex.printStackTrace();
            return false;
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
                return false;
            }
        }
        return true;
    }

    public static void start_server(ServerSocket listener) throws Exception{
        //Create monito object
        monitor = new P2pUtilWaitNotify();

        //Start listening
        while(true) {
            new Handler(listener.accept(), clientNum).start();
            P2pUtil.p2p_log("<start_server: > Client "  + clientNum + " is connected!");
            clientNum++;
        }
    }

    public static void main(String[] args) throws Exception {
        ServerSocket listener = new ServerSocket(sPort);

        try {
            //Get file from ip
            BufferedReader bufRd = new BufferedReader(new InputStreamReader(System.in));
            P2pUtil.p2p_log("<main: > Input file for distribution: ");
            String fileName = bufRd.readLine();
            //String fileName = "PNJSD.pdf";
            P2pUtil.p2p_log("<main: > Target file is " + fileName);

            // test to see if file exists in default path
            File test = new File(defaultFilePath + "/" + fileName);
            if(test.exists()) {
                Path path = FileSystems.getDefault().getPath(defaultFilePath, fileName);
                if(splitFile(path.toFile())) {
                    //initialize chunk-list
                    for(int k = 1; k <= noOfChunks; k++) {
                        chunkList.put(k, false);
                    }

                    // Now start server
                    P2pUtil.p2p_log("<main: > The server is running for bootstrapping and initial distribution");
                    start_server(listener);
                }
                else {
                    P2pUtil.p2p_log("<main: > Splitting file to mini files is unsuccessful");
                }
                
            }
            else {
                P2pUtil.p2p_log("<main: > File does not exist in default path " + defaultFilePath);
            }
        }
        catch (IOException x) {
            System.err.format("IOException: %s%n", x);
        }
        catch(Exception ex)
        {
            ex.printStackTrace();
        }
        finally {
            listener.close();
        } 
    } /* end main */
}
