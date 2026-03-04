import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DirectoryClientConnector {
	
	private static final int SERVER_PORT = 54321;
	
	private String serverName;
	private int port;
	private String name;
	private int id;
	private volatile boolean loggedOff;
	private long ttl;
	private int chatDestId;
	private volatile boolean hasQuit;
	
	private final ExecutorService executor = Executors.newCachedThreadPool();
	
	public DirectoryClientConnector(String serverName, int port, String name) {
		this.serverName = serverName;
		this.port = port;
		this.name = name;
		loggedOff = true;
	}
	
	public void setChatDest(int chatDestId) {
		this.chatDestId = chatDestId;
	}
	
	public void logon() throws Exception {
		try(
				Socket s = new Socket(serverName, SERVER_PORT);
				Scanner inFromServer = new Scanner(s.getInputStream());
				PrintWriter outToServer = new PrintWriter(s.getOutputStream(), true);
				) {
			outToServer.println("LOGON " + port + " " + name); //Using print() with \n at the end results in the server-side Scanner getting stuck in an infinite loop
			String headerLine = inFromServer.nextLine();
			String headerArray[] = headerLine.split(":");
			if(!headerArray[0].equals("ADDED") || !loggedOff) {
				throw new Exception("logon Exception");
			}
			id = Integer.parseInt(headerArray[1]);
			ttl = Long.parseLong(headerArray[2]);
			loggedOff = false;
			executor.execute(new Runnable() {
				public void run() {
					repeatPing();
				}
			});
			executor.execute(new Runnable() {
				public void run() {
					receiveChat();
				}
			});
		}
	}
	
	public ArrayList<String> getList() throws Exception {
		ArrayList<String> list = new ArrayList<String>();
		try(
				Socket s = new Socket(serverName, SERVER_PORT);
				Scanner inFromServer = new Scanner(s.getInputStream());
				PrintWriter outToServer = new PrintWriter(s.getOutputStream(), true);
				) {
			outToServer.println("LIST " + id); //print() to avoid infinite loop
			String headerLine = inFromServer.nextLine();
			String headerArray[] = headerLine.split(":");
			if(headerArray[0].equals("ERROR")) {
				throw new Exception("list Exception");
			}
			int n = Integer.parseInt(headerArray[headerArray.length - 1]);
			for(int i = 0 ; i < n ; ++i) {
				list.add(inFromServer.nextLine());
			}
		}
		return list;
	}
	
	public void logoff() throws Exception {
		try(
				Socket s = new Socket(serverName, SERVER_PORT);
				Scanner inFromServer = new Scanner(s.getInputStream());
				PrintWriter outToServer = new PrintWriter(s.getOutputStream(), true);
				) {
			outToServer.println("LOGOFF " + id); //print() to avoid infinite loop
			loggedOff = true; //stops repeating pings
			String headerLine = inFromServer.nextLine();
			String headerArray[] = headerLine.split(":");
			executor.shutdownNow();
			if(!headerArray[0].equals("DONE")) {
				throw new Exception("logoff Exception");
			}
		}
	}
	
	public void ping() throws Exception {
		try(
				Socket s = new Socket(serverName, SERVER_PORT);
				Scanner inFromServer = new Scanner(s.getInputStream());
				PrintWriter outToServer = new PrintWriter(s.getOutputStream(), true);
				) {
			outToServer.println("PING " + id); //print() to avoid infinite loop
			String headerLine = inFromServer.nextLine();
			String headerArray[] = headerLine.split(":");
			if(!headerArray[0].equals("PONG")) {
				throw new Exception("ping Exception");
			}
			ttl = Long.parseLong(headerArray[1]);
		}
	}
	
	public void repeatPing() {
		long repeatTime = Math.max(1000, (ttl - System.currentTimeMillis()) / 2);
		while(!loggedOff) {
			try {
				Thread.sleep(repeatTime);
				ping();
				repeatTime = Math.max(1000, (ttl - System.currentTimeMillis()) / 2);
			}
			catch(InterruptedException ie) {
				System.err.println("Interrupted exception");
			}
			catch (Exception e) {
				System.err.println("Client logged off while thread was sleeping. Ignore");
			}
		}
	}
	
	public void chat() throws Exception {
		try(
				Socket s = new Socket(serverName, SERVER_PORT);
				Scanner inFromServer = new Scanner(s.getInputStream());
				PrintWriter outToServer = new PrintWriter(s.getOutputStream(), true);
				) {
			outToServer.println("CHAT " + id + " " + chatDestId);
			String headerLine = inFromServer.nextLine();
			String headerArray[] = headerLine.split(":");
			if(!headerArray[0].equals("CHATTER")) {
				throw new Exception("chat Exception");
			}
			
			String ipString = headerArray[1].startsWith("/") ? headerArray[1].substring(1) : headerArray[1];
	        InetAddress destIP = InetAddress.getByName(ipString);
			
			int destPort = Integer.parseInt(headerArray[2]);
			Socket chatSocket = new Socket(destIP, destPort);
			chatInitiator(chatSocket);
		}
	}
	
	private void chatInitiator(Socket socket) {
			hasQuit = false;
			executor.execute(new Runnable() { //thread open for writing messages
				public void run() {
					chatSupportWriting(socket);
				}
			});
			executor.execute(new Runnable() { //thread for incoming messages
				public void run() {
					chatSupportListening(socket);
				}
			});
	}
	
	private void chatSupportWriting(Socket socket) {
		try {
			PrintWriter toOther = new PrintWriter(socket.getOutputStream(), true);
			Scanner input = new Scanner(System.in);
			System.out.println("You are now chatting: type a message");
			String lineToSend = input.nextLine();
			while(!lineToSend.equalsIgnoreCase("QUIT") && !hasQuit) {
				toOther.println(lineToSend);
				lineToSend = input.nextLine();
			}
			if(!hasQuit) {
				toOther.println("QUIT");
				hasQuit = true;
				socket.close();
			}
			toOther.close();
		}
		catch(IOException ioe) {
			System.err.println("error setting up stream when chat writing");
		}
	}
	
	private void chatSupportListening(Socket socket) {
		try (Scanner inFromOther = new Scanner(socket.getInputStream())) {

	        while (!hasQuit && inFromOther.hasNextLine()) {

	            String lineReceived = inFromOther.nextLine();

	            if (lineReceived.equalsIgnoreCase("QUIT")) {
	                hasQuit = true;
	                socket.close();
	                break;
	            }

	            System.out.println(lineReceived);
	        }
		}
		catch(IOException ioe) {
			System.err.println("error setting up stream when chat listening");
		}
	}
	
	private void receiveChat() {
		try(
				ServerSocket receiver = new ServerSocket(port);
				) {
			while(!loggedOff) {
				Socket s = receiver.accept();
				chatInitiator(s);
			}
		}
		catch(IOException ioe) {
			System.err.println("error setting up streams in chat receiver");
		}
	}
	
}
