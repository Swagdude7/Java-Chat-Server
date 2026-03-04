
public class TestClientFirst {

	public static void main(String[] args) { //this class exists purely to have 2 consoles in Eclipse instead of both clients sharing the same console
		DirectoryClientConnector testClient2 = new DirectoryClientConnector("localhost", 11125, "testClient2");
		try {
			testClient2.logon();
		} catch (Exception e) {
			e.printStackTrace();
		}
		long waitTime = System.currentTimeMillis() + 300000; //keep class alive for 5 minutes, again purely for testing
		while(System.currentTimeMillis() <= waitTime) {
			
		}
	}

}
