import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class DirectoryServer {
	
	public static final int PORT = 54321;
	public static final int TTL = 120; //stored here in seconds
	
	//protected Socket socket;
	protected ConcurrentHashMap<Integer, ClientEntry> map;
	
	private final AtomicInteger idGenerator = new AtomicInteger(1);
	
	public DirectoryServer() {
		map = new ConcurrentHashMap<Integer, ClientEntry>();
	}
	
	/*
	private void assignSocket(Socket s) {
		socket = s;
	}
	*/
	
	private void handleClient(Socket socket) {
		try (Scanner inFromClient = new Scanner(socket.getInputStream())) {
			String input = inFromClient.nextLine();
			String[] messageArray = input.split(" ");
			String command = messageArray[0];
			if(command.equals("LOGON")) { 
				logon(socket, Integer.parseInt(messageArray[1]), messageArray[2]); 
			}
			else if(command.equals("PING")) {
				ping(socket, Integer.parseInt(messageArray[1]));
			}
			else if(command.equals("LIST")) {
				list(socket, Integer.parseInt(messageArray[1]));
			}
			else if(command.equals("LOGOFF")) {
				logoff(socket, Integer.parseInt(messageArray[1]));
			}
			else if(command.equals("CHAT")) {
				chat(socket, Integer.parseInt(messageArray[1]), Integer.parseInt(messageArray[2]));
			}
			else {
				error(socket, "Invalid command");
			}
		}
		catch(IOException e) {
			System.err.println("Error setting up streams");
		}
		catch(NumberFormatException nfe) {
			error(socket, "Invalid port/id integer");
		}
		catch(ArrayIndexOutOfBoundsException oob) {
			error(socket, "Missing data field");
		}
	}
	
	private void logon(Socket socket, int port, String name) {
		int id = idGenerator.getAndIncrement();
		map.put(id, new ClientEntry(id, name, socket.getInetAddress(), port, ((TTL*1000) + System.currentTimeMillis()))); //convert seconds to milliseconds via *1000
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true); //changed from last assignment, all PrintWriters auto-flush to avoid infinite loops
				) {
			outToClient.print("ADDED:" + id + ":" + ((TTL*1000) + System.currentTimeMillis()) + "\n"); //Changed from last assignment, ttl now displayed as mills, not seconds
		}
		catch(IOException e) {
			System.err.println("error in LOGON stream");
		}
	}
	
	private void ping(Socket socket, int id) {
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
				) {
			if(map.get(id) != null) {
				map.get(id).setTtl((TTL*1000) + System.currentTimeMillis());
				outToClient.print("PONG:" + ((TTL*1000) + System.currentTimeMillis()) + "\n");
			}
			else {
				error(socket, "Invalid ID. Entry expired or never existed");
			}
		}
		catch(IOException e) {
			System.err.println("error in PING stream");
		}
	}
	
	private void list(Socket socket, int id) {
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
				) {
			if(map.get(id) != null) {
				outToClient.print("LIST:" + map.size() + "\n");
				for(ClientEntry entry : map.values()) {
					outToClient.print(entry.getId() + ":" + entry.getAlias() + ":" + entry.getIp() + ":" + entry.getPort() + "\n");
				}
			}
			else {
				error(socket, "Invalid ID. Provide valid ID to prove valid client");
			}
		}
		catch(IOException e) {
			System.err.println("error in LIST stream");
		}
	}

	private void logoff(Socket socket, int id) {
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
				) {
			if(map.get(id) != null) {
				map.remove(id);
				outToClient.print("DONE:" + id + "\n");
			}
			else {
				error(socket, "Invalid ID. Provide a valid ID for entry to be removed");
			}
		}
		catch(IOException e) {
			System.err.println("error in LOGOFF stream");
		}
	}
	
	private void error(Socket socket, String message) {
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
				) {
			outToClient.print("ERROR:" + message + "\n");
		}
		catch(IOException e) {
			System.err.println("error in ERROR stream");
		}
		
	}
	
	private void evaluateTtl() {
		for(ClientEntry entry : map.values()) {
			if(System.currentTimeMillis() > entry.getTtl()) {
				map.remove(entry.getId());
			}
		}
	}
	
	private void chat(Socket socket, int validID, int connectToID) {
		try(
				PrintWriter outToClient = new PrintWriter(socket.getOutputStream(), true);
				) {
			if(map.get(validID) != null) {
				if(map.get(connectToID) != null) {
					ClientEntry dest = map.get(connectToID);
					outToClient.print("CHATTER:" + dest.getIp() + ":" + dest.getPort() + "\n");
				}
				else {
					error(socket, "Invalid destination ID. Provide a valid destination ID to be connected to chat");
				}
			}
			else {
				error(socket, "Invalid authorization ID. Provide a valid ID to be connected to chat");
			}
		}
		catch(IOException e) {
			System.err.println("error in CHAT stream");
		}
	}

	public static void main(String[] args) {
		
		DirectoryServer ds = new DirectoryServer();
		ExecutorService executor = Executors.newCachedThreadPool();
		
		ScheduledExecutorService cleaner = Executors.newSingleThreadScheduledExecutor();
		cleaner.scheduleAtFixedRate(() -> ds.evaluateTtl(), 5, 5, TimeUnit.SECONDS);
		
		try (ServerSocket server = new ServerSocket(PORT)) {
			System.out.println("DirectoryServer running on port " + PORT);
			while(true) {
				Socket socket = server.accept();
				
				executor.execute(() -> {
					try {
						ds.handleClient(socket);
					} finally {
						try {
							socket.close();
						} catch(IOException ioe) {
							System.err.println("Error closing socket");
						}
					}
				});
			}
		} catch(IOException ioe) {
			System.err.println("Server error: " + ioe.getMessage());
		}

	}

}
