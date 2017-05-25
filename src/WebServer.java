
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.HashMap;

public class WebServer {
    //    private static final Logger log = LoggerFactory.getLogger(WebServer.class);
    private static final int DEFAULT_PORT = 8080;
    static HashMap<String, DataOutputStream> clients = new HashMap<String, DataOutputStream>();;
    static String[] clients2 ;
    static int i ;
    public static void main(String argv[]) throws Exception {

        Collections.synchronizedMap(clients);
        clients2 = new String[3];
        // 서버소켓을 생성한다. 웹서버는 기본적으로 8080번 포트를 사용한다.
        try (ServerSocket listenSocket = new ServerSocket(DEFAULT_PORT)) {
            System.out.println("Http Server started at"+DEFAULT_PORT+ "port");
            try { InetAddress ip = InetAddress.getLocalHost(); System.out.println("Host Name = [" + ip.getHostName() + "]"); System.out.println("Host Address = [" + ip.getHostAddress() + "]"); } catch (Exception e) { System.out.println(e); }

            // 클라이언트가 연결될때까지 대기한다.
            Socket connection;
            while ((connection = listenSocket.accept()) != null) {
                RequestHandler requestHandler = new RequestHandler(connection);
                requestHandler.start();
            }
        }
    }
}
