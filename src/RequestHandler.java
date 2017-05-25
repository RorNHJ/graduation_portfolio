import com.sun.org.apache.xerces.internal.impl.dv.util.Base64;

import javax.imageio.ImageIO;
import javax.xml.bind.DatatypeConverter;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;

public class RequestHandler extends Thread {

    boolean isHandshake = false; // 클라이언트와 서버가 웹소켓을 통해서 handshaking이 되었다면 true
    boolean isSingaling = false; // 서버가 시그널링이 성공하면 true
    String myPort;
    private Socket connection;


    boolean server1ToRemote1= false;
    boolean server2ToRemote2= false;
    boolean remote1ToServer1= false;
    boolean remote2ToServer2= false;
    boolean closeConnection =false;

    public RequestHandler(Socket connectionSocket ){
        this.connection = connectionSocket;

    }

    public void run() {
        System.out.printf("New Client Connect! Connected IP : %s, Port : %d\n", connection.getInetAddress(), connection.getPort());

        try (InputStream in = connection.getInputStream(); OutputStream out = connection.getOutputStream()) {
            myPort = Integer.toString(connection.getPort());
            WebServer.clients.put(Integer.toString(connection.getPort()), new DataOutputStream(out));
            CameraViewer cv = new CameraViewer(); // 서버측의 영상처리(3d카메라) 객체 생성
            while(true){



                if(!isHandshake){ // 핸드쉐이킹 전이라면 핸드쉐이킹 시작
                    System.out.println("isHandshake  false...");
                    BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                    String line = br.readLine();
                    if(line== null)
                        return;
                    String[] splited = line.split(" ");
                    if(splited[1].equals("/websocket")  ) { // 헤더에 웹소켓 문자열이 있으면 Sec-WebSocket-Key 파싱.
                        cv.start();
                        String key="";
                        while(!"".equals(line)){
                            System.out.println("header : {}"+line);
                            if (line.contains("Sec-WebSocket-Key")){
                                key = line.split(": ")[1];
                            }
                            line= br.readLine();
                        }

                        try {
                            /*서버가 클라이언트에게 웹소켓 키를 sha-1로 암호화하여 넘겨줘야 핸드쉐이킹이 완성된다. */
                            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                    + "Connection: Upgrade\r\n"
                                    + "Upgrade: websocket\r\n"
                                    + "Sec-WebSocket-Accept: "
                                    + DatatypeConverter
                                    .printBase64Binary(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                            .getBytes("UTF-8")))
                                    + "\r\n\r\n")
                                    .getBytes("UTF-8");
                            DataOutputStream dos = new DataOutputStream(out);

                            dos.write(response, 0, response.length);
                            isHandshake = true;

                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ImageIO.write(cv.becutted, "png", byteArrayOutputStream); // 클라이언트에게 영상처리 결과 이미지를 출력스트림에 넣는다
                            byte[] buffer = byteArrayOutputStream.toByteArray();
                            String encodedImage = Base64.encode(buffer); // 이미지를 넘기기전엔 base64로 인코딩해야함.
                            try{
                                dos.write(brodcast(encodedImage)); // 클라이언트에게  base64로 인코딩한 이미지를 전송
                            }catch (SocketException se){

                            }

                        } catch (NoSuchAlgorithmException e) {

                        }
                    }else if(splited[1].equals("/signaling")) { //웹소켓 헤드에 signaling 문자열이 get방식으로 넘어왔으면 webRTC 시그널링 신호이다
                        String key = "";
                        if(WebServer.i<3) {
                            WebServer.clients2[WebServer.i] = myPort;
                            WebServer.i++;
                        }

                        while (!"".equals(line)) {
                            System.out.println("header : {}" + line);
                            if (line.contains("Sec-WebSocket-Key")) {
                                key = line.split(": ")[1];
                                System.out.println("내가분리한 키 : " + key);
                            }
                            line = br.readLine();
                        }

                        try {
                            byte[] response = ("HTTP/1.1 101 Switching Protocols\r\n"
                                    + "Connection: Upgrade\r\n"
                                    + "Upgrade: websocket\r\n"
                                    + "Sec-WebSocket-Accept: "
                                    + DatatypeConverter
                                    .printBase64Binary(MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                            .getBytes("UTF-8")))
                                    + "\r\n\r\n")
                                    .getBytes("UTF-8");
                            DataOutputStream dos = new DataOutputStream(out);

                            dos.write(response, 0, response.length);
                            isHandshake = true;
                            isSingaling= true;
                        } catch (NoSuchAlgorithmException e) {

                        }
                    }
                    else{
                        DataOutputStream dos = new DataOutputStream(out);
                        try{
                            byte[] body = Files.readAllBytes(new File("./web/index.html").toPath());
                            dos.write(body, 0, body.length);
                            dos.writeBytes("\r\n");
                            dos.flush();
                        }catch (IOException io){

                        }

                    }
                }else{
                    if(isSingaling){
                        // 시그널링이 성공했으면 sendToAll 함수를 통해서 sdp와 candidate를 클라이언트들끼리 교환할 수 있도록 전송.
                        System.out.println("isSingaling  true...");
                        DataOutputStream dos = new DataOutputStream(out);
                        sendToAll(receiveMessage(in));
                    }
                    else{
                        DataOutputStream dos = new DataOutputStream(out);
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ImageIO.write(cv.becutted, "png", byteArrayOutputStream);
                        byte[] buffer = byteArrayOutputStream.toByteArray();
                        String encodedImage = Base64.encode(buffer);
                        try{
                            dos.write(brodcast(encodedImage));
                        }catch (SocketException se){

                        }
                    }


                }
            }

        } catch (IOException e) {
        }
    }



    public byte[] receiveMessage(InputStream rawIn)throws IOException{
        // 웹소켓 핸드쉐이킹이 성공하면 서로 교환할 메시지 프레임의 구조에 맞춰서 클라이언트측에서 보낸 메세지를 수신.
        int len = 0;
        byte[] b = new byte[200000];
        len = rawIn.read(b);
        if(len!=-1){
            byte rLength = 0;
            int rMaskIndex = 2;
            int rDataStart = 0;
            byte data = b[1];
            byte op = (byte) 127;
            rLength = (byte) (data & op);

            if(rLength==(byte)126) rMaskIndex=4;
            if(rLength==(byte)127) rMaskIndex=10;

            byte[] masks = new byte[4];

            int j=0;
            int i=0;
            for(i=rMaskIndex;i<(rMaskIndex+4);i++){
                masks[j] = b[i];
                j++;
            }

            rDataStart = rMaskIndex + 4;

            int messLen = len - rDataStart;

            byte[] message = new byte[messLen];

            for(i=rDataStart, j=0; i<len; i++, j++){
                message[j] = (byte) (b[i] ^ masks[j % 4]);
            }

            String str = new String(message);

            /*클라이언트가 candidate를 서버로 보낼 때 이상하게도 쓰레기값과 비슷한 알 수 없는 문자열들이 붙어서 전송되어진다
            * 이 값들을 걸러내고 candidate의 json 형식을 그대로 파싱한다. (아직 왜 알수 없는 문자열들이 섞여서 들어오는지는 모르겠다...)*/
            if(str.contains("closeConnection")){
                closeConnection = true;
            }
            if(str.contains("offer1") || str.contains("candidate1")){
                server1ToRemote1 = true;
            }
            if(str.contains("offer2") ||  str.contains("candidate2")){
                server2ToRemote2 = true;
            } if( str.contains("answer1") ||str.contains("Rcandidate1")){
                remote1ToServer1 =true;
            }
            if( str.contains("answer2") ||str.contains("Rcandidate2")){
                remote2ToServer2 =true;
            }

            if(str.contains("offer1") || str.contains("offer2") ){
                int lastIndexOf = str.indexOf("\"}}");
                String substring = str.substring(0, lastIndexOf+3);
                System.out.println("receive message from broswer_sub : " + substring);
                byte[] byteData = substring.getBytes();
                return byteData;
            }
            if(str.contains("candidate1") || str.contains("candidate2")  || str.contains("Rcandidate1") || str.contains("Rcandidate2")){
                int lastIndexOf = str.indexOf("\"}");
                String substring = str.substring(0, lastIndexOf+2);
                System.out.println("receive message from broswer_sub : " + substring);
                byte[] byteData = substring.getBytes();
                return byteData;
            }

            System.out.println("receive message from broswer : " + str);
            byte[] byteData = str.getBytes();
            return byteData;



        }
        String data = "null...";
        byte[] databyte = data.getBytes();

        return databyte;
    }


    // 클라이언트들에게 받은  sdp와 candidate를 다시 클라이언트들에게 전송.
    public byte[]  brodcast(String mess) throws IOException{
        //이것도 메세지 프레임 구조에 맞춰서 보내야함.. 프레임 구조는 구글 검색을 통해서 알아냈음..
        byte[] rawData = mess.getBytes();

        int frameCount  = 0;
        byte[] frame = new byte[10];

        frame[0] = (byte) 129;
        if(rawData.length <= 125){
            frame[1] = (byte) rawData.length;
            frameCount = 2;
        }else if(rawData.length >= 126 && rawData.length <= 65535){
            frame[1] = (byte) 126;
            int len = rawData.length;
            frame[2] = (byte)((len >> 8 ) & (byte)255);
            frame[3] = (byte)(len & (byte)255);
            frameCount = 4;
        }else{
            frame[1] = (byte) 127;
            long len = rawData.length;
            frame[2] = (byte)((len >> 56 ) & (byte)255);
            frame[3] = (byte)((len >> 48 ) & (byte)255);
            frame[4] = (byte)((len >> 40 ) & (byte)255);
            frame[5] = (byte)((len >> 32 ) & (byte)255);
            frame[6] = (byte)((len >> 24 ) & (byte)255);
            frame[7] = (byte)((len >> 16 ) & (byte)255);
            frame[8] = (byte)((len >> 8 ) & (byte)255);
            frame[9] = (byte)(len & (byte)255);
            frameCount = 10;

        }

        int bLength = frameCount + rawData.length;

        byte[] reply = new byte[bLength];

        int bLim = 0;
        for(int i=0; i<frameCount;i++){
            reply[bLim] = frame[i];
            bLim++;
        }
        for(int i=0; i<rawData.length;i++){
            reply[bLim] = rawData[i];
            bLim++;
        }

        return reply;

    }

    //서버가 영상처리한 이미지를 클라이언트에게 보낼 때 호출된다.
    void sendToAll(byte[] bytemsg) {
        Iterator it = WebServer.clients.keySet().iterator();
        Object next;
        while (it.hasNext()) {
            try {
                next = it.next();

                if(closeConnection){
                    if(   !(next.toString().equals(WebServer.clients2[0])) ){
                        DataOutputStream dos = (DataOutputStream) WebServer.clients
                                .get(next);
                        String str = new String(bytemsg);
                        dos.write(brodcast(str));
                        next = it.next();
                        DataOutputStream dos2 = (DataOutputStream) WebServer.clients
                                .get(next);
                        String str2 = new String(bytemsg);
                        dos2.write(brodcast(str2));
                        closeConnection =false;
                    }
                }

                if(server1ToRemote1 ){
                    if(next.toString().equals(WebServer.clients2[1]) ){
                        System.out.println("server1ToRemote1:   "+WebServer.clients2[1]);
                        server1ToRemote1= false;
                        DataOutputStream dos = (DataOutputStream) WebServer.clients
                                .get(next);
                        String str = new String(bytemsg);
                        dos.write(brodcast(str));
                    }
                } if(server2ToRemote2  ){
                    if(next.toString().equals(WebServer.clients2[2]) ){
                        System.out.println("server2ToRemote2:   "+WebServer.clients2[2]);
                        server2ToRemote2= false;

                        DataOutputStream dos = (DataOutputStream) WebServer.clients
                                .get(next);
                        String str = new String(bytemsg);
                        dos.write(brodcast(str));
                    }
                } if(    (remote1ToServer1 || remote2ToServer2)   ){
                    if(next.toString().equals(WebServer.clients2[0]) ){
                        if(remote1ToServer1)
                            remote1ToServer1 = false;
                        if(remote2ToServer2)
                            remote2ToServer2 = false;
                        if(server1ToRemote1)
                            server1ToRemote1 = false;
                        if(server2ToRemote2)
                            server2ToRemote2 = false;
                        System.out.println("remote1ToServer1:   "+WebServer.clients2[0]);
                        DataOutputStream dos = (DataOutputStream) WebServer.clients
                                .get(next);
                        String str = new String(bytemsg);
                        dos.write(brodcast(str));
                    }
                }
                //  본인 빼고 다른 상대방에게만 메세지 전달....


            } catch (IOException e) {
            }
        } // while
    }
}



