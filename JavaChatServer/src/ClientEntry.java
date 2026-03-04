import java.net.InetAddress;

public class ClientEntry {
	
	private int id;
	private String alias;
	private InetAddress ip;
	private int port;
	private long ttl;
	
	public ClientEntry(int id, String alias, InetAddress ip, int port, long ttl) {
		this.id = id;
		this.alias = alias;
		this.ip = ip;
		this.port = port;
		this.ttl = ttl;
	}

	public int getId() {
		return id;
	}

	public String getAlias() {
		return alias;
	}

	public InetAddress getIp() {
		return ip;
	}

	public int getPort() {
		return port;
	}

	public long getTtl() {
		return ttl;
	}

	public void setTtl(long ttl) {
		this.ttl = ttl;
	}
	
}
